package de.quaelgeist

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import de.blaintino.contract.AppContract
import de.blaintino.contract.AppContract.Quaelgeist as Q

/**
 * Schnittstelle für die eigenen Apps (Vertrag in `de.blaintino.contract.AppContract`, Spezifikation ddbd 12.3):
 * Die Listen-App „Denk dran“ legt per Broadcast `CREATE_REMINDER` eine Erinnerung an, ohne Oberfläche,
 * ändert sie mit derselben id und höherer `REVISION` und entfernt sie per `CANCEL_REMINDER`.
 * Quälgeist meldet `accepted`, später `done` (abgehakt) oder `dismissed` (gelöscht), bei ungültigen
 * Angaben `rejected` mit Grund. Nur Apps mit derselben Signatur dürfen senden und empfangen.
 */
object Interop {

    /** Geprüfte Anfrage. [error] != null: ablehnen (wenn möglich mit `rejected` antworten). */
    data class Request(
        val externalId: String,
        val sourcePackage: String,
        val title: String = "",
        val note: String = "",
        val dueAt: Long? = null,
        val fromName: String = "",
        val openUri: String = "",
        val revision: Long = 0,
        val error: String? = null,
    )

    private val PACKAGE = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

    /**
     * Prüft alle Extras (Spezifikation 14.8). null = so ungültig, dass nicht einmal eine Antwort möglich ist
     * (keine gültige id oder kein Absender).
     */
    fun validate(
        externalId: String?, sourcePackage: String?, title: String?, note: String?, dueAt: Long?,
        fromName: String?, openUri: String?, version: Int, revision: Long = 0,
    ): Request? {
        if (!AppContract.isUuid(externalId) || sourcePackage == null || !PACKAGE.matches(sourcePackage)) return null
        val base = Request(externalId!!.lowercase(), sourcePackage)
        val t = title?.trim().orEmpty()
        return when {
            version != AppContract.VERSION -> base.copy(error = "Vertragsversion $version wird nicht unterstützt")
            t.isEmpty() || t.length > Q.MAX_TITLE -> base.copy(error = "Titel fehlt oder ist zu lang")
            (note?.length ?: 0) > Q.MAX_NOTE -> base.copy(error = "Notiz ist zu lang")
            (fromName?.length ?: 0) > Q.MAX_FROM_NAME -> base.copy(error = "Absender ist zu lang")
            dueAt != null && dueAt <= 0 -> base.copy(error = "Ungültiger Zeitpunkt")
            revision < 0 -> base.copy(error = "Ungültiger Stand")
            // OPEN_URI nur mit ddbd://, Quälgeist öffnet nichts anderes (14.8)
            !openUri.isNullOrEmpty() && !isOpenableUri(openUri) -> base.copy(error = "Ungültiger Link")
            else -> base.copy(title = t, note = note?.trim().orEmpty(), dueAt = dueAt,
                fromName = fromName?.trim().orEmpty(), openUri = openUri.orEmpty(), revision = revision)
        }
    }

    fun isOpenableUri(uri: String): Boolean =
        uri.startsWith(AppContract.Ddbd.URI_PREFIX) && uri.length <= 200 && uri.none { it.isWhitespace() }

    /** Anlegen oder (gleiche id) nur den aktuellen Stand melden. Liefert die Antwort an den Absender. */
    fun create(ctx: Context, req: Request) {
        if (req.error != null) { report(ctx, req.externalId, req.sourcePackage, Q.STATUS_REJECTED, req.error); return }
        val store = ReminderStore(ctx)
        val existing = store.all().firstOrNull { it.origin?.externalId == req.externalId }
        if (existing != null) {
            // Gleicher Stand erneut geschickt: nichts ändern. Neuerer Stand: offene Erinnerung anpassen.
            if (!existing.done) applyUpdate(existing, req, System.currentTimeMillis())?.let { u ->
                Notifier.cancel(ctx, u.id)
                store.upsert(u)
                Scheduler.schedule(ctx, u)
            }
            report(ctx, req.externalId, req.sourcePackage, if (existing.done) Q.STATUS_DONE else Q.STATUS_ACCEPTED)
            return
        }
        val at = req.dueAt ?: System.currentTimeMillis()
        var id = ReminderStore.newId()
        while (store.get(id) != null) id++                               // mehrere Aufrufe in derselben Millisekunde
        val r = Reminder(
            id = id, title = req.title, baseAt = at, fireAt = at,
            nagIntervalMin = Settings.get(ctx).nagIntervalMin,
            origin = Origin(req.externalId, req.sourcePackage, req.fromName, req.note, req.openUri, req.revision),
        )
        store.upsert(r)
        Scheduler.schedule(ctx, r)
        report(ctx, req.externalId, req.sourcePackage, Q.STATUS_ACCEPTED)
        Log.i("Quaelgeist", "Erinnerung ${r.id} für ${req.sourcePackage} angelegt")
    }

