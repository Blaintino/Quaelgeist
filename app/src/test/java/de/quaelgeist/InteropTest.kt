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
        revision: Long = 0,
    ) = Interop.validate(externalId, source, title, note, dueAt, fromName, openUri, version, revision)

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

    // ------------------------------------------------------------- Ändern (REVISION)
    private val now = 1_800_000_000_000L
    private fun r(base: Long = now + 3_600_000, rev: Long = 5, nag: Int = 0) = Reminder(
        id = 1, title = "Müll", baseAt = base, fireAt = base, nagCount = nag,
        origin = Origin(id.lowercase(), "de.ddbd", "Lisa", "alt", "", rev),
    )
    private fun req(rev: Long, due: Long? = null, title: String = "Müll rausbringen") =
        v(title = title, dueAt = due, openUri = null, revision = rev)!!

    @Test fun gleicherOderAelterStandAendertNichts() {
        assertNull(Interop.applyUpdate(r(rev = 5), req(5), now))
        assertNull(Interop.applyUpdate(r(rev = 5), req(4), now))
        assertNull("ohne origin", Interop.applyUpdate(r().copy(origin = null), req(9), now))
    }

    @Test fun neuerStandUebernimmtTextUndZeit() {
        val later = now + 7_200_000
        val u = Interop.applyUpdate(r(nag = 3), req(6, due = later), now)!!
        assertEquals("Müll rausbringen", u.title)
        assertEquals(6L, u.origin!!.revision)
        assertEquals(later, u.baseAt); assertEquals(later, u.fireAt); assertEquals(0, u.nagCount)
    }

    @Test fun nurTextGeaendertBehaeltVerschieben() {
        val snoozed = r().copy(fireAt = now + 99_000, nagCount = 2)
        val u = Interop.applyUpdate(snoozed, req(6, due = snoozed.baseAt), now)!!
        assertEquals(now + 99_000, u.fireAt); assertEquals(2, u.nagCount)
    }

    @Test fun sofortZiehtNurNochNichtFaelligeVor() {
        assertEquals(now, Interop.applyUpdate(r(base = now + 60_000), req(6), now)!!.fireAt)
        val overdue = r(base = now - 60_000).copy(fireAt = now + 600_000)
        assertEquals("läuft schon: Nag-Stand bleibt", now + 600_000, Interop.applyUpdate(overdue, req(6), now)!!.fireAt)
    }
}
