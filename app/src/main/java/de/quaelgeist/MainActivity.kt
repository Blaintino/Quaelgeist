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
        intent?.getLongExtra(EXTRA_EDIT_ID, -1)?.takeIf { it >= 0 }?.let { editRequest.value = it }
    }

    override fun onResume() {
        super.onResume()
        // Zurück aus den Systemeinstellungen: Prüfliste neu auswerten.
        resumeTick.value = resumeTick.value + 1
    }

    companion object {
        /** Öffnet den Editor für eine neue Erinnerung mit diesem Titel (aus der Schnelleingabe). */
        const val EXTRA_NEW_TITLE = "newTitle"

        /** Öffnet den Editor für diese Erinnerung (aus dem Verschieben-Bildschirm). */
        const val EXTRA_EDIT_ID = "editId"

        val resumeTick = MutableStateFlow(0)
        val newTitleRequest = MutableStateFlow<String?>(null)
        val editRequest = MutableStateFlow<Long?>(null)
    }
}
