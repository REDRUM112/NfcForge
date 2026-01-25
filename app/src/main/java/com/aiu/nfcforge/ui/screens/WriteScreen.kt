package com.aiu.nfcforge.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aiu.nfcforge.ui.components.ScanIndicator
import com.aiu.nfcforge.viewmodel.NdefWriteType
import com.aiu.nfcforge.viewmodel.NfcUiState

/**
 * WriteScreen
 *
 * Flow:
 * - User selects record type (Text / URL) and enters payload.
 * - User taps "Arm Write"
 * - Next NFC tag tapped will be written.
 *
 * Error cases handled by ViewModel:
 * - Read-only tags
 * - Unsupported tag types
 * - Write failures
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun WriteScreen(
    state: NfcUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onArmWrite: (type: NdefWriteType, payload: String) -> Unit,
    onCancelWrite: () -> Unit,
    onClear: () -> Unit,
) {
    var writeType by remember { mutableStateOf(NdefWriteType.Text) }
    var payload by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(contentPadding)) {
        TopAppBar(
            title = { Text("Write Data") },
            navigationIcon = {
                OutlinedButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) { Text("Back") }
            },
            actions = {
                OutlinedButton(
                    onClick = onClear,
                    enabled = state.lastResult != null,
                    modifier = Modifier.padding(end = 8.dp),
                ) { Text("Clear") }
            },
        )

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
                        text = "Record Type",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { writeType = NdefWriteType.Text },
                            enabled = writeType != NdefWriteType.Text,
                        ) { Text("Text") }
                        OutlinedButton(
                            onClick = { writeType = NdefWriteType.Url },
                            enabled = writeType != NdefWriteType.Url,
                        ) { Text("URL") }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (writeType == NdefWriteType.Text) "Text payload" else "URL payload",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = payload,
                        onValueChange = { payload = it },
                        placeholder = {
                            Text(if (writeType == NdefWriteType.Text) "Hello NFC" else "https://example.com")
                        },
                        singleLine = false,
                        minLines = 2,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (writeType == NdefWriteType.Url) KeyboardType.Uri else KeyboardType.Text,
                        ),
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onArmWrite(writeType, payload.trim()) },
                            enabled = payload.trim().isNotEmpty() && !state.isWriteArmed,
                        ) { Text("Arm Write") }

                        OutlinedButton(
                            onClick = onCancelWrite,
                            enabled = state.isWriteArmed,
                        ) { Text("Cancel") }
                    }
                }
            }

            AnimatedVisibility(
                visible = state.isWriteArmed,
                enter = fadeIn(animationSpec = tween(220)),
                exit = fadeOut(animationSpec = tween(220)),
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Write Armed",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Now tap an NFC tag to write the data.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ScanIndicator(active = true, diameter = 160.dp)
                        Text(
                            text = "Waiting for tag…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.lastWriteStatus != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Last Write",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = state.lastWriteStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}



