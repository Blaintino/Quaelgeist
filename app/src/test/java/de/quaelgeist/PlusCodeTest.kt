package de.quaelgeist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlusCodeTest {

    private val eps = 1e-6

    @Test
    fun decodeFullCode() {
        // Beispiel aus der Open-Location-Code-Doku (Zürich): Fläche 47,3655–47,365625 / 8,524875–8,525.
        val p = PlusCode.decode("8FVC9G8F+6X")!!
        assertEquals(47.3655625, p.lat, eps)
        assertEquals(8.5249375, p.lon, eps)
    }

    @Test
    fun encodeRoundTrip() {
        assertEquals("8FVC9G8F+6X", PlusCode.encode(47.365590, 8.524997))
        val p = PlusCode.decode(PlusCode.encode(50.984815, 11.029857))!!
        assertEquals(50.984815, p.lat, 1e-4)
        assertEquals(11.029857, p.lon, 1e-4)
    }

    @Test
    fun shortCodeNeedsReference() {
        val p = PlusCode.decodeNear("9G8F+6X", 47.4, 8.6)!!
        assertEquals(47.3655625, p.lat, eps)
        assertEquals(8.5249375, p.lon, eps)
        // Bezugspunkt knapp jenseits einer Gradgrenze: trotzdem der nächstgelegene Code.
        val q = PlusCode.decodeNear("9G8F+6X", 46.9, 8.1)!!
        assertEquals(47.3655625, q.lat, eps)
        assertEquals(8.5249375, q.lon, eps)
    }

    @Test
    fun shortCodeAroundErfurt() {
        val full = PlusCode.encode(50.984815, 11.029857)
        val short = full.substring(4)
        val p = PlusCode.decodeNear(short, 50.97, 11.03)!!
        assertEquals(50.984815, p.lat, 1e-4)
        assertEquals(11.029857, p.lon, 1e-4)
    }

    @Test
    fun recognizesCodes() {
        assertTrue(PlusCode.isFull("8FVC9G8F+6X"))
        assertTrue(PlusCode.isShort("9G8F+6X"))
        assertFalse(PlusCode.isFull("9G8F+6X"))
        assertFalse(PlusCode.isShort("9G8+6X"))
        assertNull(PlusCode.decode("8FVC9G8F+6"))
        assertEquals("9G8F+6X", PlusCode.find("Plus Code: 9g8f+6x Zürich"))
    }
}
