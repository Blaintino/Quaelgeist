package de.quaelgeist

import java.time.Instant
import java.time.ZoneId

/** Am Ort oder nicht; UNKNOWN = noch nicht bestimmt (z. B. direkt nach Start der Überwachung). */
enum class Presence { INSIDE, OUTSIDE, UNKNOWN }

/**
 * Regeln der Ortserkennung ohne Android, testbar in `GeoLogicTest`.
 *
 * Nur echte Übergänge lösen aus (draußen → drin = Ankommen, drin → draußen = Verlassen).
 * Ein Übergang aus UNKNOWN zählt nicht: Wer „beim Heimkommen“ anlegt, während er zu Hause
 * ist, wird erst nach dem nächsten Weggehen und Wiederkommen erinnert.
 */
object GeoLogic {

    /** Ungenauer gemeldet als das gilt eine Messung nicht als „drin“. */
    const val MAX_INSIDE_ACCURACY_M = 250f

    /**
     * Abstand, den man über den Radius hinaus (abzüglich Messungenauigkeit) weg sein muss,
     * damit es als „verlassen“ zählt. Verhindert Fehlalarme durch springende Messungen am Rand.
     */
    const val LEAVE_MARGIN_M = 50f

    const val DEFAULT_RADIUS_M = 150
    const val DEFAULT_INTERVAL_MIN = 15
    const val MIN_INTERVAL_MIN = 2

    /**
     * Einstufung einer Messung für einen Ort. Ein WLAN des Orts gilt immer als „drin“ und
     * verhindert „draußen“ (Wechsel zwischen WLANs am selben Ort löst so nichts aus).
     * null = nicht eindeutig, bisherigen Zustand behalten.
     */
    fun classify(
        distanceM: Float?,
        accuracyM: Float?,
        radiusM: Int,
        onPlaceWifi: Boolean,
    ): Presence? {
        if (onPlaceWifi) return Presence.INSIDE
        if (distanceM == null) return null
        val acc = accuracyM ?: 0f
        if (distanceM <= radiusM && acc <= MAX_INSIDE_ACCURACY_M) return Presence.INSIDE
        if (distanceM - acc > radiusM + LEAVE_MARGIN_M) return Presence.OUTSIDE
        return null
    }

    /** Welcher Auslöser passt zum Wechsel [old] → [new]? null = keiner. */
    fun triggerFor(old: Presence, new: Presence): PlaceTrigger? = when {
        old == Presence.OUTSIDE && new == Presence.INSIDE -> PlaceTrigger.ARRIVE
        old == Presence.INSIDE && new == Presence.OUTSIDE -> PlaceTrigger.LEAVE
        else -> null
    }

    /**
     * Liegt [minuteOfDay] im Zeitfenster? Ohne Fenster (Start oder Ende < 0) immer.
     * Start > Ende: über Mitternacht (z. B. 22:00–06:00).
     */
    fun inWindow(startMin: Int, endMin: Int, minuteOfDay: Int): Boolean {
        if (startMin < 0 || endMin < 0 || startMin == endMin) return true
        return if (startMin < endMin) minuteOfDay in startMin until endMin
        else minuteOfDay >= startMin || minuteOfDay < endMin
    }

    fun inWindow(r: Reminder, now: Long, zone: ZoneId): Boolean {
        val t = Instant.ofEpochMilli(now).atZone(zone)
        return inWindow(r.windowStartMin, r.windowEndMin, t.hour * 60 + t.minute)
    }

    /** Nächster Fensterbeginn oder -ende nach [now]; null ohne Fenster. */
    fun nextBoundary(r: Reminder, now: Long, zone: ZoneId): Long? {
        if (r.windowStartMin < 0 || r.windowEndMin < 0 || r.windowStartMin == r.windowEndMin) return null
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return listOf(r.windowStartMin, r.windowEndMin).minOf { min ->
            var at = today.atTime(min / 60, min % 60).atZone(zone)
            if (at.toInstant().toEpochMilli() <= now) at = at.plusDays(1)
            at.toInstant().toEpochMilli()
        }
    }

    /** Wartet die Erinnerung auf einen Ort und läuft ihr Zeitfenster gerade? */
    fun isWatching(r: Reminder, now: Long, zone: ZoneId): Boolean =
        !r.done && r.placeArmed && r.placeId != 0L && inWindow(r, now, zone)

    /** Prüfintervall in Minuten, nie unter [MIN_INTERVAL_MIN]. */
    fun intervalMin(r: Reminder): Int = r.checkIntervalMin.coerceAtLeast(MIN_INTERVAL_MIN)
}

/** WLAN-Namen vergleichen (wie in der FH-Stempelkarte). Tests: `GeoLogicTest`. */
object Ssid {
    private const val UNKNOWN = "<unknown ssid>"

    /** Entfernt Android-Anführungszeichen; unbekannte/leere SSID → null. */
    fun normalize(raw: String?): String? {
        var s = raw?.trim() ?: return null
        if (s.length >= 2 && s.startsWith('"') && s.endsWith('"')) s = s.substring(1, s.length - 1)
        if (s.isEmpty() || s.equals(UNKNOWN, ignoreCase = true) || s == "0x") return null
        return s
    }

    fun matches(current: String?, configured: Collection<String>): Boolean {
        val c = normalize(current) ?: return false
        return configured.any { normalize(it)?.equals(c, ignoreCase = true) == true }
    }
}
