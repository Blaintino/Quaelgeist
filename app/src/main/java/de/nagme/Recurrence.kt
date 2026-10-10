package de.nagme

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

object Recurrence {

    /** Nächster Termin der Serie, der nach [after] liegt, oder null bei einmaligen Erinnerungen. */
    fun next(r: Reminder, after: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (r.repeatType == RepeatType.NONE) return null
        var current = Instant.ofEpochMilli(r.baseAt).atZone(zone)
        var guard = 0
        do {
            current = step(r, current)
            guard++
        } while (current.toInstant().toEpochMilli() <= after && guard < 5000)
        return current.toInstant().toEpochMilli()
    }

    private fun step(r: Reminder, c: ZonedDateTime): ZonedDateTime {
        val every = r.repeatEvery.coerceAtLeast(1).toLong()
        return when (r.repeatType) {
            RepeatType.DAILY -> c.plusDays(every)
            RepeatType.WEEKLY -> nextWeekly(r, c, every)
            RepeatType.MONTHLY_DAY -> c.plusMonths(every)
            RepeatType.MONTHLY_NTH_WEEKDAY -> nthWeekday(c, every)
            RepeatType.NONE -> c
        }
    }

    private fun nextWeekly(r: Reminder, c: ZonedDateTime, every: Long): ZonedDateTime {
        val today = c.dayOfWeek.value // Mo = 1 ... So = 7
        val mask = if (r.weekdayMask == 0) 1 shl (today - 1) else r.weekdayMask
        // Rest der aktuellen Woche
        for (d in (today + 1)..7) {
            if ((mask and (1 shl (d - 1))) != 0) return c.plusDays((d - today).toLong())
        }
        // Erster passender Tag in der nächsten (alle N Wochen) Woche
        val monday = c.minusDays((today - 1).toLong()).plusWeeks(every)
        for (d in 1..7) {
            if ((mask and (1 shl (d - 1))) != 0) return monday.plusDays((d - 1).toLong())
        }
        return c.plusWeeks(every)
    }

    /** z. B. "jeden 3. Mittwoch im Monat"; ab dem 5. Vorkommen: "letzter" Wochentag. */
    private fun nthWeekday(c: ZonedDateTime, every: Long): ZonedDateTime {
        val nth = (c.dayOfMonth - 1) / 7 + 1
        val dow = c.dayOfWeek
        val target = c.withDayOfMonth(1).plusMonths(every)
        return if (nth >= 5) {
            target.with(TemporalAdjusters.lastInMonth(dow))
        } else {
            target.with(TemporalAdjusters.dayOfWeekInMonth(nth, dow))
        }
    }
}
