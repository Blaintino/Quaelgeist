package de.nagme

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Wird vom AlarmManager ausgelöst: zeigt die Meldung und plant ggf. die nächste Wiederholung. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getLongExtra(Scheduler.EXTRA_ID, -1)
        if (id < 0) return
        val store = ReminderStore(ctx)
        val r = store.get(id) ?: return
        if (r.done) return

        // Zuerst die nächste Wiederholung sichern – falls beim Anzeigen etwas schiefgeht,
        // reißt die Nag-Kette trotzdem nicht ab.
        val canNag = r.canNag
        val updated = if (canNag) {
            r.copy(
                nagCount = r.nagCount + 1,
                fireAt = System.currentTimeMillis() + r.nagIntervalMin * 60_000L,
            )
        } else {
            // Letzte Meldung: Serien rücken zum nächsten Termin vor, einmalige bleiben überfällig.
            val next = Recurrence.next(r, System.currentTimeMillis())
            if (next != null) r.copy(baseAt = next, fireAt = next, nagCount = 0) else r
        }
        if (updated != r) store.upsert(updated)
        if (canNag || updated.fireAt != r.fireAt) Scheduler.schedule(ctx, updated)

        runCatching { Notifier.show(ctx, r) }
        runCatching { Notifier.buzz(ctx) }
    }
}

/** Schaltflächen der Benachrichtigung. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getLongExtra(Scheduler.EXTRA_ID, -1)
        if (id < 0) return
        val base = intent.getLongExtra(Actions.EXTRA_BASE, -1).takeIf { it >= 0 }
        when (intent.action) {
            Actions.DONE -> Actions.markDone(ctx, id, base)
        }
    }
}

/** Plant nach Neustart, App-Update, Zeitumstellung oder neuer Alarm-Berechtigung alle Alarme neu. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        // Exportiert (für die System-Broadcasts) – fremde Intents ignorieren.
        if (intent.action !in HANDLED) return
        Scheduler.rescheduleAll(ctx)
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
