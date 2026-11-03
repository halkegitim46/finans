package com.kisisel.finans

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kisisel.finans.reminder.EXTRA_OCCURRENCE_ID
import com.kisisel.finans.security.AppLockManager
import com.kisisel.finans.security.SecurityStore
import com.kisisel.finans.ui.lock.LockGate
import com.kisisel.finans.ui.navigation.AppRoot
import com.kisisel.finans.ui.theme.KisiselFinansTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** BiometricPrompt FragmentActivity gerektirir; Compose ile kullanımı aynıdır. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var lockManager: AppLockManager
    @Inject lateinit var securityStore: SecurityStore

    /** Bildirime dokunulunca açılacak fatura dönemi. Uygulama kilitliyse kilit açılana kadar bekler. */
    private var pendingOccurrence by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setSecureScreen(true)   // ayarlar yüklenene kadar güvenli varsayılan
        pendingOccurrence = extractOccurrence(intent)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                securityStore.settings.collect { settings ->
                    lockManager.onSettings(settings)
                    setSecureScreen(settings.secureScreen)
                }
            }
        }

        setContent {
            KisiselFinansTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LockGate {
                        AppRoot(
                            pendingOccurrenceId = pendingOccurrence,
                            onPendingConsumed = {
                                pendingOccurrence = null
                                intent?.removeExtra(EXTRA_OCCURRENCE_ID)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lockManager.onForegrounded(SystemClock.elapsedRealtime())
    }

    override fun onStop() {
        // Ekran döndürme gibi yapılandırma değişiklikleri kilitlemeyi tetiklemesin.
        if (!isChangingConfigurations) lockManager.onBackgrounded(SystemClock.elapsedRealtime())
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingOccurrence = extractOccurrence(intent)
    }

    private fun setSecureScreen(enabled: Boolean) {
        if (enabled) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun extractOccurrence(intent: Intent?): Long? =
        intent?.getLongExtra(EXTRA_OCCURRENCE_ID, 0L)?.takeIf { it > 0 }
}
