package de.quaelgeist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class SettingsTest {

    private fun t(h: Int, m: Int) = LocalTime.of(h, m)

    @Test
    fun presetsSortedDistinctMaxFour() {
        assertEquals(
            listOf(t(7, 0), t(9, 30), t(12, 0), t(17, 0)),
            SettingsRules.normalizePresets(listOf(t(17, 0), t(9, 30), t(12, 0), t(9, 30), t(7, 0), t(20, 0)).take(5)),
        )
    }

    @Test
    fun tooFewPresetsAreFilledFromDefaults() {
        assertEquals(listOf(t(9, 30), t(12, 0)), SettingsRules.normalizePresets(listOf(t(12, 0))))
        assertEquals(QuickTimes.PRESETS, SettingsRules.normalizePresets(emptyList()))
    }

    @Test
    fun durationLabels() {
        assertEquals("10 Min", SettingsRules.durationLabel(10))
        assertEquals("1 Std", SettingsRules.durationLabel(60))
        assertEquals("1 Std 30 Min", SettingsRules.durationLabel(90))
        assertEquals("3 Std", SettingsRules.durationLabel(180))
    }

    @Test
    fun parseWithinRange() {
        assertEquals(150, SettingsRules.parse(" 150 ", SettingsRules.RADIUS_RANGE))
        assertNull(SettingsRules.parse("10", SettingsRules.RADIUS_RANGE))
        assertNull(SettingsRules.parse("", SettingsRules.SNOOZE_RANGE))
        assertEquals(0, SettingsRules.parse("0", SettingsRules.NAG_RANGE))
    }
}
