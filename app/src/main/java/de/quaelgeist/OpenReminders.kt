package de.quaelgeist

/**
 * Weitere offene Erinnerungen für den Verschieben-Bildschirm (reine Funktion, `OpenRemindersTest`).
 * „Offen" = wie der Abschnitt „Überfällig" in der Übersicht ([DueGroups.isOverdue]): fällig
 * oder nervt gerade, nicht verschoben. Älteste nächste Meldung zuerst.
 */
object OpenReminders {
    fun others(all: List<Reminder>, currentId: Long, now: Long): List<Reminder> =
        all.filter { it.id != currentId && DueGroups.isOverdue(it, now) }.sortedBy { it.fireAt }
}
