package de.quaelgeist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinksTest {

    @Test
    fun linkInTitleMovesToEmptyLinkField() {
        assertEquals(
            "Artikel lesen" to "https://example.com/a?b=1",
            Links.split("Artikel lesen https://example.com/a?b=1", ""),
        )
        assertEquals(
            "Formular ausfüllen" to "http://amt.de/form",
            Links.split("Formular ausfüllen: http://amt.de/form", ""),
        )
    }

    @Test
    fun onlyLinkInTitleStaysTitle() {
        assertEquals(
            "https://example.com/x" to "https://example.com/x",
            Links.split("  https://example.com/x  ", ""),
        )
    }

    @Test
    fun trailingPunctuationIsNotPartOfLink() {
        assertEquals("https://example.com/x", Links.find("siehe https://example.com/x)."))
    }

    @Test
    fun filledLinkFieldWins() {
        assertEquals(
            "Lesen https://a.de" to "https://b.de",
            Links.split("Lesen https://a.de", " https://b.de "),
        )
    }

    @Test
    fun titleWithoutLinkUnchanged() {
        assertEquals("Müll rausbringen" to "", Links.split(" Müll rausbringen ", ""))
        assertEquals("mailto:a@b.de" to "", Links.split("mailto:a@b.de", ""))
    }

    @Test
    fun normalize() {
        assertEquals("", Links.normalize("  "))
        assertEquals("https://example.com", Links.normalize("example.com"))
        assertEquals("https://example.com:8080/x", Links.normalize("example.com:8080/x"))
        assertEquals("HTTP://Example.com", Links.normalize(" HTTP://Example.com "))
        assertNull(Links.normalize("mailto:a@b.de"))
        assertNull(Links.normalize("tel:0123"))
        assertNull(Links.normalize("https://"))
        assertNull(Links.normalize("zwei wörter"))
    }

    @Test
    fun isOpenable() {
        assertTrue(Links.isOpenable("https://a.de"))
        assertTrue(Links.isOpenable("http://a.de"))
        assertFalse(Links.isOpenable(""))
        assertFalse(Links.isOpenable("ftp://a.de"))
    }
}
