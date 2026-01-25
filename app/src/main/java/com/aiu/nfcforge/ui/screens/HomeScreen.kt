package com.aiu.nfcforge.ui.screens

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiu.nfcforge.ui.components.InfoRow
import com.aiu.nfcforge.ui.components.ScanIndicator
import com.aiu.nfcforge.viewmodel.NfcUiState

/**
 * HomeScreen
 *
 * Shows:
 * - NFC availability + enabled status
 * - Animated scan indicator
 * - Quick summary of last read/write
 * - Navigation actions
 */
@Composable
fun HomeScreen(
    state: NfcUiState,
    contentPadding: PaddingValues,
    onGoToWrite: () -> Unit,
    onGoToRead: () -> Unit,
    onGoToHistory: () -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "NfcForge",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Tap an NFC tag to read it. Arm a write, then tap a tag to write.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoRow(
                    label = "NFC Available",
                    value = if (state.isNfcAvailable) "Yes" else "No",
                )
                InfoRow(
                    label = "NFC Enabled",
                    value = if (state.isNfcEnabled) "Yes" else "No",
                )

                if (state.isNfcAvailable && !state.isNfcEnabled) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            // Open NFC settings so the user can enable NFC.
                            context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                        },
                    ) {
                        Text("Open NFC Settings")
                    }
                }
            }
        }

        // Scan indicator area
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ScanIndicator(active = state.isReadyToScan, diameter = 190.dp)
                Text(
                    text = state.scanHeadline,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = state.scanSubtext,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Last result summary
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Last Tag",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (state.lastResult == null) {
                    Text(
                        text = "No tag scanned yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    InfoRow(label = "UID", value = state.lastResult.tag.uidHex)
                    InfoRow(label = "Techs", value = state.lastResult.tag.techList.joinToString(limit = 3, truncated = "…"))
                    InfoRow(label = "NDEF", value = state.lastResult.ndefSummary)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onGoToRead, enabled = state.lastResult != null) { Text("Read Result") }
                    OutlinedButton(onClick = onClear, enabled = state.lastResult != null) { Text("Clear") }
                }
            }
        }

        // Primary actions
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(modifier = Modifier.weight(1f), onClick = onGoToWrite) { Text("Write Data") }
            OutlinedButton(modifier = Modifier.weight(1f), onClick = onGoToHistory) { Text("History") }
        }
    }
}



