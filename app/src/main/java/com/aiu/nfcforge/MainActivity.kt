package com.aiu.nfcforge

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.aiu.nfcforge.ui.NfcForgeApp
import com.aiu.nfcforge.viewmodel.NfcViewModel
import com.aiu.nfcforge.viewmodel.NfcViewModelFactory

/**
 * MainActivity
 *
 * Responsibilities:
 * - Hosts the Compose UI.
 * - Manages NFC foreground dispatch using:
 *   - NfcAdapter
 *   - PendingIntent
 *   - IntentFilter
 *   - Ndef tech list
 * - Forwards NFC intents to the [NfcViewModel].
 *
 * Notes:
 * - We use launchMode="singleTop" so new NFC intents are delivered via [onNewIntent].
 * - Foreground dispatch is enabled in [onResume] and disabled in [onPause] (required lifecycle handling).
 */
class MainActivity : ComponentActivity() {

    private val viewModel: NfcViewModel by viewModels {
        NfcViewModelFactory(applicationContext)
    }

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFilters: Array<IntentFilter> = emptyArray()
    private var techLists: Array<Array<String>> = emptyArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Optional enhancement: Android 12+ splash screen.
        installSplashScreen()
        super.onCreate(savedInstanceState)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        viewModel.setNfcAvailability(
            isAvailable = nfcAdapter != null,
            isEnabled = nfcAdapter?.isEnabled == true,
        )

        // PendingIntent used by the system to deliver tag intents to this Activity while in foreground.
        val nfcIntent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

        pendingIntent = PendingIntent.getActivity(this, 0, nfcIntent, flags)

        // Foreground dispatch intent filters: handle any tag type while app is open.
        intentFilters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
        )

        // Tech lists allow us to prioritize tags that support NDEF / NDEF formatting.
        techLists = arrayOf(
            arrayOf(Ndef::class.java.name),
            arrayOf(NdefFormatable::class.java.name),
        )

        // If the app was launched by tapping a tag, process the initial intent.
        intent?.let(viewModel::onNewNfcIntent)

        setContent {
            NfcForgeApp(viewModel = viewModel)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setNfcAvailability(
            isAvailable = nfcAdapter != null,
            isEnabled = nfcAdapter?.isEnabled == true,
        )

        // Enable foreground dispatch so tag taps are delivered to this Activity.
        val adapter = nfcAdapter ?: return
        if (!adapter.isEnabled) return
        val pi = pendingIntent ?: return
        adapter.enableForegroundDispatch(this, pi, intentFilters, techLists)
    }

    override fun onPause() {
        super.onPause()
        // Always disable foreground dispatch when not in foreground.
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Forward NFC intents to ViewModel for parsing + read/write.
        viewModel.onNewNfcIntent(intent)
    }
}



