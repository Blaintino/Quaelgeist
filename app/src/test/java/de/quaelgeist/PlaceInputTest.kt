package de.quaelgeist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceInputTest {

    private fun found(r: PlaceInput.Result): PlaceInput.Result.Found {
        assertTrue("erwartet Found, war $r", r is PlaceInput.Result.Found)
        return r as PlaceInput.Result.Found
    }

    @Test
    fun coordinatesFromGoogleMapsLongPress() {
        val r = found(PlaceInput.parse("50.984815, 11.029857"))
        assertEquals(50.984815, r.lat, 1e-9)
        assertEquals(11.029857, r.lon, 1e-9)
    }

    @Test
    fun geoAndMapsLinks() {
        found(PlaceInput.parse("geo:50.9848,11.0298?z=17")).let { assertEquals(11.0298, it.lon, 1e-9) }
        found(PlaceInput.parse("https://www.google.com/maps/place/X/@50.9848,11.0298,17z/data=!3d50.9851!4d11.0301"))
            .let {
                // !3d/!4d ist der Ort selbst, @… nur die Kartenmitte.
                assertEquals(50.9851, it.lat, 1e-9)
                assertEquals(11.0301, it.lon, 1e-9)
            }
    }

    @Test
    fun plusCodes() {
        found(PlaceInput.parse("8FVC9G8F+6X")).let { assertEquals(47.3655625, it.lat, 1e-6) }
        assertEquals(PlaceInput.Result.NeedsReference("9G8F+6X"), PlaceInput.parse("9G8F+6X Zürich"))
        found(PlaceInput.parse("9G8F+6X Zürich", 47.4, 8.6)).let { assertEquals(8.5249375, it.lon, 1e-6) }
    }

    @Test
    fun shortLinkExplainsWhatToDo() {
        val r = PlaceInput.parse("Schneiderei Müller\nhttps://maps.app.goo.gl/AbC123xyz")
        assertEquals(PlaceInput.Result.Error(PlaceInput.SHORT_LINK_HINT), r)
    }

    @Test
    fun invalidInput() {
        assertTrue(PlaceInput.parse("") is PlaceInput.Result.Error)
        assertTrue(PlaceInput.parse("Hauptstraße 12, 99084 Erfurt") is PlaceInput.Result.Error)
        assertTrue(PlaceInput.parse("95.123456, 11.123456") is PlaceInput.Result.Error)
    }

    @Test
    fun nameSuggestion() {
        assertEquals(
            "Schneiderei Müller",
            PlaceInput.suggestName("Schneiderei Müller\nHauptstr. 1\nhttps://maps.app.goo.gl/AbC"),
        )
        assertEquals("", PlaceInput.suggestName("50.9848, 11.0298"))
    }
}
