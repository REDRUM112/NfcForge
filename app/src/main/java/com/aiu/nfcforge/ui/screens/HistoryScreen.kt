package com.aiu.nfcforge.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.aiu.nfcforge.data.db.NfcTransactionEntity
import com.aiu.nfcforge.data.db.TransactionDirection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * HistoryScreen
 *
 * Shows a list of NFC interactions (reads and write attempts).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    contentPadding: PaddingValues,
    history: List<NfcTransactionEntity>,
    onBack: () -> Unit,
    onOpenSavedCards: () -> Unit,
    onOpenTransaction: (id: Long) -> Unit,
) {
    Column(modifier = Modifier.padding(contentPadding)) {
        TopAppBar(
            title = { Text("History") },
            navigationIcon = {
                OutlinedButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) { Text("Back") }
            },
            actions = {
                OutlinedButton(onClick = onOpenSavedCards, modifier = Modifier.padding(end = 8.dp)) {
                    Text("Saved Cards")
                }
            },
        )

        if (history.isEmpty()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "No history yet.",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Scan a tag (read) or write to a tag to create history entries.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(history, key = { it.id }) { tx ->
                HistoryRow(tx = tx, onClick = { onOpenTransaction(tx.id) })
            }
        }
    }
}

@Composable
private fun HistoryRow(
    tx: NfcTransactionEntity,
    onClick: () -> Unit,
) {
    val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val whenText = df.format(Date(tx.timestampMs))
    val directionText = if (tx.direction == TransactionDirection.READ) "READ" else "WRITE"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = directionText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = whenText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = "UID: ${tx.tagUidHex}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!tx.summary.isNullOrBlank()) {
                Text(
                    text = tx.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = tx.status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "NDEF records: ${tx.ndefRecordCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}


