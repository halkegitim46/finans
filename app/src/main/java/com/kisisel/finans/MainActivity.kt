package com.kisisel.finans

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kisisel.finans.reminder.EXTRA_OCCURRENCE_ID
import com.kisisel.finans.ui.navigation.AppRoot
import com.kisisel.finans.ui.theme.KisiselFinansTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Bildirime dokunulunca açılacak fatura dönemi (tüketilince null olur). */
    private var pendingOccurrence by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingOccurrence = extractOccurrence(intent)
        setContent {
            KisiselFinansTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingOccurrence = extractOccurrence(intent)
    }

    private fun extractOccurrence(intent: Intent?): Long? =
        intent?.getLongExtra(EXTRA_OCCURRENCE_ID, 0L)?.takeIf { it > 0 }
}
