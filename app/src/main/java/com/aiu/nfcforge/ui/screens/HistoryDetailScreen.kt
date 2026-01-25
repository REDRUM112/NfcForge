package com.aiu.nfcforge.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.aiu.nfcforge.data.db.TransactionDirection
import com.aiu.nfcforge.data.db.TransactionWithRecords
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.util.Base64
import org.json.JSONObject

fun hexDump(bytes: ByteArray, limit: Int = 256): String {
    val out = StringBuilder()
    for (i in bytes.indices) {
        if (i > 0 && i % 16 == 0) out.append("\n")
        out.append("%02X ".format(bytes[i]))
        if (i >= limit - 1) {
            out.append("… (${bytes.size} bytes)")
            break
        }
    }
    return out.toString().trim()
}
fun safeUtf8(bytes: ByteArray): String = runCatching { String(bytes, Charsets.UTF_8) }.getOrElse { "[invalid UTF-8]" }


/**
 * HistoryDetailScreen
 *
 * Shows one transaction and all associated NDEF records.
 * For READ transactions, each record can be:
 * - replayed (arm a write with its payload)
 * - saved as a reusable "NFC card"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(
    contentPadding: PaddingValues,
    details: TransactionWithRecords?,
    onBack: () -> Unit,
    onReplayRecord: (text: String?, url: String?) -> Unit,
    onReplayNdefMessage: (messageBase64: String) -> Unit,
    onReplayEmvCard: () -> Unit,
    onSaveAsCard: (label: String, text: String?, url: String?, sourceTransactionId: Long) -> Unit,
    onSaveAsMessageCard: (label: String, messageBase64: String, sourceTransactionId: Long) -> Unit,
    onSaveAsEmvCard: (label: String, emvCardJson: String, sourceTransactionId: Long) -> Unit,
    onDelete: (id: Long) -> Unit,
) {
    Column(modifier = Modifier.padding(contentPadding)) {
        TopAppBar(
            title = { Text("Transaction") },
            navigationIcon = {
                OutlinedButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) { Text("Back") }
            },
        )

        if (details == null) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Transaction not found.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        val tx = details.transaction
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val whenText = df.format(Date(tx.timestampMs))
        val canReplay = tx.direction == TransactionDirection.READ
        val clipboard = LocalClipboardManager.current

        val replayableRecords = if (canReplay) {
            details.records.filter { !it.text.isNullOrBlank() || !it.url.isNullOrBlank() }
        } else {
            emptyList()
        }

        var showSaveDialog by remember { mutableStateOf(false) }
        var saveLabel by remember { mutableStateOf("") }
        var selectedText by remember { mutableStateOf<String?>(null) }
        var selectedUrl by remember { mutableStateOf<String?>(null) }
        var saveWholeMessage by remember { mutableStateOf(false) }
        var saveEmvCard by remember { mutableStateOf(false) }

        if (showSaveDialog) {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                title = { Text("Save as NFC Card") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Give this card a label (you can replay it later).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = saveLabel,
                            onValueChange = { saveLabel = it },
                            singleLine = true,
                            placeholder = { Text("e.g. Office Wi‑Fi tag") },
                        )
                    }
                },
                    confirmButton = {
                        OutlinedButton(
                            onClick = {
                                if (saveEmvCard) {
                                    val emvJson = tx.emvCardJson ?: ""
                                    if (emvJson.isNotBlank()) {
                                        onSaveAsEmvCard(saveLabel.trim(), emvJson, tx.id)
                                    }
                                } else if (saveWholeMessage) {
                                    val msg = tx.ndefMessageBase64 ?: ""
                                    if (msg.isNotBlank()) {
                                        onSaveAsMessageCard(saveLabel.trim(), msg, tx.id)
                                    }
                                } else {
                                    onSaveAsCard(saveLabel.trim(), selectedText, selectedUrl, tx.id)
                                }
                                showSaveDialog = false
                                saveLabel = ""
                                saveWholeMessage = false
                                saveEmvCard = false
                            },
                        ) { Text("Save") }
                    },
                dismissButton = {
                    OutlinedButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
                },
            )
        }

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (tx.direction == TransactionDirection.READ) "READ" else "WRITE",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = whenText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(text = "UID: ${tx.tagUidHex}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Status: ${tx.status}", style = MaterialTheme.typography.bodyMedium)
                    if (tx.direction == TransactionDirection.WRITE) {
                        Text(
                            text = "Write: ${tx.writeType ?: "-"}  ${tx.writePayload ?: ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Show action buttons if this is a READ transaction OR if there's any NDEF/EMV data to save
                    val hasNdefData = !tx.ndefMessageBase64.isNullOrBlank()
                    val hasEmvData = !tx.emvCardJson.isNullOrBlank()
                    if (canReplay || hasNdefData || hasEmvData) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Replay button - for EMV cards, this triggers a read; for others, it arms a write
                            if (canReplay) {
                                OutlinedButton(
                                    onClick = {
                                        if (hasEmvData) {
                                            // Replay EMV card = trigger a new read operation
                                            // Navigate to home screen where user can tap the card again
                                            onReplayEmvCard()
                                        } else if (replayableRecords.isNotEmpty()) {
                                            val first = replayableRecords.firstOrNull()
                                            onReplayRecord(first?.text, first?.url)
                                        } else if (hasNdefData) {
                                            // Replay raw NDEF message if no text/url records
                                            onReplayNdefMessage(tx.ndefMessageBase64!!)
                                        }
                                    },
                                    enabled = hasEmvData || replayableRecords.isNotEmpty() || hasNdefData,
                                ) { Text("Replay") }
                            }
                            OutlinedButton(
                                onClick = {
                                    // Always save whole message/EMV data if available, otherwise save first replayable record.
                                    if (hasEmvData) {
                                        // Save EMV card JSON as a special card type
                                        saveEmvCard = true
                                        saveWholeMessage = false
                                        selectedText = null
                                        selectedUrl = null
                                    } else if (hasNdefData) {
                                        saveWholeMessage = true
                                        saveEmvCard = false
                                        selectedText = null
                                        selectedUrl = null
                                    } else if (replayableRecords.isNotEmpty()) {
                                        val first = replayableRecords.firstOrNull()
                                        saveWholeMessage = false
                                        saveEmvCard = false
                                        selectedText = first?.text
                                        selectedUrl = first?.url
                                    }
                                    showSaveDialog = true
                                },
                                enabled = hasEmvData || hasNdefData || replayableRecords.isNotEmpty(),
                            ) { Text("Save as Card") }
                        }
                    }
                    
                    // Delete button for all transactions
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { onDelete(tx.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Delete Transaction", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Records",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )

                    if (details.records.isEmpty()) {
                        Text(
                            text = "No records saved for this transaction.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        details.records.forEach { rec ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Record ${rec.indexInMessage + 1}: ${rec.kind}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (!rec.text.isNullOrBlank()) {
                                        SelectionContainer {
                                            Text("Text:\n${rec.text}")
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(rec.text!!)) }) {
                                                Text("Copy")
                                            }
                                        }
                                    }
                                    if (!rec.url.isNullOrBlank()) {
                                        SelectionContainer {
                                            Text("URL: ${rec.url}")
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(rec.url!!)) }) {
                                                Text("Copy")
                                            }
                                        }
                                    }
                                    Text(
                                        text = "Raw (hex): ${rec.payloadHexPreview}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )

                                    if (canReplay) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            OutlinedButton(
                                                onClick = { onReplayRecord(rec.text, rec.url) },
                                                enabled = !rec.text.isNullOrBlank() || !rec.url.isNullOrBlank(),
                                            ) { Text("Replay") }
                                            OutlinedButton(
                                                onClick = {
                                                    selectedText = rec.text
                                                    selectedUrl = rec.url
                                                    saveWholeMessage = false
                                                    showSaveDialog = true
                                                },
                                                enabled = !rec.text.isNullOrBlank() || !rec.url.isNullOrBlank(),
                                            ) { Text("Save as Card") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // EMV Card Data section - shows full bank card details as JSON
            if (!tx.emvCardJson.isNullOrBlank()) {
                val parsedJson = remember(tx.emvCardJson) {
                    runCatching {
                        JSONObject(tx.emvCardJson)
                    }.getOrNull()
                }
                val formattedJson = remember(tx.emvCardJson) {
                    runCatching {
                        val json = JSONObject(tx.emvCardJson)
                        json.toString(2) // Pretty print with 2-space indent
                    }.getOrElse { e ->
                        "Error parsing JSON: ${e.message}\n\nRaw: ${tx.emvCardJson}"
                    }
                }
                val isError = formattedJson.startsWith("Error parsing JSON")
                
                // Extract key card info for prominent display
                val cardNumber = parsedJson?.optString("panFormatted") ?: parsedJson?.optString("pan")
                val expiryDate = parsedJson?.optString("expiryDateFormatted") ?: parsedJson?.optString("expiryDate")
                val cardholderName = parsedJson?.optString("cardholderName") ?: parsedJson?.optString("cardholderNameFromAid")
                val serviceCode = parsedJson?.optString("serviceCode")
                val appLabel = parsedJson?.optString("applicationLabel") ?: parsedJson?.optString("applicationPreferredName")
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Bank Card Details (EMV)",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        
                        // Show key card information prominently
                        if (!isError && (cardNumber != null || expiryDate != null || cardholderName != null)) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (cardNumber != null) {
                                        Text(
                                            text = "Card Number: $cardNumber",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    if (expiryDate != null) {
                                        Text(
                                            text = "Expiry Date: $expiryDate",
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    }
                                    if (cardholderName != null) {
                                        Text(
                                            text = "Cardholder: $cardholderName",
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    }
                                    if (serviceCode != null) {
                                        Text(
                                            text = "Service Code: $serviceCode",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (appLabel != null) {
                                        Text(
                                            text = "Application: $appLabel",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        
                        Text(
                            text = "Full JSON Data:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        SelectionContainer {
                            Text(
                                text = formattedJson,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth(),
                                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        if (!isError) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { clipboard.setText(AnnotatedString(formattedJson)) },
                                ) {
                                    Text("Copy JSON")
                                }
                            }
                        }
                    }
                }
            }

            // Raw NDEF Data section - shows full unparsed data
            if (!tx.ndefMessageBase64.isNullOrBlank()) {
                val ndefBytes = try {
                    Base64.decode(tx.ndefMessageBase64, Base64.DEFAULT)
                } catch (e: Exception) {
                    byteArrayOf()
                }
                if (ndefBytes.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "Raw NDEF Data",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Full unparsed NDEF message data (${ndefBytes.size} bytes)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            // Hex dump
                            Text(
                                text = "Hex:",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            SelectionContainer {
                                Text(
                                    text = hexDump(ndefBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { clipboard.setText(AnnotatedString(hexDump(ndefBytes))) },
                                ) {
                                    Text("Copy Hex")
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Base64
                            Text(
                                text = "Base64:",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            SelectionContainer {
                                Text(
                                    text = tx.ndefMessageBase64,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { clipboard.setText(AnnotatedString(tx.ndefMessageBase64)) },
                                ) {
                                    Text("Copy Base64")
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // UTF-8 attempt
                            Text(
                                text = "As UTF-8:",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            SelectionContainer {
                                Text(
                                    text = safeUtf8(ndefBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { clipboard.setText(AnnotatedString(safeUtf8(ndefBytes))) },
                                ) {
                                    Text("Copy UTF-8")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


