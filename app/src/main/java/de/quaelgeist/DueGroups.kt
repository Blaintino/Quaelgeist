package de.quaelgeist

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** Abschnitte der Übersicht, in dieser Reihenfolge. */
enum class DueGroup(val label: String) {
    OVERDUE("Überfällig"),
    TODAY("Heute"),
    TOMORROW("Morgen"),
    THIS_WEEK("Diese Woche"),
    LATER("Ab nächster Woche"),
}

/** Einteilung der offenen Erinnerungen nach nächster Meldung (reine Funktionen, `DueGroupsTest`). */
object DueGroups {

    /**
     * Überfällig = Termin erreicht und nicht verschoben: Die nächste Meldung ist schon vorbei
     * oder die Erinnerung nervt gerade (`nagCount > 0`, nächster Nag liegt in der Zukunft).
     * Verschieben setzt `nagCount` zurück und zählt daher nicht als überfällig.
     */
    fun isOverdue(r: Reminder, now: Long): Boolean =
        !r.done && (r.fireAt <= now || r.nagCount > 0)

    fun of(r: Reminder, now: ZonedDateTime): DueGroup {
        if (isOverdue(r, now.toInstant().toEpochMilli())) return DueGroup.OVERDUE
        val day = Instant.ofEpochMilli(r.fireAt).atZone(now.zone).toLocalDate()
        val today = now.toLocalDate()
        val sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        return when {
            !day.isAfter(today) -> DueGroup.TODAY
            day == today.plusDays(1) -> DueGroup.TOMORROW
            !day.isAfter(sunday) -> DueGroup.THIS_WEEK
            else -> DueGroup.LATER
        }
    }

    /** Nur Gruppen mit Einträgen; die Reihenfolge innerhalb einer Gruppe bleibt erhalten. */
    fun group(list: List<Reminder>, now: ZonedDateTime): List<Pair<DueGroup, List<Reminder>>> {
        val byGroup = list.groupBy { of(it, now) }
        return DueGroup.entries.mapNotNull { g -> byGroup[g]?.let { g to it } }
    }
}
