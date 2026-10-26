package de.quaelgeist

/**
 * Weitere offene Erinnerungen für den Verschieben-Bildschirm (reine Funktion, `OpenRemindersTest`).
 * „Offen" = wie der Abschnitt „Überfällig" in der Übersicht ([DueGroups.isOverdue]): fällig
 * oder nervt gerade, nicht verschoben. Älteste nächste Meldung zuerst. Orts-Erinnerungen, die
 * noch auf den Ort warten, zählen nicht: Ihr `fireAt` ist nur der Anlagezeitpunkt.
 */
object OpenReminders {
    fun others(all: List<Reminder>, currentId: Long, now: Long): List<Reminder> =
        all.filter { it.id != currentId && !it.placeArmed && DueGroups.isOverdue(it, now) }
            .sortedBy { it.fireAt }
}
