package de.quaelgeist

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Notifier.ensureChannel(this)
        // Repariert verlorene Alarme (z. B. nach „Stopp erzwingen" oder Samsung-Tiefschlaf).
        Scheduler.rescheduleAll(this)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            NagTheme {
                App()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_NEW_TITLE)?.let { newTitleRequest.value = it }
    }

    override fun onResume() {
        super.onResume()
        // Zurück aus den Systemeinstellungen: Prüfliste neu auswerten.
        resumeTick.value = resumeTick.value + 1
    }

    companion object {
        /** Öffnet den Editor für eine neue Erinnerung mit diesem Titel (aus der Schnelleingabe). */
        const val EXTRA_NEW_TITLE = "newTitle"

        val resumeTick = MutableStateFlow(0)
        val newTitleRequest = MutableStateFlow<String?>(null)
    }
}
