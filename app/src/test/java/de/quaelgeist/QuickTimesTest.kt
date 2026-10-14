package de.quaelgeist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class QuickTimesTest {

    private val zone = ZoneId.of("Europe/Berlin")

    private fun at(d: Int, h: Int, m: Int, s: Int = 0) =
        ZonedDateTime.of(2026, 10, d, h, m, s, 0, zone)

    @Test
    fun inOneHourDropsSeconds() {
        assertEquals(at(12, 15, 30), QuickTimes.inOneHour(at(12, 14, 30, 42)))
    }

    @Test
    fun thisEveningOnlyBeforeSix() {
        assertEquals(at(12, 18, 0), QuickTimes.thisEvening(at(12, 9, 0)))
        assertNull(QuickTimes.thisEvening(at(12, 18, 0)))
        assertNull(QuickTimes.thisEvening(at(12, 21, 0)))
    }

    @Test
    fun tomorrowMorningIsNextDayEight() {
        assertEquals(at(13, 8, 0), QuickTimes.tomorrowMorning(at(12, 23, 59)))
        assertEquals(at(13, 8, 0), QuickTimes.tomorrowMorning(at(12, 0, 5)))
    }

    @Test
    fun pickedTimeRollsOverWhenPast() {
        assertEquals(at(12, 20, 15), QuickTimes.atTime(at(12, 9, 0), 20, 15))
        assertEquals(at(13, 7, 0), QuickTimes.atTime(at(12, 9, 0), 7, 0))
        // Exakt jetzt zählt als vorbei.
        assertEquals(at(13, 9, 0), QuickTimes.atTime(at(12, 9, 0), 9, 0))
    }

    @Test
    fun dstChangeKeepsWallClock() {
        // 25.10.2026: Umstellung auf Winterzeit; morgen 8:00 bleibt 8:00 Ortszeit.
        val evening = ZonedDateTime.of(2026, 10, 24, 22, 0, 0, 0, zone)
        assertEquals(8, QuickTimes.tomorrowMorning(evening).hour)
    }

    @Test
    fun nextMondayKeepsTimeAndSkipsToday() {
        val nine = LocalTime.of(9, 0)
        // 12.10.2026 ist ein Montag: dann der Montag eine Woche später.
        assertEquals(at(19, 9, 0), QuickTimes.nextMonday(at(12, 7, 0), nine))
        assertEquals(at(19, 9, 0), QuickTimes.nextMonday(at(13, 22, 0), nine))
        assertEquals(at(19, 9, 0), QuickTimes.nextMonday(at(18, 23, 59), nine))
    }

    @Test
    fun nextMondayAcrossDstChange() {
        // Zeitumstellung am 25.10.2026: 09:00 bleibt 09:00 Ortszeit.
        val monday = QuickTimes.nextMonday(at(24, 12, 0), LocalTime.of(9, 0))
        assertEquals(ZonedDateTime.of(2026, 10, 26, 9, 0, 0, 0, zone), monday)
    }

    @Test
    fun onDayOrNextKeepsFutureTime() {
        val now = at(14, 12, 0)
        assertEquals(at(14, 17, 0), QuickTimes.onDayOrNext(now.toLocalDate(), LocalTime.of(17, 0), now))
        assertEquals(at(20, 9, 30), QuickTimes.onDayOrNext(LocalDate.of(2026, 10, 20), LocalTime.of(9, 30), now))
    }

    @Test
    fun onDayOrNextMovesPastTimeToNextDay() {
        val now = at(14, 12, 0)
        // Heute 9:30 ist schon vorbei → morgen 9:30.
        assertEquals(at(15, 9, 30), QuickTimes.onDayOrNext(now.toLocalDate(), LocalTime.of(9, 30), now))
        // Genau jetzt zählt auch als vorbei.
        assertEquals(at(15, 12, 0), QuickTimes.onDayOrNext(now.toLocalDate(), LocalTime.of(12, 0), now))
        // Datum in der Vergangenheit → nächste Gelegenheit ab jetzt.
        assertEquals(at(14, 17, 0), QuickTimes.onDayOrNext(LocalDate.of(2026, 10, 10), LocalTime.of(17, 0), now))
    }
}
