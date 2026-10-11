package de.quaelgeist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class RecurrenceTest {

    private val zone = ZoneId.of("Europe/Berlin")

    private fun ms(y: Int, mo: Int, d: Int, h: Int = 9, mi: Int = 0): Long =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    private fun zdt(ms: Long): ZonedDateTime =
        java.time.Instant.ofEpochMilli(ms).atZone(zone)

    private fun reminder(
        at: Long,
        type: RepeatType,
        every: Int = 1,
        mask: Int = 0,
        anchor: Long = at,
    ) = Reminder(
        id = 1, title = "t", baseAt = at, fireAt = at, anchorAt = anchor,
        repeatType = type, repeatEvery = every, weekdayMask = mask,
    )

    /** Folge der nächsten [n] Termine, jeweils direkt nach dem vorigen erledigt. */
    private fun series(r: Reminder, n: Int): List<Long> {
        var cur = r
        return List(n) {
            val next = Recurrence.next(cur, cur.baseAt, zone)!!
            cur = cur.copy(baseAt = next, fireAt = next)
            next
        }
    }

    private val MO = 1 shl 0
    private val MI = 1 shl 2
    private val FR = 1 shl 4

    @Test
    fun einmaligHatKeinenNaechstenTermin() {
        val at = ms(2026, 10, 10)
        assertNull(Recurrence.next(reminder(at, RepeatType.NONE), at, zone))
    }

    @Test
    fun taeglich() {
        val r = reminder(ms(2026, 10, 10), RepeatType.DAILY)
        assertEquals(listOf(ms(2026, 10, 11), ms(2026, 10, 12)), series(r, 2))
    }

    @Test
    fun alleDreiTage() {
        val r = reminder(ms(2026, 10, 30), RepeatType.DAILY, every = 3)
        assertEquals(listOf(ms(2026, 11, 2), ms(2026, 11, 5)), series(r, 2))
    }

    @Test
    fun woechentlichOhneMaskeGleicherWochentag() {
        val r = reminder(ms(2026, 10, 7), RepeatType.WEEKLY) // Mittwoch
        assertEquals(listOf(ms(2026, 10, 14), ms(2026, 10, 21)), series(r, 2))
    }

    @Test
    fun woechentlichMitMaske() {
        val r = reminder(ms(2026, 10, 5), RepeatType.WEEKLY, mask = MO or FR) // Montag
        assertEquals(
            listOf(ms(2026, 10, 9), ms(2026, 10, 12), ms(2026, 10, 16)),
            series(r, 3),
        )
    }

    @Test
    fun alleZweiWochenUeberMonatsgrenze() {
        // Mo 26.10.2026, Mo + Mi, alle 2 Wochen
        val r = reminder(ms(2026, 10, 26), RepeatType.WEEKLY, every = 2, mask = MO or MI)
        assertEquals(
            listOf(ms(2026, 10, 28), ms(2026, 11, 9), ms(2026, 11, 11), ms(2026, 11, 23)),
            series(r, 4),
        )
    }

    @Test
    fun startAufNichtGewaehltemWochentagWirdVerschoben() {
        // Start Mittwoch 7.10., gewählt nur Montag und Freitag → erster Termin Fr 9.10.
        val r = Recurrence.alignStart(
            reminder(ms(2026, 10, 7, 18, 30), RepeatType.WEEKLY, mask = MO or FR),
            zone,
        )
        assertEquals(ms(2026, 10, 9, 18, 30), r.baseAt)
        assertEquals(r.baseAt, r.fireAt)
        assertEquals(r.baseAt, r.anchorAt)
    }

    @Test
    fun monatlichAmDatum() {
        val r = reminder(ms(2026, 10, 15), RepeatType.MONTHLY_DAY)
        assertEquals(listOf(ms(2026, 11, 15), ms(2026, 12, 15)), series(r, 2))
    }

    @Test
    fun monatlichAm31RutschtNichtDauerhaft() {
        val r = reminder(ms(2027, 1, 31), RepeatType.MONTHLY_DAY)
        assertEquals(
            listOf(ms(2027, 2, 28), ms(2027, 3, 31), ms(2027, 4, 30), ms(2027, 5, 31)),
            series(r, 4),
        )
    }

    @Test
    fun monatlichAm29ImSchaltjahr() {
        val r = reminder(ms(2028, 1, 29), RepeatType.MONTHLY_DAY)
        assertEquals(listOf(ms(2028, 2, 29), ms(2028, 3, 29)), series(r, 2))
    }

    @Test
    fun alleDreiMonate() {
        val r = reminder(ms(2026, 11, 30), RepeatType.MONTHLY_DAY, every = 3)
        assertEquals(listOf(ms(2027, 2, 28), ms(2027, 5, 30)), series(r, 2))
    }

    @Test
    fun dritterMittwoch() {
        val r = reminder(ms(2026, 10, 21), RepeatType.MONTHLY_NTH_WEEKDAY) // 3. Mi Okt.
        assertEquals(
            listOf(ms(2026, 11, 18), ms(2026, 12, 16), ms(2027, 1, 20)),
            series(r, 3),
        )
    }

    @Test
    fun fuenftesVorkommenWirdZuLetztemUndBleibtEs() {
        // Do 29.10.2026 ist der 5. Donnerstag → „letzter Donnerstag"
        val r = reminder(ms(2026, 10, 29), RepeatType.MONTHLY_NTH_WEEKDAY)
        assertEquals(
            // Nov: 26. (4. = letzter), Dez: 31. (5.), Jan: 28. (4.), Feb: 25. (4.)
            listOf(ms(2026, 11, 26), ms(2026, 12, 31), ms(2027, 1, 28), ms(2027, 2, 25)),
            series(r, 4),
        )
    }

    @Test
    fun uhrzeitBleibtUeberSommerzeitumstellung() {
        // 29.03.2026: Umstellung auf Sommerzeit
        val r = reminder(ms(2026, 3, 27, 8, 0), RepeatType.DAILY)
        series(r, 4).forEach {
            assertEquals(8, zdt(it).hour)
            assertEquals(0, zdt(it).minute)
        }
    }

    @Test
    fun uhrzeitBleibtUeberWinterzeitumstellung() {
        // 25.10.2026: Umstellung auf Winterzeit
        val r = reminder(ms(2026, 10, 18, 7, 30), RepeatType.WEEKLY)
        val next = series(r, 2)
        assertEquals(listOf(ms(2026, 10, 25, 7, 30), ms(2026, 11, 1, 7, 30)), next)
        assertEquals(1, zdt(next[1]).offset.totalSeconds / 3600)
    }

    @Test
    fun terminInDerUmstellungsluekeVerschiebtDieSerieNicht() {
        // 02:30 existiert am 29.03.2026 nicht → an dem Tag 03:30, danach wieder 02:30
        val r = reminder(ms(2026, 3, 28, 2, 30), RepeatType.DAILY)
        val next = series(r, 2).map { zdt(it).toLocalTime().toString() }
        assertEquals(listOf("03:30", "02:30"), next)
    }

    @Test
    fun spaeteErledigungLiegtNachJetzt() {
        val r = reminder(ms(2026, 1, 1), RepeatType.DAILY)
        val now = ms(2026, 10, 10, 12, 0)
        val next = Recurrence.next(r, now, zone)!!
        assertTrue(next > now)
        assertEquals(ms(2026, 10, 11), next)
    }

    @Test
    fun spaeteErledigungWoechentlich() {
        val r = reminder(ms(2026, 9, 7), RepeatType.WEEKLY, mask = MO or FR)
        val now = ms(2026, 10, 7, 12, 0) // Mittwoch
        assertEquals(ms(2026, 10, 9), Recurrence.next(r, now, zone))
    }
}
