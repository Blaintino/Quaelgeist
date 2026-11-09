package de.quaelgeist

import de.blaintino.contract.AppContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InteropTest {
    private val id = "6F1C2B3A-1111-4222-8333-944455556666"
    private fun v(
        externalId: String? = id, source: String? = "de.ddbd", title: String? = " Müll rausbringen ", note: String? = null,
        dueAt: Long? = null, fromName: String? = "Lisa", openUri: String? = "ddbd://list/abc", version: Int = AppContract.VERSION,
    ) = Interop.validate(externalId, source, title, note, dueAt, fromName, openUri, version)

    @Test fun gueltigeAnfrage() {
        val r = v()!!
        assertNull(r.error)
        assertEquals(id.lowercase(), r.externalId)
        assertEquals("Müll rausbringen", r.title)
        assertEquals("ddbd://list/abc", r.openUri)
    }

    @Test fun ohneIdOderAbsenderKeineAntwort() {
        assertNull(v(externalId = "keine-uuid"))
        assertNull(v(source = null))
        assertNull(v(source = "kein paket"))
    }

    @Test fun ungueltigeWerteWerdenAbgelehnt() {
        assertTrue(v(title = "  ")!!.error != null)
        assertTrue(v(title = "x".repeat(1001))!!.error != null)
        assertTrue(v(note = "x".repeat(10_001))!!.error != null)
        assertTrue(v(version = 2)!!.error != null)
        assertTrue(v(dueAt = 0)!!.error != null)
    }

    @Test fun nurDdbdLinks() {
        assertTrue(v(openUri = "https://example.com")!!.error != null)
        assertTrue(v(openUri = "javascript:alert(1)")!!.error != null)
        assertTrue(v(openUri = "ddbd://list/a b")!!.error != null)
        assertNull(v(openUri = null)!!.error)
        assertNull(v(openUri = "")!!.error)
    }
}
