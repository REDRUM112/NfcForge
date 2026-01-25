package com.aiu.nfcforge.ui.screens

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
import com.aiu.nfcforge.data.db.SavedCardEntity

/**
 * SavedCardsScreen
 *
 * Shows reusable "NFC cards" created from READ history.
 * Users can arm a write from a saved card at any time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedCardsScreen(
    contentPadding: PaddingValues,
    cards: List<SavedCardEntity>,
    onBack: () -> Unit,
    onReplay: (SavedCardEntity) -> Unit,
    onDelete: (SavedCardEntity) -> Unit,
) {
    Column(modifier = Modifier.padding(contentPadding)) {
        TopAppBar(
            title = { Text("Saved Cards") },
            navigationIcon = {
                OutlinedButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) { Text("Back") }
            },
        )

        if (cards.isEmpty()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "No saved cards yet.",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Open a READ transaction in History, then tap “Save as Card”.",
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
            items(cards, key = { it.id }) { card ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = card.label,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Type: ${card.type.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = card.payload ?: "(raw NDEF message)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { onReplay(card) }) { 
                                Text(
                                    if (card.type == com.aiu.nfcforge.data.db.SavedCardType.NDEF_MESSAGE && 
                                        !card.payload.isNullOrBlank() && 
                                        card.payload.startsWith("{")) {
                                        "Replay"
                                    } else {
                                        "Arm Write"
                                    }
                                ) 
                            }
                            OutlinedButton(onClick = { onDelete(card) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }
}


