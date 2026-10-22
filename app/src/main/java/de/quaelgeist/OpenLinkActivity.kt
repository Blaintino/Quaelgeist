package de.quaelgeist

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast

/**
 * Knopf „Link öffnen" der Benachrichtigung: öffnet den Link und hakt die Erinnerung ab
 * (bei Serien nur diesen Termin, wie *Erledigt*). Ohne eigene Oberfläche.
 */
class OpenLinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getLongExtra(Scheduler.EXTRA_ID, -1)
        val base = intent.getLongExtra(Actions.EXTRA_BASE, -1).takeIf { it >= 0 }
        val r = ReminderStore(this).get(id)
        if (r == null || !Links.isOpenable(r.link)) {
            Notifier.cancel(this, id)
            Toast.makeText(this, "Kein Link mehr hinterlegt", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(r.link))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(view)
            // Erst nach erfolgreichem Öffnen abhaken. Gehört die Meldung zu einem älteren
            // Termin, ignoriert markDone das Abhaken (siehe [Actions]); der Link öffnet trotzdem.
            Actions.markDone(this, id, base)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Kein Browser gefunden", Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
