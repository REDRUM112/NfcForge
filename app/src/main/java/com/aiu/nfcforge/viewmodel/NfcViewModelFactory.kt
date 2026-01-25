package com.aiu.nfcforge.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aiu.nfcforge.data.NfcRepository
import com.aiu.nfcforge.data.db.AppDatabase

/**
 * NfcViewModelFactory
 *
 * Provides the ViewModel with its required dependencies (Room repository).
 * This keeps the ViewModel constructor clean and testable.
 */
class NfcViewModelFactory(
    private val appContext: Context,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NfcViewModel::class.java)) {
            val db = AppDatabase.get(appContext)
            val repo = NfcRepository(db.nfcDao())
            return NfcViewModel(repository = repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}


