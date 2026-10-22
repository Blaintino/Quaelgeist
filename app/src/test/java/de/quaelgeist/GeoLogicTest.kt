package de.quaelgeist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class GeoLogicTest {

    private val zone = ZoneId.of("Europe/Berlin")

    @Test
    fun classifyByDistance() {
        assertEquals(Presence.INSIDE, GeoLogic.classify(100f, 20f, 150, false))
        // Am Rand ohne genug Abstand: unentschieden, Zustand bleibt.
        assertNull(GeoLogic.classify(180f, 20f, 150, false))
        assertEquals(Presence.OUTSIDE, GeoLogic.classify(250f, 20f, 150, false))
        // Sehr ungenaue Messung im Radius zählt nicht als „da“.
        assertNull(GeoLogic.classify(100f, 800f, 150, false))
        // Ungenauigkeit wird vom Abstand abgezogen.
        assertNull(GeoLogic.classify(300f, 120f, 150, false))
        assertNull(GeoLogic.classify(null, null, 150, false))
    }

    @Test
    fun placeWifiWins() {
        assertEquals(Presence.INSIDE, GeoLogic.classify(5000f, 20f, 150, true))
        assertEquals(Presence.INSIDE, GeoLogic.classify(null, null, 150, true))
    }

    @Test
    fun onlyRealTransitionsTrigger() {
        assertEquals(PlaceTrigger.ARRIVE, GeoLogic.triggerFor(Presence.OUTSIDE, Presence.INSIDE))
        assertEquals(PlaceTrigger.LEAVE, GeoLogic.triggerFor(Presence.INSIDE, Presence.OUTSIDE))
        assertNull(GeoLogic.triggerFor(Presence.UNKNOWN, Presence.INSIDE))
        assertNull(GeoLogic.triggerFor(Presence.UNKNOWN, Presence.OUTSIDE))
        assertNull(GeoLogic.triggerFor(Presence.INSIDE, Presence.INSIDE))
    }

    @Test
    fun windows() {
        assertTrue(GeoLogic.inWindow(-1, -1, 3 * 60))
        assertTrue(GeoLogic.inWindow(16 * 60, 20 * 60, 16 * 60))
        assertFalse(GeoLogic.inWindow(16 * 60, 20 * 60, 20 * 60))
        assertFalse(GeoLogic.inWindow(16 * 60, 20 * 60, 9 * 60))
        // Über Mitternacht.
        assertTrue(GeoLogic.inWindow(22 * 60, 6 * 60, 23 * 60))
        assertTrue(GeoLogic.inWindow(22 * 60, 6 * 60, 5 * 60))
        assertFalse(GeoLogic.inWindow(22 * 60, 6 * 60, 12 * 60))
    }

    private fun ms(h: Int, m: Int, day: Int = 22) =
        ZonedDateTime.of(2026, 10, day, h, m, 0, 0, zone).toInstant().toEpochMilli()

    private fun reminder(start: Int, end: Int) = Reminder(
        id = 1, title = "x", baseAt = 0, fireAt = 0, placeId = 7, placeArmed = true,
        windowStartMin = start, windowEndMin = end,
    )

    @Test
    fun nextBoundary() {
        val r = reminder(16 * 60, 20 * 60)
        assertEquals(ms(16, 0), GeoLogic.nextBoundary(r, ms(9, 0), zone))
        assertEquals(ms(20, 0), GeoLogic.nextBoundary(r, ms(16, 0), zone))
        assertEquals(ms(16, 0, 23), GeoLogic.nextBoundary(r, ms(21, 0), zone))
        assertNull(GeoLogic.nextBoundary(reminder(-1, -1), ms(9, 0), zone))
    }

    @Test
    fun watching() {
        val r = reminder(16 * 60, 20 * 60)
        assertTrue(GeoLogic.isWatching(r, ms(17, 0), zone))
        assertFalse(GeoLogic.isWatching(r, ms(21, 0), zone))
        assertFalse(GeoLogic.isWatching(r.copy(placeArmed = false), ms(17, 0), zone))
        assertFalse(GeoLogic.isWatching(r.copy(done = true), ms(17, 0), zone))
        assertEquals(GeoLogic.MIN_INTERVAL_MIN, GeoLogic.intervalMin(r.copy(checkIntervalMin = 0)))
    }

    @Test
    fun ssid() {
        assertEquals("FRITZ!Box 7590", Ssid.normalize("\"FRITZ!Box 7590\""))
        assertNull(Ssid.normalize("<unknown ssid>"))
        assertTrue(Ssid.matches("\"Heim-5G\"", listOf("heim-5g", "Heim")))
        assertFalse(Ssid.matches(null, listOf("Heim")))
    }
}
