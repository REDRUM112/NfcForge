package com.aiu.nfcforge.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiu.nfcforge.viewmodel.NfcUiState

/**
 * ReplayScreen
 *
 * Continuously attempts to read the saved card until cancelled.
 * Shows a scanning indicator and allows cancellation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplayScreen(
    contentPadding: PaddingValues,
    state: NfcUiState,
    onCancel: () -> Unit,
) {
    var isReplaying by remember { mutableStateOf(true) }

    // Auto-navigate to read screen when a tag is detected
    LaunchedEffect(state.lastResult) {
        if (state.lastResult != null && isReplaying) {
            // Tag was read, keep replaying
            isReplaying = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TopAppBar(
            title = { Text("Replaying Card") },
            navigationIcon = {
                OutlinedButton(onClick = onCancel, modifier = Modifier.padding(start = 8.dp)) {
                    Text("Cancel")
                }
            },
        )

        Card(
            modifier = Modifier.padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                CircularProgressIndicator()
                Text(
                    text = "Scanning for card...",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Hold your card near the device",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.lastResult != null) {
                    Text(
                        text = "Card detected! Continuing to scan...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                OutlinedButton(
                    onClick = {
                        isReplaying = false
                        onCancel()
                    },
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text("Stop Replay")
                }
            }
        }
    }
}

