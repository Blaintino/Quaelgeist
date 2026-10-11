package de.nagme

import android.content.Context
import java.time.Instant
import java.time.ZoneId

/** Aktionen auf Erinnerungen – gemeinsam genutzt von Benachrichtigung und Oberfläche. */
object Actions {

    const val DONE = "de.nagme.DONE"
    const val EXTRA_BASE = "base"

    /**
     * [expectedBase] stammt aus einer Benachrichtigung. Passt es nicht mehr zum aktuellen Termin
     * (Serie ist inzwischen weitergelaufen), wird die Aktion ignoriert.
     */
    private fun load(ctx: Context, id: Long, expectedBase: Long?): Reminder? {
        val r = ReminderStore(ctx).get(id)
        if (r == null || r.done || (expectedBase != null && expectedBase != r.baseAt)) {
            Notifier.cancel(ctx, id)
            return null
        }
        return r
    }

    fun markDone(ctx: Context, id: Long, expectedBase: Long? = null) {
        val r = load(ctx, id, expectedBase) ?: return
        Notifier.cancel(ctx, id)
        val next = Recurrence.next(r, System.currentTimeMillis())
        val updated = if (next != null) {
            r.copy(baseAt = next, fireAt = next, nagCount = 0)
        } else {
            r.copy(done = true, nagCount = 0)
        }
        ReminderStore(ctx).upsert(updated)
        Scheduler.schedule(ctx, updated)
    }

    fun snooze(ctx: Context, id: Long, minutes: Int, expectedBase: Long? = null) =
        snoozeUntil(ctx, id, System.currentTimeMillis() + minutes * 60_000L, expectedBase)

    /** Morgen zur selben Uhrzeit wie der ursprüngliche Termin. */
    fun snoozeTomorrow(ctx: Context, id: Long, expectedBase: Long? = null) {
        val r = ReminderStore(ctx).get(id) ?: return
        val zone = ZoneId.systemDefault()
        val time = Instant.ofEpochMilli(r.baseAt).atZone(zone).toLocalTime()
        val tomorrow = Instant.now().atZone(zone).plusDays(1).with(time)
        snoozeUntil(ctx, id, tomorrow.toInstant().toEpochMilli(), expectedBase)
    }

    /** Nächste Meldung zum Zeitpunkt [at]; danach wird wieder im Intervall genervt. */
    fun snoozeUntil(ctx: Context, id: Long, at: Long, expectedBase: Long? = null) {
        val r = load(ctx, id, expectedBase) ?: return
        Notifier.cancel(ctx, id)
        val updated = r.copy(fireAt = at, nagCount = 0)
        ReminderStore(ctx).upsert(updated)
        Scheduler.schedule(ctx, updated)
    }

    fun delete(ctx: Context, id: Long) {
        Scheduler.cancel(ctx, id)
        Notifier.cancel(ctx, id)
        ReminderStore(ctx).delete(id)
    }
}
