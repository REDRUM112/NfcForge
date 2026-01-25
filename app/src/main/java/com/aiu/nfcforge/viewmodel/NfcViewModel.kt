package com.aiu.nfcforge.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiu.nfcforge.data.NfcRepository
import com.aiu.nfcforge.data.db.SavedCardEntity
import com.aiu.nfcforge.data.db.SavedCardType
import com.aiu.nfcforge.data.db.TransactionWithRecords
import com.aiu.nfcforge.nfc.NfcIntentParser
import com.aiu.nfcforge.nfc.NfcReader
import com.aiu.nfcforge.nfc.NfcWriter
import com.aiu.nfcforge.nfc.WriteFailure
import com.aiu.nfcforge.utils.HexUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * NfcViewModel (MVVM)
 *
 * Responsibilities:
 * - Holds UI state in a [StateFlow]
 * - Exposes one-shot UI events (snackbar, haptics, navigation) via [SharedFlow]
 * - Coordinates NFC read/write operations on background threads
 */
class NfcViewModel(
    private val repository: NfcRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NfcUiState())
    val state: StateFlow<NfcUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<NfcUiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<NfcUiEvent> = _events.asSharedFlow()

    /**
     * History list for the History screen.
     */
    val history = repository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Saved card templates for later reuse.
     */
    val savedCards = repository.observeSavedCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Called by Activity when NFC availability/enabled state changes (e.g. in onResume).
     */
    fun setNfcAvailability(isAvailable: Boolean, isEnabled: Boolean) {
        _state.update {
            it.copy(
                isNfcAvailable = isAvailable,
                isNfcEnabled = isEnabled,
            )
        }
    }

    /**
     * Handles new NFC intents delivered by foreground dispatch.
     */
    fun onNewNfcIntent(intent: Intent) {
        val tag = NfcIntentParser.extractTag(intent) ?: return

        // If write is armed, we attempt to write; otherwise we read.
        if (_state.value.isWriteArmed) {
            writeToTag(tag)
        } else if (_state.value.isContinuousReplay) {
            // In continuous replay mode, keep reading
            readTag(tag)
        } else {
            readTag(tag)
        }
    }

    /**
     * Arms a write: the next tag tapped will be written.
     */
    fun armWrite(type: NdefWriteType, payload: String) {
        val st = _state.value
        if (!st.isNfcAvailable) {
            _events.tryEmit(NfcUiEvent.ShowSnackbar("NFC not available on this device."))
            _events.tryEmit(NfcUiEvent.HapticError)
            return
        }
        if (!st.isNfcEnabled) {
            _events.tryEmit(NfcUiEvent.ShowSnackbar("Enable NFC in system settings first."))
            _events.tryEmit(NfcUiEvent.HapticError)
            return
        }
        if (payload.isBlank()) {
            _events.tryEmit(NfcUiEvent.ShowSnackbar("Enter a payload first."))
            _events.tryEmit(NfcUiEvent.HapticError)
            return
        }

        _state.update {
            it.copy(
                isWriteArmed = true,
                armedWriteType = type,
                armedWritePayload = payload,
                lastWriteStatus = "Write armed. Tap a tag to write.",
            )
        }
        _events.tryEmit(NfcUiEvent.ShowSnackbar("Write armed. Tap a tag."))
    }

    /**
     * Cancels a pending write operation.
     */
    fun cancelWrite() {
        _state.update {
            it.copy(
                isWriteArmed = false,
                armedWriteType = null,
                armedWritePayload = null,
                armedWriteMessageBase64 = null,
                lastWriteStatus = "Write cancelled.",
            )
        }
        _events.tryEmit(NfcUiEvent.ShowSnackbar("Write cancelled."))
    }

    /**
     * Clears the last scanned tag and last write status.
     */
    fun clearLastResult() {
        _state.update {
            it.copy(
                lastResult = null,
                lastWriteStatus = null,
            )
        }
        _events.tryEmit(NfcUiEvent.ShowSnackbar("Cleared."))
    }

    private fun readTag(tag: android.nfc.Tag) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val outcome = NfcReader.readDetailed(tag)
                val result = outcome.scan
                _state.update { it.copy(lastResult = result) }
                _events.tryEmit(NfcUiEvent.ShowSnackbar("Tag read successfully."))
                _events.tryEmit(NfcUiEvent.HapticSuccess)

                // Persist history (read transaction + parsed records + EMV card data + other card data + advanced data if present).
                val emvJson = outcome.emvCardData?.toJson()?.toString()
                val cardJson = outcome.cardData?.toJson()?.toString()
                val advancedJson = outcome.advancedCardData?.toJson()?.toString()
                
                // Combine all JSONs
                val combinedJson = when {
                    advancedJson != null -> advancedJson // Advanced data includes everything
                    emvJson != null && cardJson != null -> {
                        val combined = org.json.JSONObject()
                        combined.put("emv", org.json.JSONObject(emvJson))
                        combined.put("card", org.json.JSONObject(cardJson))
                        combined.toString()
                    }
                    emvJson != null -> emvJson
                    cardJson != null -> cardJson
                    else -> null
                }
                runCatching { 
                    repository.saveReadTransaction(
                        result.tag, 
                        result.ndef, 
                        outcome.ndefMessageBytes,
                        emvCardJson = combinedJson
                    ) 
                }
            } catch (t: Throwable) {
                _events.tryEmit(NfcUiEvent.ShowSnackbar("Read failed: ${t.message ?: "Unknown error"}"))
                _events.tryEmit(NfcUiEvent.HapticError)
            }
        }
    }
    
    /**
     * Start continuous replay mode - keeps reading until cancelled.
     */
    fun startContinuousReplay() {
        _state.update { it.copy(isContinuousReplay = true) }
        _events.tryEmit(NfcUiEvent.Navigate(com.aiu.nfcforge.ui.Routes.Replay))
    }
    
    /**
     * Stop continuous replay mode.
     */
    fun stopContinuousReplay() {
        _state.update { it.copy(isContinuousReplay = false) }
    }

    private fun writeToTag(tag: android.nfc.Tag) {
        val st = _state.value
        val armedMessageBase64 = st.armedWriteMessageBase64
        val type = st.armedWriteType
        val payload = st.armedWritePayload

        viewModelScope.launch(Dispatchers.IO) {
            val tagInfo = TagInfo(
                uidHex = HexUtils.toHex(tag.id ?: ByteArray(0)),
                techList = tag.techList.orEmpty().toList(),
            )
            try {
                val message = when {
                    armedMessageBase64 != null -> NfcWriter.buildNdefMessageFromBase64(armedMessageBase64)
                    type != null && payload != null -> NfcWriter.buildNdefMessage(type, payload)
                    else -> return@launch
                }
                NfcWriter.write(tag, message)

                // Persist history (write transaction).
                val writtenInfo = runCatching { com.aiu.nfcforge.nfc.NdefCodec.parse(message) }.getOrNull()
                runCatching {
                    repository.saveWriteTransaction(
                        tag = tagInfo,
                        type = when {
                            armedMessageBase64 != null -> SavedCardType.NDEF_MESSAGE
                            type != null -> type.toSavedCardType()
                            else -> SavedCardType.NDEF_MESSAGE
                        },
                        payload = payload ?: "(raw NDEF message)",
                        status = "Write OK",
                        writtenMessage = writtenInfo,
                        writtenMessageBytes = message.toByteArray(),
                    )
                }

                _state.update {
                    it.copy(
                        isWriteArmed = false,
                        armedWriteType = null,
                        armedWritePayload = null,
                        armedWriteMessageBase64 = null,
                        lastWriteStatus = "Write OK. (${type?.name ?: "RAW"})",
                    )
                }
                _events.tryEmit(NfcUiEvent.ShowSnackbar("Write successful."))
                _events.tryEmit(NfcUiEvent.HapticSuccess)
            } catch (e: WriteFailure) {
                runCatching {
                    repository.saveWriteTransaction(
                        tag = tagInfo,
                        type = when {
                            armedMessageBase64 != null -> SavedCardType.NDEF_MESSAGE
                            type != null -> type.toSavedCardType()
                            else -> SavedCardType.NDEF_MESSAGE
                        },
                        payload = payload ?: "(raw NDEF message)",
                        status = "Write failed: ${e.userMessage}",
                        writtenMessage = null,
                        writtenMessageBytes = null,
                    )
                }
                _state.update { it.copy(lastWriteStatus = "Write failed: ${e.userMessage}") }
                _events.tryEmit(NfcUiEvent.ShowSnackbar("Write failed: ${e.userMessage}"))
                _events.tryEmit(NfcUiEvent.HapticError)
            } catch (t: Throwable) {
                runCatching {
                    repository.saveWriteTransaction(
                        tag = tagInfo,
                        type = when {
                            armedMessageBase64 != null -> SavedCardType.NDEF_MESSAGE
                            type != null -> type.toSavedCardType()
                            else -> SavedCardType.NDEF_MESSAGE
                        },
                        payload = payload ?: "(raw NDEF message)",
                        status = "Write failed: ${t.message ?: "Unknown error"}",
                        writtenMessage = null,
                        writtenMessageBytes = null,
                    )
                }
                _state.update { it.copy(lastWriteStatus = "Write failed: ${t.message ?: "Unknown error"}") }
                _events.tryEmit(NfcUiEvent.ShowSnackbar("Write failed: ${t.message ?: "Unknown error"}"))
                _events.tryEmit(NfcUiEvent.HapticError)
            }
        }
    }

    /**
     * Observe one transaction (with its record list) for the History detail screen.
     */
    fun observeTransactionDetails(id: Long): kotlinx.coroutines.flow.Flow<TransactionWithRecords?> =
        repository.observeTransactionDetails(id)

    /**
     * From a READ history record, start continuous replay mode.
     * This keeps reading the card until cancelled.
     */
    fun replayRecord(text: String?, url: String?) {
        // For continuous replay, we just start the replay mode
        // The actual reading happens when tags are detected
        startContinuousReplay()
    }

    /**
     * Replay a raw NDEF message from a transaction - start continuous replay.
     */
    fun replayNdefMessage(messageBase64: String) {
        if (messageBase64.isBlank()) {
            _events.tryEmit(NfcUiEvent.ShowSnackbar("No NDEF message to replay."))
            _events.tryEmit(NfcUiEvent.HapticError)
            return
        }
        // Start continuous replay mode
        startContinuousReplay()
    }

    /**
     * Save a history record as a reusable "NFC card" template.
     */
    fun saveAsCard(label: String, text: String?, url: String?, sourceTransactionId: Long?) {
        val (type, payload) = when {
            !text.isNullOrBlank() -> SavedCardType.TEXT to text
            !url.isNullOrBlank() -> SavedCardType.URL to url
            else -> null
        } ?: run {
            _events.tryEmit(NfcUiEvent.ShowSnackbar("Only Text/URL records can be saved as cards."))
            _events.tryEmit(NfcUiEvent.HapticError)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.saveCard(
                    label = label.ifBlank { "Saved card" },
                    type = type,
                    payload = payload,
                    messageBase64 = null,
                    sourceTransactionId = sourceTransactionId,
                )
            }.onSuccess {
                _events.tryEmit(NfcUiEvent.ShowSnackbar("Saved as card."))
                _events.tryEmit(NfcUiEvent.HapticSuccess)
            }.onFailure { t ->
                _events.tryEmit(NfcUiEvent.ShowSnackbar("Save failed: ${t.message ?: "Unknown error"}"))
                _events.tryEmit(NfcUiEvent.HapticError)
            }
        }
    }

    /**
     * Replay a saved card: for EMV cards, this triggers a read operation.
     * For other cards, it arms a write operation.
     */
    fun replaySavedCard(card: SavedCardEntity) {
        when (card.type) {
            SavedCardType.TEXT -> {
                if (card.payload.isNullOrBlank()) {
                    _events.tryEmit(NfcUiEvent.ShowSnackbar("Card payload is missing."))
                    _events.tryEmit(NfcUiEvent.HapticError)
                    return
                }
                armWrite(NdefWriteType.Text, card.payload)
                _events.tryEmit(NfcUiEvent.Navigate(com.aiu.nfcforge.ui.Routes.Write))
            }
            SavedCardType.URL -> {
                if (card.payload.isNullOrBlank()) {
                    _events.tryEmit(NfcUiEvent.ShowSnackbar("Card payload is missing."))
                    _events.tryEmit(NfcUiEvent.HapticError)
                    return
                }
                armWrite(NdefWriteType.Url, card.payload)
                _events.tryEmit(NfcUiEvent.Navigate(com.aiu.nfcforge.ui.Routes.Write))
            }
            SavedCardType.NDEF_MESSAGE -> {
                val msg = card.messageBase64
                val payload = card.payload
                
                if (!payload.isNullOrBlank() && payload.startsWith("{")) {
                    // EMV card JSON - trigger a read operation to replay (read) the card
                    _events.tryEmit(NfcUiEvent.ShowSnackbar("Tap your bank card to read it again."))
                    _events.tryEmit(NfcUiEvent.Navigate(com.aiu.nfcforge.ui.Routes.Home))
                    // The read will happen automatically when user taps the card
                    return
                } else if (!msg.isNullOrBlank()) {
                    // Standard NDEF message - arm write
                    armWriteNdefMessage(msg)
                    _events.tryEmit(NfcUiEvent.Navigate(com.aiu.nfcforge.ui.Routes.Write))
                } else {
                    _events.tryEmit(NfcUiEvent.ShowSnackbar("Saved message is missing."))
                    _events.tryEmit(NfcUiEvent.HapticError)
                    return
                }
            }
        }
    }

    fun deleteSavedCard(card: SavedCardEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.deleteSavedCard(card) }
                .onSuccess { _events.tryEmit(NfcUiEvent.ShowSnackbar("Card deleted.")) }
                .onFailure { _events.tryEmit(NfcUiEvent.ShowSnackbar("Delete failed.")) }
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.deleteTransaction(id) }
                .onSuccess {
                    _events.tryEmit(NfcUiEvent.ShowSnackbar("Transaction deleted."))
                    _events.tryEmit(NfcUiEvent.Navigate(com.aiu.nfcforge.ui.Routes.History))
                }
                .onFailure { _events.tryEmit(NfcUiEvent.ShowSnackbar("Delete failed.")) }
        }
    }

    /**
     * Save an entire raw NDEF message as a reusable card.
     */
    fun saveAsMessageCard(label: String, messageBase64: String, sourceTransactionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.saveCard(
                    label = label.ifBlank { "Saved tag" },
                    type = SavedCardType.NDEF_MESSAGE,
                    payload = null,
                    messageBase64 = messageBase64,
                    sourceTransactionId = sourceTransactionId,
                )
            }.onSuccess {
                _events.tryEmit(NfcUiEvent.ShowSnackbar("Saved tag as card."))
                _events.tryEmit(NfcUiEvent.HapticSuccess)
            }.onFailure {
                _events.tryEmit(NfcUiEvent.ShowSnackbar("Save failed."))
                _events.tryEmit(NfcUiEvent.HapticError)
            }
        }
    }

    /**
     * Save EMV card JSON as a reusable card.
     * Stores the JSON in the payload field for easy retrieval.
     */
    fun saveAsEmvCard(label: String, emvCardJson: String, sourceTransactionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.saveCard(
                    label = label.ifBlank { "Saved bank card" },
                    type = SavedCardType.NDEF_MESSAGE, // Use NDEF_MESSAGE type but store JSON in payload
                    payload = emvCardJson,
                    messageBase64 = null,
                    sourceTransactionId = sourceTransactionId,
                )
            }.onSuccess {
                _events.tryEmit(NfcUiEvent.ShowSnackbar("Saved bank card as card."))
                _events.tryEmit(NfcUiEvent.HapticSuccess)
            }.onFailure {
                _events.tryEmit(NfcUiEvent.ShowSnackbar("Save failed."))
                _events.tryEmit(NfcUiEvent.HapticError)
            }
        }
    }

    /**
     * Arms the next tap to write a raw NDEF message.
     */
    fun armWriteNdefMessage(messageBase64: String) {
        val st = _state.value
        if (!st.isNfcAvailable) {
            _events.tryEmit(NfcUiEvent.ShowSnackbar("NFC not available on this device."))
            _events.tryEmit(NfcUiEvent.HapticError)
            return
        }
        if (!st.isNfcEnabled) {
            _events.tryEmit(NfcUiEvent.ShowSnackbar("Enable NFC in system settings first."))
            _events.tryEmit(NfcUiEvent.HapticError)
            return
        }
        _state.update {
            it.copy(
                isWriteArmed = true,
                armedWriteType = null,
                armedWritePayload = null,
                armedWriteMessageBase64 = messageBase64,
                lastWriteStatus = "Write armed. Tap a tag to write (raw NDEF).",
            )
        }
        _events.tryEmit(NfcUiEvent.ShowSnackbar("Write armed. Tap a tag."))
    }
}

/**
 * One-shot UI events:
 * - Snackbars
 * - Navigation
 * - Haptic feedback
 */
sealed interface NfcUiEvent {
    data class ShowSnackbar(val message: String) : NfcUiEvent
    data class Navigate(val route: String) : NfcUiEvent
    data object HapticSuccess : NfcUiEvent
    data object HapticError : NfcUiEvent
}

private fun NdefWriteType.toSavedCardType(): SavedCardType = when (this) {
    NdefWriteType.Text -> SavedCardType.TEXT
    NdefWriteType.Url -> SavedCardType.URL
}



