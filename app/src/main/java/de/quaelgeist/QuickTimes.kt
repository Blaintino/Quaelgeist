package de.quaelgeist

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** Zeitvorschläge der Schnelleingabe (reine Funktionen, siehe `QuickTimesTest`). */
object QuickTimes {

    fun inOneHour(now: ZonedDateTime): ZonedDateTime = inMinutes(now, 60)

    fun inMinutes(now: ZonedDateTime, minutes: Long): ZonedDateTime =
        now.plusMinutes(minutes).withSecond(0).withNano(0)

    /**
     * Nächster Montag zur Uhrzeit [time]; ist heute Montag, der Montag in einer Woche.
     * Fällt [time] in die Lücke der Zeitumstellung, verschiebt `atZone` sie nach vorn.
     */
    fun nextMonday(now: ZonedDateTime, time: LocalTime): ZonedDateTime =
        now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atTime(time)
            .atZone(now.zone)

    /** Feste Uhrzeiten in Editor und Widget (Wunsch des Nutzers). */
    val PRESETS: List<LocalTime> = listOf(LocalTime.of(9, 30), LocalTime.of(17, 0))

    /**
     * Uhrzeit [time] am Tag [date] – liegt das nicht in der Zukunft, stattdessen die nächste
     * künftige Gelegenheit zu dieser Uhrzeit (heute oder morgen). So wird kein Termin in der
     * Vergangenheit angelegt, der sofort klingeln würde.
     */
    fun onDayOrNext(date: LocalDate, time: LocalTime, now: ZonedDateTime): ZonedDateTime {
        val at = date.atTime(time).atZone(now.zone)
        return if (at.isAfter(now)) at else atTime(now, time.hour, time.minute)
    }

    /** Gewählte Uhrzeit: heute, oder morgen, falls sie heute schon vorbei ist. */
    fun atTime(now: ZonedDateTime, time: LocalTime): ZonedDateTime = atTime(now, time.hour, time.minute)

    /** Gewählte Uhrzeit: heute, oder morgen, falls sie heute schon vorbei ist. */
    fun atTime(now: ZonedDateTime, hour: Int, minute: Int): ZonedDateTime {
        val today = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }
}
