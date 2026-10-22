package de.quaelgeist

import kotlin.math.floor
import kotlin.math.pow

/**
 * Plus Codes (Open Location Code) offline umrechnen, ohne Bibliothek und ohne Netzwerk.
 * Volle Codes (`9F3X3V4M+2X`) lassen sich direkt umrechnen. Kurze Codes (`3V4M+2X`, so zeigt
 * Google Maps sie meist mit Ortsnamen) brauchen einen Bezugspunkt in der Nähe (bis grob 50 km);
 * den Ortsnamen selbst auflösen ginge nur mit Internet. Tests: `PlusCodeTest`.
 */
object PlusCode {

    private const val ALPHABET = "23456789CFGHJMPQRVWX"
    private const val SEPARATOR_POS = 8
    private const val PAIR_LENGTH = 10
    private const val GRID_ROWS = 5
    private const val GRID_COLS = 4

    /** Kandidaten im Text, z. B. „3V4M+2X Erfurt“ oder „9F3X3V4M+2X“. */
    private val PATTERN = Regex(
        """(?<![0-9A-Za-z])([$ALPHABET]{2,8}\+[$ALPHABET]{0,7})(?![0-9A-Za-z+])""",
        RegexOption.IGNORE_CASE,
    )

    data class LatLon(val lat: Double, val lon: Double)

    fun find(text: String): String? = PATTERN.find(text)?.groupValues?.get(1)?.uppercase()

    fun isFull(code: String): Boolean = code.indexOf('+') == SEPARATOR_POS && isValid(code)

    fun isShort(code: String): Boolean {
        val sep = code.indexOf('+')
        return sep in 2 until SEPARATOR_POS && sep % 2 == 0 && isValid(code)
    }

    private fun isValid(code: String): Boolean {
        val c = code.uppercase()
        val sep = c.indexOf('+')
        if (sep < 0 || sep != c.lastIndexOf('+')) return false
        val after = c.length - sep - 1
        if (after == 1) return false
        return c.all { it == '+' || it in ALPHABET }
    }

    /** Mitte der Fläche eines vollen Codes, sonst null. */
    fun decode(code: String): LatLon? {
        val c = code.uppercase()
        if (!isFull(c)) return null
        val digits = c.replace("+", "")
        var lat = -90.0
        var lon = -180.0
        var res = 20.0
        var i = 0
        // Paare: Breite, Länge – 20°, 1°, 0,05°, 0,0025°, 0,000125°.
        while (i < minOf(digits.length, PAIR_LENGTH)) {
            lat += ALPHABET.indexOf(digits[i]) * res
            lon += ALPHABET.indexOf(digits[i + 1]) * res
            i += 2
            if (i < minOf(digits.length, PAIR_LENGTH)) res /= 20
        }
        var latRes = res
        var lonRes = res
        // Weitere Stellen: Raster 5 Zeilen × 4 Spalten.
        while (i < digits.length) {
            latRes /= GRID_ROWS
            lonRes /= GRID_COLS
            val v = ALPHABET.indexOf(digits[i])
            lat += (v / GRID_COLS) * latRes
            lon += (v % GRID_COLS) * lonRes
            i++
        }
        return LatLon(lat + latRes / 2, lon + lonRes / 2)
    }

    /** Kurzen Code mit Bezugspunkt zum nächstgelegenen vollen Code ergänzen und umrechnen. */
    fun decodeNear(code: String, refLat: Double, refLon: Double): LatLon? {
        val c = code.uppercase()
        if (isFull(c)) return decode(c)
        if (!isShort(c)) return null
        val missing = SEPARATOR_POS - c.indexOf('+')
        val resolution = 20.0.pow(2 - missing / 2)
        val half = resolution / 2
        val full = decode(encode(refLat, refLon).substring(0, missing) + c) ?: return null
        var lat = full.lat
        var lon = full.lon
        if (refLat + half < lat && lat - resolution >= -90) lat -= resolution
        else if (refLat - half > lat && lat + resolution <= 90) lat += resolution
        if (refLon + half < lon) lon -= resolution
        else if (refLon - half > lon) lon += resolution
        return LatLon(lat, normalizeLon(lon))
    }

    /** Voller Code mit 10 Stellen (Genauigkeit ca. 14 m). */
    fun encode(lat: Double, lon: Double): String {
        var la = lat.coerceIn(-90.0, 90.0) + 90
        if (la >= 180) la = 180 - 1e-9
        var lo = normalizeLon(lon) + 180
        val sb = StringBuilder()
        var res = 20.0
        repeat(PAIR_LENGTH / 2) {
            val dLa = floor(la / res).toInt().coerceIn(0, 19)
            val dLo = floor(lo / res).toInt().coerceIn(0, 19)
            sb.append(ALPHABET[dLa]).append(ALPHABET[dLo])
            la -= dLa * res
            lo -= dLo * res
            res /= 20
        }
        return sb.insert(SEPARATOR_POS, '+').toString()
    }

    private fun normalizeLon(lon: Double): Double {
        var l = lon
        while (l < -180) l += 360
        while (l >= 180) l -= 360
        return l
    }
}
