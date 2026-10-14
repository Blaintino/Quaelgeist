package de.quaelgeist

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class DueGroupsTest {

    private val zone = ZoneId.of("Europe/Berlin")

    // Oktober 2026: 14. = Mittwoch, 18. = Sonntag, 19. = Montag, 25. = Zeitumstellung.
    private fun at(d: Int, h: Int, m: Int = 0) = ZonedDateTime.of(2026, 10, d, h, m, 0, 0, zone)

    private fun ms(t: ZonedDateTime) = t.toInstant().toEpochMilli()

    private fun reminder(fireAt: ZonedDateTime, nagCount: Int = 0, base: ZonedDateTime = fireAt) =
        Reminder(id = 1, title = "x", baseAt = ms(base), fireAt = ms(fireAt), nagCount = nagCount)

    private fun groupOf(r: Reminder, now: ZonedDateTime) = DueGroups.of(r, now)

    @Test
    fun byDayOfNextNotification() {
        val now = at(14, 10)
        assertEquals(DueGroup.TODAY, groupOf(reminder(at(14, 10, 1)), now))
        assertEquals(DueGroup.TODAY, groupOf(reminder(at(14, 23, 59)), now))
        assertEquals(DueGroup.TOMORROW, groupOf(reminder(at(15, 0)), now))
        assertEquals(DueGroup.THIS_WEEK, groupOf(reminder(at(16, 9)), now))
        assertEquals(DueGroup.THIS_WEEK, groupOf(reminder(at(18, 23, 59)), now))
        assertEquals(DueGroup.LATER, groupOf(reminder(at(19, 0)), now))
    }

    @Test
    fun overdueWhenPastOrNagging() {
        val now = at(14, 10)
        assertEquals(DueGroup.OVERDUE, groupOf(reminder(at(14, 10)), now))
        assertEquals(DueGroup.OVERDUE, groupOf(reminder(at(13, 8)), now))
        // Nervt gerade: nächster Nag in 10 Min, Termin war um 9.
        assertEquals(DueGroup.OVERDUE, groupOf(reminder(at(14, 10, 10), 3, at(14, 9)), now))
        // Verschoben (nagCount 0): steht unter dem neuen Tag, nicht überfällig.
        assertEquals(DueGroup.LATER, groupOf(reminder(at(19, 9), 0, at(14, 9)), now))
    }

    @Test
    fun sundayTomorrowIsMondayAndSaturdayHasEmptyWeek() {
        // Sonntag: Montag ist „Morgen“, nicht „Ab nächster Woche“.
        assertEquals(DueGroup.TOMORROW, groupOf(reminder(at(19, 9)), at(18, 12)))
        assertEquals(DueGroup.LATER, groupOf(reminder(at(20, 9)), at(18, 12)))
        // Samstag: Sonntag ist „Morgen“, Montag schon nächste Woche.
        assertEquals(DueGroup.TOMORROW, groupOf(reminder(at(18, 9)), at(17, 12)))
        assertEquals(DueGroup.LATER, groupOf(reminder(at(19, 9)), at(17, 12)))
    }

    @Test
    fun acrossDstChange() {
        // Samstag 24.10. abends: Sonntag (25., 25 Stunden lang) ist „Morgen“.
        assertEquals(DueGroup.TOMORROW, groupOf(reminder(at(25, 23, 30)), at(24, 23)))
        assertEquals(DueGroup.LATER, groupOf(reminder(at(26, 0, 30)), at(24, 23)))
    }

    @Test
    fun groupKeepsOrderAndSkipsEmpty() {
        val now = at(14, 10)
        val a = reminder(at(14, 11)).copy(id = 1)
        val b = reminder(at(14, 12)).copy(id = 2)
        val c = reminder(at(20, 9)).copy(id = 3)
        val groups = DueGroups.group(listOf(a, b, c), now)
        assertEquals(listOf(DueGroup.TODAY, DueGroup.LATER), groups.map { it.first })
        assertEquals(listOf(1L, 2L), groups[0].second.map { it.id })
    }
}