    /**
     * Neuerer Stand für eine offene Erinnerung (reine Funktion, `InteropTest`). null = nichts zu tun, weil
     * [Request.revision] nicht größer ist. Ein neuer Zeitpunkt setzt Termin und nächste Meldung neu; „sofort“
     * zieht nur eine noch nicht fällige Erinnerung auf jetzt vor. Sonst bleiben Verschieben und Nag-Stand erhalten.
     */
    fun applyUpdate(r: Reminder, req: Request, now: Long): Reminder? {
        val o = r.origin ?: return null
        if (req.revision <= o.revision) return null
        var u = r.copy(
            title = req.title,
            origin = o.copy(note = req.note, fromName = req.fromName, openUri = req.openUri, revision = req.revision),
        )
        val due = req.dueAt
        if (due != null && due != r.baseAt) u = u.copy(baseAt = due, fireAt = due, anchorAt = due, nagCount = 0)
        else if (due == null && r.baseAt > now) u = u.copy(baseAt = now, fireAt = now, anchorAt = now, nagCount = 0)
        return u
    }

    /** Zurückgezogen: offene Erinnerung ohne Rückmeldung entfernen (nur vom selben Absender). */
    fun cancel(ctx: Context, externalId: String, sourcePackage: String) {
        val store = ReminderStore(ctx)
        val r = store.all().firstOrNull { it.origin?.externalId == externalId && it.origin?.sourcePackage == sourcePackage } ?: return
        if (r.done) return
        Scheduler.cancel(ctx, r.id)
        Notifier.cancel(ctx, r.id)
        store.delete(r.id)
        if (r.isPlace) GeoWatch.replan(ctx)
        Log.i("Quaelgeist", "Erinnerung ${r.id} von $sourcePackage zurückgezogen")
    }

    /** Erledigt bzw. gelöscht an die anlegende App melden (nur bei Erinnerungen mit [Origin]). */
    fun reportDone(ctx: Context, r: Reminder) { r.origin?.let { report(ctx, it.externalId, it.sourcePackage, Q.STATUS_DONE) } }
    fun reportDismissed(ctx: Context, r: Reminder) {
        if (!r.done) r.origin?.let { report(ctx, it.externalId, it.sourcePackage, Q.STATUS_DISMISSED) }
    }

    /** Explizit an [pkg], und nur eine App mit derselben Signatur bekommt die Nachricht. */
    private fun report(ctx: Context, id: String, pkg: String, status: String, reason: String? = null) {
        val i = Intent(Q.ACTION_REMINDER_STATUS).setPackage(pkg)
            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES) // auch wenn „Denk dran“ gerade gestoppt ist
            .putExtra(AppContract.EXTRA_CONTRACT_VERSION, AppContract.VERSION)
            .putExtra(Q.EXTRA_EXTERNAL_ID, id)
            .putExtra(Q.EXTRA_STATUS, status)
            .putExtra(Q.EXTRA_AT, System.currentTimeMillis())
        if (reason != null) i.putExtra(Q.EXTRA_REASON, reason.take(Q.MAX_REASON))
        runCatching { ctx.sendBroadcast(i, AppContract.PERMISSION_INTERNAL) }
    }
}

/** `CREATE_REMINDER` (geschützt durch die Signatur-Berechtigung im Manifest). */
class CreateReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Q.ACTION_CREATE_REMINDER) return
        val req = Interop.validate(
            externalId = intent.getStringExtra(Q.EXTRA_EXTERNAL_ID),
            sourcePackage = intent.getStringExtra(Q.EXTRA_SOURCE_PACKAGE),
            title = intent.getStringExtra(Q.EXTRA_TITLE),
            note = intent.getStringExtra(Q.EXTRA_NOTE),
            dueAt = if (intent.hasExtra(Q.EXTRA_DUE_AT)) intent.getLongExtra(Q.EXTRA_DUE_AT, -1) else null,
            fromName = intent.getStringExtra(Q.EXTRA_FROM_NAME),
            openUri = intent.getStringExtra(Q.EXTRA_OPEN_URI),
            version = intent.getIntExtra(AppContract.EXTRA_CONTRACT_VERSION, -1),
            revision = intent.getLongExtra(Q.EXTRA_REVISION, 0),
        ) ?: return
        Interop.create(ctx, req)
    }
}

/** `CANCEL_REMINDER` (geschützt wie [CreateReminderReceiver]). */
class CancelReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Q.ACTION_CANCEL_REMINDER) return
        val id = intent.getStringExtra(Q.EXTRA_EXTERNAL_ID)?.takeIf(AppContract::isUuid)?.lowercase() ?: return
        val src = intent.getStringExtra(Q.EXTRA_SOURCE_PACKAGE) ?: return
        Interop.cancel(ctx, id, src)
    }
}
