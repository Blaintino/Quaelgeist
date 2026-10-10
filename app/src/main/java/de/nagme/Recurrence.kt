package de.nagme

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object Recurrence {

    /**
     * Nächster Termin der Serie, der nach [after] liegt, oder null bei einmaligen Erinnerungen.
     *
     * Gerechnet wird mit Kalenderdaten ab dem aktuellen Termin ([Reminder.baseAt]); Uhrzeit,
     * Tag im Monat und „n-ter Wochentag" stammen dagegen immer aus [Reminder.anchorAt]. So
     * rutscht eine Serie weder nach einem kurzen Monat (31. → 30.) noch nach einer
     * Zeitumstellung (02:30 → 03:30) dauerhaft auf einen anderen Wert.
     */
    fun next(r: Reminder, after: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (r.repeatType == RepeatType.NONE) return null
        val anchor = Instant.ofEpochMilli(r.anchorAt).atZone(zone)
        val time = anchor.toLocalTime()
        var date = Instant.ofEpochMilli(r.baseAt).atZone(zone).toLocalDate()
        var result: Long
        var guard = 0
        do {
            date = step(r, date, anchor.toLocalDate())
            result = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
            guard++
        } while (result <= after && guard < 5000)
        return result
    }

    /**
     * Wöchentliche Serie mit Wochentagsauswahl, deren Startdatum selbst nicht ausgewählt ist
     * (z. B. Start Mittwoch, Auswahl Mo + Fr): Termin auf den ersten passenden Tag ab dem
     * Startdatum verschieben, sonst käme die erste Meldung an einem nicht gewählten Tag.
     */
    fun alignStart(r: Reminder, zone: ZoneId = ZoneId.systemDefault()): Reminder {
        if (r.repeatType != RepeatType.WEEKLY || r.weekdayMask == 0) return r
        val start = Instant.ofEpochMilli(r.baseAt).atZone(zone)
        if (r.weekdayMask and dayBit(start.dayOfWeek) != 0) return r
        var date = start.toLocalDate()
        repeat(7) {
            date = date.plusDays(1)
            if (r.weekdayMask and dayBit(date.dayOfWeek) != 0) {
                val at = date.atTime(start.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
                return r.copy(baseAt = at, fireAt = at, anchorAt = at)
            }
        }
        return r
    }

    private fun dayBit(d: DayOfWeek) = 1 shl (d.value - 1)

    private fun step(r: Reminder, d: LocalDate, anchor: LocalDate): LocalDate {
        val every = r.repeatEvery.coerceAtLeast(1).toLong()
        return when (r.repeatType) {
            RepeatType.DAILY -> d.plusDays(every)
            RepeatType.WEEKLY -> nextWeekly(r, d, every)
            RepeatType.MONTHLY_DAY -> {
                val target = d.withDayOfMonth(1).plusMonths(every)
                target.withDayOfMonth(minOf(anchor.dayOfMonth, target.lengthOfMonth()))
            }
            RepeatType.MONTHLY_NTH_WEEKDAY -> nthWeekday(d, anchor, every)
            RepeatType.NONE -> d
        }
    }

    private fun nextWeekly(r: Reminder, d: LocalDate, every: Long): LocalDate {
        val today = d.dayOfWeek.value // Mo = 1 ... So = 7
        val mask = if (r.weekdayMask == 0) 1 shl (today - 1) else r.weekdayMask
        // Rest der aktuellen Woche
        for (day in (today + 1)..7) {
            if ((mask and (1 shl (day - 1))) != 0) return d.plusDays((day - today).toLong())
        }
        // Erster passender Tag in der nächsten (alle N Wochen) Woche
        val monday = d.minusDays((today - 1).toLong()).plusWeeks(every)
        for (day in 1..7) {
            if ((mask and (1 shl (day - 1))) != 0) return monday.plusDays((day - 1).toLong())
        }
        return d.plusWeeks(every)
    }

    /**
     * z. B. „jeden 3. Mittwoch im Monat"; liegt der Starttermin auf dem 5. Vorkommen
     * (29.–31.), gilt „letzter" Wochentag. Die Regel kommt immer aus dem Starttermin.
     */
    private fun nthWeekday(d: LocalDate, anchor: LocalDate, every: Long): LocalDate {
        val nth = nthOf(anchor)
        val dow = anchor.dayOfWeek
        val target = d.withDayOfMonth(1).plusMonths(every)
        return if (nth >= 5) {
            target.with(TemporalAdjusters.lastInMonth(dow))
        } else {
            target.with(TemporalAdjusters.dayOfWeekInMonth(nth, dow))
        }
    }

    /** Welches Vorkommen seines Wochentags im Monat [d] ist (1–5). */
    fun nthOf(d: LocalDate): Int = (d.dayOfMonth - 1) / 7 + 1
}
