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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiu.nfcforge.ui.components.InfoRow
import com.aiu.nfcforge.ui.components.TechChip
import com.aiu.nfcforge.viewmodel.NfcUiState

/**
 * ReadResultScreen
 *
 * Displays:
 * - Tag UID
 * - Tech list
 * - Parsed NDEF records (Text / URL) and raw payload preview
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ReadResultScreen(
    state: NfcUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    Column(modifier = Modifier.padding(contentPadding)) {
        TopAppBar(
            title = { Text("Read Result") },
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

        val result = state.lastResult
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (result == null) {
                Text(
                    text = "No tag scanned yet. Go back and tap a tag.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Tag Info",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    InfoRow(label = "UID", value = result.tag.uidHex)
                    InfoRow(label = "Tech count", value = result.tag.techList.size.toString())

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tech List",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        result.tag.techList.take(3).forEach { TechChip(tech = it.substringAfterLast(".")) }
                    }
                    if (result.tag.techList.size > 3) {
                        Text(
                            text = "…and ${result.tag.techList.size - 3} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "NDEF Payload",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )

                    if (result.ndef.records.isEmpty()) {
                        Text(
                            text = "No NDEF message found on this tag.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        result.ndef.records.forEachIndexed { idx, rec ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Record ${idx + 1}: ${rec.kind}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (rec.text != null) InfoRow(label = "Text", value = rec.text)
                                    if (rec.url != null) InfoRow(label = "URL", value = rec.url)
                                    InfoRow(label = "TNF", value = rec.tnf.toString())
                                    InfoRow(label = "Type", value = rec.typeDescription)
                                    Text(
                                        text = "Raw (hex): ${rec.payloadHexPreview}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}



