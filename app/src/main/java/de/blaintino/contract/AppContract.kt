package de.blaintino.contract

/**
 * Schnittstellen-Vertrag zwischen den eigenen Apps (Spezifikation ddbd, Abschnitt 12).
 * Diese Datei ist in allen beteiligten Repos identisch (ddbd, Quaelgeist). Änderungen immer in allen
 * Repos gleich machen und bei inkompatiblen Änderungen [VERSION] erhöhen.
 */
object AppContract {
    /** Wird bei jedem Aufruf als [EXTRA_CONTRACT_VERSION] mitgeschickt. */
    const val VERSION = 1
    const val EXTRA_CONTRACT_VERSION = "EXTRA_CONTRACT_VERSION"

    /**
     * Signatur-Berechtigung, die jede App selbst festlegt und anfordert. Nur Apps mit demselben
     * Zertifikat bekommen sie. Achtung: Legen zwei unterschiedlich signierte Apps (z. B. Debug und Release)
     * sie fest, scheitert die Installation der zweiten mit INSTALL_FAILED_DUPLICATE_PERMISSION.
     */
    const val PERMISSION_INTERNAL = "de.blaintino.permission.INTERNAL"

    /** Listen-App „Denk dran“. */
    object Ddbd {
        val PACKAGES = listOf("de.ddbd", "de.ddbd.debug")
        /** Link-Schema; Quälgeist öffnet als OPEN_URI nur URIs mit diesem Präfix. */
        const val URI_PREFIX = "ddbd://"
        fun listUri(id: String) = "ddbd://list/$id"
    }

    /** Erinnerungs-App Quälgeist. Bevorzugt wird das erste installierte Paket. */
    object Quaelgeist {
        val PACKAGES = listOf("de.quaelgeist", "de.quaelgeist.beta", "de.quaelgeist.debug")

        /**
         * Broadcast an Quälgeist: Erinnerung im Hintergrund anlegen. Gibt es die [EXTRA_EXTERNAL_ID] schon,
         * wird eine offene Erinnerung nur geändert, wenn [EXTRA_REVISION] größer ist als die gespeicherte
         * (gleicher Stand erneut geschickt = keine Änderung).
         */
        const val ACTION_CREATE_REMINDER = "de.quaelgeist.action.CREATE_REMINDER"
        /** Broadcast an Quälgeist: offene Erinnerung entfernen (die Absenderin hat zurückgezogen). Ohne Rückmeldung. */
        const val ACTION_CANCEL_REMINDER = "de.quaelgeist.action.CANCEL_REMINDER"
        /** Broadcast von Quälgeist, explizit an [EXTRA_SOURCE_PACKAGE]: Stand der Erinnerung. */
        const val ACTION_REMINDER_STATUS = "de.quaelgeist.action.REMINDER_STATUS"

        const val EXTRA_EXTERNAL_ID = "EXTERNAL_ID"        // String (UUID), Pflicht
        const val EXTRA_SOURCE_PACKAGE = "SOURCE_PACKAGE"  // String, Pflicht
        const val EXTRA_TITLE = "TITLE"                    // String, Pflicht, 1..1000 Zeichen
        const val EXTRA_NOTE = "NOTE"                      // String, optional, bis 10000 Zeichen
        const val EXTRA_DUE_AT = "DUE_AT"                  // Long (Epoch ms), optional; fehlt = sofort
        const val EXTRA_FROM_NAME = "FROM_NAME"            // String, optional, bis 100 Zeichen („von Lisa“)
        const val EXTRA_OPEN_URI = "OPEN_URI"              // String, optional, muss mit ddbd:// beginnen
        const val EXTRA_REVISION = "REVISION"              // Long, optional (fehlt = 0), Stand der Inhalte

        const val EXTRA_STATUS = "STATUS"                  // einer der STATUS_*-Werte
        const val EXTRA_REASON = "REASON"                  // String, nur bei STATUS_REJECTED
        const val EXTRA_AT = "AT"                          // Long (Epoch ms)

        const val STATUS_ACCEPTED = "accepted"
        const val STATUS_DONE = "done"
        const val STATUS_DISMISSED = "dismissed"
        const val STATUS_REJECTED = "rejected"

        const val MAX_TITLE = 1000
        const val MAX_NOTE = 10_000
        const val MAX_FROM_NAME = 100
        const val MAX_REASON = 500
    }

    /** Gültige UUID in der kanonischen Form (36 Zeichen, Kleinbuchstaben oder Großbuchstaben). */
    fun isUuid(s: String?): Boolean =
        s != null && Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$").matches(s)
}
