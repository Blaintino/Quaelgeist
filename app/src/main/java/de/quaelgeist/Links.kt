package de.quaelgeist

/**
 * Links an Erinnerungen (nur http/https). Reine Funktionen, siehe `LinksTest`.
 *
 * Steht ein Link im Titel und ist das Linkfeld leer, wandert er beim Speichern ins Linkfeld
 * (Wunsch des Nutzers). Bleibt dann vom Titel nichts übrig, wird der Link selbst der Titel.
 */
object Links {

    /** Erster http(s)-Link im Text; Satzzeichen am Ende gehören nicht dazu. */
    private val URL = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
    private const val TRAILING = ".,;:!?)]}'"

    fun find(text: String): String? =
        URL.find(text)?.value?.trimEnd { it in TRAILING }?.takeIf { it.length > "https://".length }

    /**
     * Linkfeld leer und Link im Titel: Link aus dem Titel ins Linkfeld übernehmen.
     * Liefert (Titel, Link).
     */
    fun split(title: String, link: String): Pair<String, String> {
        val trimmed = title.trim()
        if (link.isNotBlank()) return trimmed to link.trim()
        val url = find(trimmed) ?: return trimmed to ""
        val rest = trimmed.replace(url, " ").replace(Regex("""\s+"""), " ").trim()
        val cleaned = rest.trim { it.isWhitespace() || it in "-–:|" }
        return (cleaned.ifEmpty { url }) to url
    }

    /**
     * Eingabe im Linkfeld vereinheitlichen: ohne Schema gilt https://. Andere Schemata
     * (mailto:, tel: …) werden nicht unterstützt → null.
     */
    fun normalize(input: String): String? {
        val s = input.trim()
        if (s.isEmpty()) return ""
        if (s.any(Char::isWhitespace)) return null
        if (HTTP.matches(s)) return s
        // Anderes Schema (mailto:, tel: …); „example.com:8080" hat einen Punkt, also Host mit Port.
        if (OTHER_SCHEME.containsMatchIn(s)) return null
        return "https://$s"
    }

    private val HTTP = Regex("""^https?://\S+$""", RegexOption.IGNORE_CASE)
    private val OTHER_SCHEME = Regex("""^[a-zA-Z][a-zA-Z0-9+-]*:""")

    fun isOpenable(link: String): Boolean =
        link.startsWith("https://", true) || link.startsWith("http://", true)
}
