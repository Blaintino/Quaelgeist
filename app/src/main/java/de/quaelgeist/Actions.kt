package de.quaelgeist

import android.content.Context
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Aktionen auf Erinnerungen – gemeinsam genutzt von Benachrichtigung und Oberfläche. */
object Actions {

    const val DONE = "de.quaelgeist.DONE"
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
            r.copy(done = true, doneAt = System.currentTimeMillis(), nagCount = 0)
        }
        ReminderStore(ctx).upsert(updated)
        Scheduler.schedule(ctx, updated)
        if (r.isPlace) GeoWatch.replan(ctx)
    }

    /** Ort erkannt: Termin = jetzt, dann wie ein fälliger Alarm (Meldung, Nag-Kette). */
    fun firePlace(ctx: Context, r: Reminder) {
        val now = System.currentTimeMillis()
        ReminderStore(ctx).upsert(
            r.copy(placeArmed = false, baseAt = now, fireAt = now, anchorAt = now, nagCount = 0),
        )
        AlarmReceiver.fire(ctx, r.id)
    }

    /** „Beim nächsten Ankommen/Verlassen“: wieder auf den Ort warten, ohne Zeit-Alarm. */
    fun rearmPlace(ctx: Context, id: Long, expectedBase: Long? = null) {
        val r = load(ctx, id, expectedBase) ?: return
        if (!r.isPlace) return
        Notifier.cancel(ctx, id)
        val updated = r.copy(placeArmed = true, nagCount = 0)
        ReminderStore(ctx).upsert(updated)
        Scheduler.schedule(ctx, updated)
        GeoWatch.refresh(ctx)
    }

    fun snooze(ctx: Context, id: Long, minutes: Int, expectedBase: Long? = null) =
        snoozeUntil(ctx, id, System.currentTimeMillis() + minutes * 60_000L, expectedBase)

    /**
     * Morgen zur Uhrzeit der Serie an diesem Tag ([Recurrence.timeOn]: normale Uhrzeit, am
     * Wochenende ggf. die Wochenend-Uhrzeit; bei einmaligen die des Termins).
     */
    fun snoozeTomorrow(ctx: Context, id: Long, expectedBase: Long? = null) {
        val r = ReminderStore(ctx).get(id) ?: return
        val zone = ZoneId.systemDefault()
        val day = LocalDate.now(zone).plusDays(1)
        val tomorrow = day.atTime(Recurrence.timeOn(r, day, zone)).atZone(zone)
        snoozeUntil(ctx, id, tomorrow.toInstant().toEpochMilli(), expectedBase)
    }

    /** Nächster Montag zur normalen Uhrzeit der Serie bzw. des Termins. */
    fun snoozeNextMonday(ctx: Context, id: Long, expectedBase: Long? = null) {
        val r = ReminderStore(ctx).get(id) ?: return
        val zone = ZoneId.systemDefault()
        val now = Instant.now().atZone(zone)
        val mondayDate = now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        val monday = QuickTimes.nextMonday(now, Recurrence.timeOn(r, mondayDate, zone))
        snoozeUntil(ctx, id, monday.toInstant().toEpochMilli(), expectedBase)
    }

    /** Nächste Meldung zum Zeitpunkt [at]; danach wird wieder im Intervall genervt. */
    fun snoozeUntil(ctx: Context, id: Long, at: Long, expectedBase: Long? = null) {
        val r = load(ctx, id, expectedBase) ?: return
        Notifier.cancel(ctx, id)
        val updated = r.copy(fireAt = at, nagCount = 0)
        ReminderStore(ctx).upsert(updated)
        Scheduler.schedule(ctx, updated)
    }

    /** Archiv leeren: alle erledigten Erinnerungen löschen (haben keine Alarme mehr). */
    fun clearArchive(ctx: Context) {
        ReminderStore(ctx).all().filter { it.done }.forEach { Notifier.cancel(ctx, it.id) }
        ReminderStore(ctx).deleteDone()
    }

    fun delete(ctx: Context, id: Long) {
        val wasPlace = ReminderStore(ctx).get(id)?.isPlace == true
        Scheduler.cancel(ctx, id)
        Notifier.cancel(ctx, id)
        ReminderStore(ctx).delete(id)
        if (wasPlace) GeoWatch.replan(ctx)
    }
}
