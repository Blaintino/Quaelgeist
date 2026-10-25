package de.quaelgeist

enum class RepeatType { NONE, DAILY, WEEKLY, MONTHLY_DAY, MONTHLY_NTH_WEEKDAY }

/** Wochenend-Modus für tägliche/wöchentliche Serien (siehe [Recurrence.timeOn]). */
enum class WeekendMode { NORMAL, SKIP, OTHER_TIME }

/** Orts-Erinnerung: beim Ankommen oder beim Verlassen (siehe [GeoLogic]). */
enum class PlaceTrigger { ARRIVE, LEAVE }

/**
 * baseAt  = geplanter Termin (bei Wiederholungen der aktuelle Termin der Serie)
 * fireAt  = Zeitpunkt der nächsten Benachrichtigung (Termin, verschobener Termin oder nächster Nag)
 * Erledigte einmalige Erinnerungen ([done]) stehen im Archiv-Tab.
 * anchorAt = erster Termin der Serie; liefert Uhrzeit, Tag im Monat und „n-ter Wochentag"
 *            für die Wiederholungsregel (siehe [Recurrence.next])
 *
 * Orts-Erinnerungen ([placeId] != 0, nur einmalig): Solange [placeArmed], wartet sie ohne
 * Zeit-Alarm auf Ankommen/Verlassen ([GeoWatch]). Beim Auslösen werden baseAt/fireAt auf den
 * Auslösezeitpunkt gesetzt, danach nervt sie wie jede andere Erinnerung.
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
    val doneAt: Long = 0, // Zeitpunkt des Abhakens (nur erledigte einmalige; 0 = unbekannt)
    val weekendMode: WeekendMode = WeekendMode.NORMAL,
    val weekendTimeMin: Int = DEFAULT_WEEKEND_TIME_MIN, // Minuten ab Mitternacht (OTHER_TIME)
    val link: String = "", // http(s)-Link oder leer; „Link öffnen" in der Meldung (siehe [Links])
    val placeId: Long = 0, // 0 = Zeit-Erinnerung, sonst [Place.id]
    val placeTrigger: PlaceTrigger = PlaceTrigger.ARRIVE,
    val placeArmed: Boolean = false, // wartet auf den Ort (noch nicht ausgelöst)
    val windowStartMin: Int = -1, // Zeitfenster der Ortserkennung, Minuten ab Mitternacht; -1 = immer
    val windowEndMin: Int = -1,
    val checkIntervalMin: Int = GeoLogic.DEFAULT_INTERVAL_MIN, // Standort-Prüfintervall
) {
    val isPlace: Boolean get() = placeId != 0L

    /** Darf nach der aktuellen Meldung noch eine weitere Wiederholung folgen? */
    val canNag: Boolean get() = nagIntervalMin > 0 && (maxNags == 0 || nagCount < maxNags)

    companion object {
        const val DEFAULT_WEEKEND_TIME_MIN = 7 * 60
    }
}
