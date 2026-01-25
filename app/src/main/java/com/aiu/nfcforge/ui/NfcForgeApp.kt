package com.aiu.nfcforge.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aiu.nfcforge.ui.screens.HomeScreen
import com.aiu.nfcforge.ui.screens.HistoryDetailScreen
import com.aiu.nfcforge.ui.screens.HistoryScreen
import com.aiu.nfcforge.ui.screens.ReadResultScreen
import com.aiu.nfcforge.ui.screens.SavedCardsScreen
import com.aiu.nfcforge.ui.screens.WriteScreen
import com.aiu.nfcforge.ui.theme.NfcForgeTheme
import com.aiu.nfcforge.viewmodel.NfcUiEvent
import com.aiu.nfcforge.viewmodel.NfcViewModel

/**
 * NfcForgeApp
 *
 * Top-level Compose host:
 * - Applies the Material 3 theme (dark by default per requirements).
 * - Hosts navigation between screens.
 * - Centralizes snackbar + haptic feedback via one-shot [NfcUiEvent]s.
 */
@Composable
fun NfcForgeApp(
    viewModel: NfcViewModel,
) {
    NfcForgeTheme {
        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }
        val haptics = LocalHapticFeedback.current

        // Handle one-time UI events from ViewModel.
        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when (event) {
                    is NfcUiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                    is NfcUiEvent.Navigate -> navController.navigate(event.route)
                    NfcUiEvent.HapticSuccess -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    NfcUiEvent.HapticError -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val state = viewModel.state.collectAsStateWithLifecycle().value

            androidx.compose.material3.Scaffold(
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = Routes.Home,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable(Routes.Home) {
                        HomeScreen(
                            state = state,
                            contentPadding = innerPadding,
                            onGoToWrite = { navController.navigate(Routes.Write) },
                            onGoToRead = { navController.navigate(Routes.Read) },
                            onGoToHistory = { navController.navigate(Routes.History) },
                            onClear = viewModel::clearLastResult,
                        )
                    }
                    composable(Routes.Read) {
                        ReadResultScreen(
                            state = state,
                            contentPadding = innerPadding,
                            onBack = { navController.popBackStack() },
                            onClear = viewModel::clearLastResult,
                        )
                    }
                    composable(Routes.Write) {
                        WriteScreen(
                            state = state,
                            contentPadding = innerPadding,
                            onBack = { navController.popBackStack() },
                            onArmWrite = viewModel::armWrite,
                            onCancelWrite = viewModel::cancelWrite,
                            onClear = viewModel::clearLastResult,
                        )
                    }
                    composable(Routes.History) {
                        val history = viewModel.history.collectAsStateWithLifecycle().value
                        HistoryScreen(
                            contentPadding = innerPadding,
                            history = history,
                            onBack = { navController.popBackStack() },
                            onOpenSavedCards = { navController.navigate(Routes.Cards) },
                            onOpenTransaction = { id -> navController.navigate(Routes.historyDetail(id)) },
                        )
                    }
                    composable(
                        route = Routes.HistoryDetail,
                        arguments = listOf(navArgument("id") { type = NavType.LongType }),
                    ) { backStackEntry ->
                        val id = backStackEntry.arguments?.getLong("id") ?: return@composable
                        val details = viewModel.observeTransactionDetails(id)
                            .collectAsStateWithLifecycle(initialValue = null).value
                        HistoryDetailScreen(
                            contentPadding = innerPadding,
                            details = details,
                            onBack = { navController.popBackStack() },
                            onReplayRecord = { text, url -> viewModel.replayRecord(text, url) },
                            onReplayNdefMessage = { messageBase64 -> viewModel.replayNdefMessage(messageBase64) },
                            onReplayEmvCard = {
                                // Navigate to home screen where user can tap the card again
                                navController.navigate(Routes.Home) { popUpTo(Routes.Home) { inclusive = true } }
                            },
                            onSaveAsCard = { label, text, url, sourceId ->
                                viewModel.saveAsCard(label, text, url, sourceId)
                            },
                            onSaveAsMessageCard = { label, messageBase64, sourceId ->
                                viewModel.saveAsMessageCard(label, messageBase64, sourceId)
                            },
                            onSaveAsEmvCard = { label, emvCardJson, sourceId ->
                                viewModel.saveAsEmvCard(label, emvCardJson, sourceId)
                            },
                            onDelete = { txId -> viewModel.deleteTransaction(txId) },
                        )
                    }
                    composable(Routes.Cards) {
                        val cards = viewModel.savedCards.collectAsStateWithLifecycle().value
                        SavedCardsScreen(
                            contentPadding = innerPadding,
                            cards = cards,
                            onBack = { navController.popBackStack() },
                            onReplay = { viewModel.replaySavedCard(it) },
                            onDelete = { viewModel.deleteSavedCard(it) },
                        )
                    }
                    composable(Routes.Replay) {
                        ReplayScreen(
                            contentPadding = innerPadding,
                            state = state,
                            onCancel = {
                                viewModel.stopContinuousReplay()
                                navController.popBackStack()
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Simple string routes for Navigation Compose.
 */
object Routes {
    const val Home = "home"
    const val Read = "read"
    const val Write = "write"
    const val History = "history"
    const val Cards = "cards"
    const val Replay = "replay"

    const val HistoryDetail = "history/{id}"
    fun historyDetail(id: Long) = "history/$id"
}



