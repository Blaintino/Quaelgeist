package de.quaelgeist

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** Zeitvorschläge der Schnelleingabe (reine Funktionen, siehe `QuickTimesTest`). */
object QuickTimes {

    private val EVENING = LocalTime.of(18, 0)
    private val MORNING = LocalTime.of(8, 0)

    fun inOneHour(now: ZonedDateTime): ZonedDateTime =
        now.plusHours(1).withSecond(0).withNano(0)

    /** Heute 18:00 – null, wenn das schon vorbei ist (dann ergibt der Knopf keinen Sinn). */
    fun thisEvening(now: ZonedDateTime): ZonedDateTime? =
        now.with(EVENING).withSecond(0).withNano(0).takeIf { it.isAfter(now) }

    fun tomorrowMorning(now: ZonedDateTime): ZonedDateTime =
        now.plusDays(1).with(MORNING).withSecond(0).withNano(0)

    /**
     * Nächster Montag zur Uhrzeit [time]; ist heute Montag, der Montag in einer Woche.
     * Fällt [time] in die Lücke der Zeitumstellung, verschiebt `atZone` sie nach vorn.
     */
    fun nextMonday(now: ZonedDateTime, time: LocalTime): ZonedDateTime =
        now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atTime(time)
            .atZone(now.zone)

    /** Gewählte Uhrzeit: heute, oder morgen, falls sie heute schon vorbei ist. */
    fun atTime(now: ZonedDateTime, hour: Int, minute: Int): ZonedDateTime {
        val today = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }
}
