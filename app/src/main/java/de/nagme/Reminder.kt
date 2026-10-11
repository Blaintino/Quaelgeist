package de.nagme

enum class RepeatType { NONE, DAILY, WEEKLY, MONTHLY_DAY, MONTHLY_NTH_WEEKDAY }

/**
 * baseAt  = geplanter Termin (bei Wiederholungen der aktuelle Termin der Serie)
 * fireAt  = Zeitpunkt der nächsten Benachrichtigung (Termin, verschobener Termin oder nächster Nag)
 * anchorAt = erster Termin der Serie; liefert Uhrzeit, Tag im Monat und „n-ter Wochentag"
 *            für die Wiederholungsregel (siehe [Recurrence.next])
 */
data class Reminder(
    val id: Long,
    val title: String,
    val baseAt: Long,
    val fireAt: Long,
    val anchorAt: Long = baseAt,
    val repeatType: RepeatType = RepeatType.NONE,
    val repeatEvery: Int = 1,
    val weekdayMask: Int = 0, // WEEKLY: Bit 0 = Montag ... Bit 6 = Sonntag
    val nagIntervalMin: Int = 10, // 0 = nicht wiederholen
    val maxNags: Int = 0, // 0 = unbegrenzt (Anzahl Wiederholungen nach der ersten Meldung)
    val nagCount: Int = 0,
    val done: Boolean = false,
) {
    /** Darf nach der aktuellen Meldung noch eine weitere Wiederholung folgen? */
    val canNag: Boolean get() = nagIntervalMin > 0 && (maxNags == 0 || nagCount < maxNags)
}
