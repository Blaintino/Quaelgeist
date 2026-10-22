package de.quaelgeist

/**
 * Ort aus eingefügtem oder geteiltem Text lesen, ohne Netzwerk. Tests: `PlaceInputTest`.
 * Erkennt Koordinaten („50.9848, 11.0298“, `geo:`-Links, Google-Maps-Links mit `@…`/`q=…`)
 * und Plus Codes. Ein reiner Kurzlink (`maps.app.goo.gl/…`) enthält keine Koordinaten.
 */
object PlaceInput {

    sealed interface Result {
        data class Found(val lat: Double, val lon: Double, val source: String) : Result

        /** Kurzer Plus Code: braucht einen Bezugspunkt (aktueller Standort oder ein Ort). */
        data class NeedsReference(val code: String) : Result
        data class Error(val message: String) : Result
    }

    /** Zwei Dezimalzahlen mit Komma dazwischen; Google hängt bei `!3d…!4d…` keins an. */
    private val PAIR = Regex("""(-?\d{1,2}\.\d{3,})\s*,\s*(-?\d{1,3}\.\d{3,})""")
    private val DATA = Regex("""!3d(-?\d{1,2}\.\d+)!4d(-?\d{1,3}\.\d+)""")
    private val SHORT_LINK = Regex("""(maps\.app\.goo\.gl|goo\.gl/maps)""", RegexOption.IGNORE_CASE)

    const val SHORT_LINK_HINT =
        "Der Link enthält keine Koordinaten. In Google Maps lange auf den Ort drücken und " +
            "oben die Koordinaten oder den Plus Code antippen (kopiert), dann hier einfügen."

    fun parse(text: String, refLat: Double? = null, refLon: Double? = null): Result {
        val t = text.trim()
        if (t.isEmpty()) return Result.Error("Nichts eingegeben")
        DATA.find(t)?.let { m -> valid(m.groupValues[1], m.groupValues[2])?.let { return it } }
        PAIR.findAll(t).forEach { m -> valid(m.groupValues[1], m.groupValues[2])?.let { return it } }
        PlusCode.find(t)?.let { code ->
            if (PlusCode.isFull(code)) {
                PlusCode.decode(code)?.let { return Result.Found(it.lat, it.lon, "Plus Code") }
            } else if (PlusCode.isShort(code)) {
                if (refLat == null || refLon == null) return Result.NeedsReference(code)
                PlusCode.decodeNear(code, refLat, refLon)
                    ?.let { return Result.Found(it.lat, it.lon, "Plus Code") }
            }
        }
        if (SHORT_LINK.containsMatchIn(t)) return Result.Error(SHORT_LINK_HINT)
        return Result.Error("Keine Koordinaten oder Plus Code gefunden")
    }

    private fun valid(a: String, b: String): Result.Found? {
        val lat = a.toDoubleOrNull() ?: return null
        val lon = b.toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return Result.Found(lat, lon, "Koordinaten")
    }

    /**
     * Namensvorschlag aus geteiltem Text: Google Maps schickt „Name\nAdresse\nLink“.
     * Erste Zeile ohne Link und ohne Koordinaten/Plus Code.
     */
    fun suggestName(text: String): String =
        text.lines().map { it.trim() }.firstOrNull { line ->
            line.isNotEmpty() && !line.contains("://") && !line.startsWith("geo:", true) &&
                !PAIR.containsMatchIn(line) && PlusCode.find(line) == null
        }?.take(40).orEmpty()
}
