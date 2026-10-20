package de.quaelgeist

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenRemindersTest {

    private val now = 1_000_000L

    private fun r(id: Long, fireAt: Long, nagCount: Int = 0, done: Boolean = false) =
        Reminder(id = id, title = "r$id", baseAt = fireAt, fireAt = fireAt, nagCount = nagCount, done = done)

    @Test
    fun nurAndereOffeneAeltesteZuerst() {
        val all = listOf(
            r(1, now - 10), // aktuelle, wird ausgelassen
            r(2, now - 5),
            r(3, now - 50),
            r(4, now + 60_000, nagCount = 2), // nervt gerade (nächster Nag in der Zukunft)
            r(5, now + 60_000), // verschoben bzw. noch nicht fällig
            r(6, now - 100, done = true), // erledigt
        )
        assertEquals(listOf(3L, 2L, 4L), OpenReminders.others(all, 1, now).map { it.id })
    }

    @Test
    fun leerWennNichtsOffen() {
        assertEquals(emptyList<Reminder>(), OpenReminders.others(listOf(r(1, now - 1)), 1, now))
    }
}
