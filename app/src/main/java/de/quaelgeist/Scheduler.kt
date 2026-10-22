package de.quaelgeist

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

object Scheduler {

    const val EXTRA_ID = "id"

    private fun firePendingIntent(ctx: Context, id: Long): PendingIntent {
        val intent = Intent(ctx, AlarmReceiver::class.java).apply {
            data = Uri.parse("quaelgeist://fire/$id")
            putExtra(EXTRA_ID, id)
        }
        return PendingIntent.getBroadcast(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun canScheduleExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /**
     * Plant den Alarm für [Reminder.fireAt]; erledigte Erinnerungen werden abgemeldet.
     *
     * Bevorzugt `setAlarmClock`: Das ist die Alarmart von Wecker-Apps. Android weckt das Gerät
     * dafür auch aus dem Tiefschlaf (Doze) pünktlich, streckt sie nicht und nimmt die App für
     * den Moment von Hintergrund-Einschränkungen aus. Nebenwirkung: In der Statusleiste
     * erscheint ein Wecker-Symbol, solange ein Termin ansteht.
     */
    fun schedule(ctx: Context, r: Reminder) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = firePendingIntent(ctx, r.id)
        // Wartende Orts-Erinnerungen haben keinen Zeit-Alarm (siehe [GeoWatch]).
        if (r.done || r.placeArmed) {
            am.cancel(pi)
            return
        }
        if (canScheduleExact(ctx)) {
            val show = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(r.fireAt, show), pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.fireAt, pi)
        }
    }

    fun cancel(ctx: Context, id: Long) {
        ctx.getSystemService(AlarmManager::class.java).cancel(firePendingIntent(ctx, id))
    }

    /**
     * Meldet alle offenen Alarme neu an – nach Neustart, Update, Zeitänderung und bei jedem
     * App-Start (repariert Alarme, die z. B. durch „Stopp erzwingen" verloren gingen).
     * Überfällige Erinnerungen ohne weitere erlaubte Wiederholung werden übersprungen,
     * sonst würden sie bei jedem App-Start erneut klingeln.
     */
    fun rescheduleAll(ctx: Context) {
        val now = System.currentTimeMillis()
        ReminderStore(ctx).all()
            .filter { !it.done && !it.placeArmed && (it.fireAt > now || it.canNag) }
            .forEach { schedule(ctx, it) }
        // Geofences und Standort-Updates überleben weder Neustart noch „Stopp erzwingen“.
        runCatching { GeoWatch.replan(ctx) }
    }
}
