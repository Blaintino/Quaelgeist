package de.quaelgeist

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Gespeicherter Ort für Orts-Erinnerungen („Zuhause“, „Arbeit“ …). */
data class Place(
    val id: Long,
    val name: String,
    val lat: Double,
    val lon: Double,
    val radiusM: Int = GeoLogic.DEFAULT_RADIUS_M,
    val ssids: List<String> = emptyList(), // WLANs des Orts: verbunden = „drin“
)

/** Orte und der zuletzt erkannte Zustand je Ort, lokal in SharedPreferences (wie [ReminderStore]). */
class PlaceStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("places", Context.MODE_PRIVATE)

    @Synchronized
    fun all(): List<Place> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getJSONObject(it).toPlace() }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun get(id: Long): Place? = all().firstOrNull { it.id == id }

    @Synchronized
    fun upsert(p: Place) {
        val list = all().toMutableList()
        val i = list.indexOfFirst { it.id == p.id }
        if (i >= 0) list[i] = p else list.add(p)
        save(list)
        // Geänderter Ort (Lage, Radius, WLAN): Zustand neu bestimmen statt falsch auszulösen.
        setPresence(p.id, Presence.UNKNOWN)
    }

    @Synchronized
    fun delete(id: Long) {
        save(all().filter { it.id != id })
    }

    fun presence(id: Long): Presence =
        runCatching { Presence.valueOf(prefs.getString("presence_$id", null)!!) }
            .getOrDefault(Presence.UNKNOWN)

    @SuppressLint("ApplySharedPref")
    fun setPresence(id: Long, p: Presence) {
        prefs.edit().putString("presence_$id", p.name).commit()
        version.value = version.value + 1
    }

    /** Orte, deren Überwachung gerade läuft ([GeoWatch.replan]). */
    var watched: Set<Long>
        get() = prefs.getStringSet(KEY_WATCHED, emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
        @SuppressLint("ApplySharedPref")
        set(v) {
            prefs.edit().putStringSet(KEY_WATCHED, v.map { it.toString() }.toSet()).commit()
        }

    @SuppressLint("ApplySharedPref")
    private fun save(list: List<Place>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY, arr.toString()).commit()
        version.value = version.value + 1
    }

    companion object {
        private const val KEY = "list"
        private const val KEY_WATCHED = "watched"

        /** Zähler wie [ReminderStore.version] – die Oberfläche lädt dann neu. */
        val version = MutableStateFlow(0)
    }
}

private fun Place.toJson() = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("lat", lat)
    put("lon", lon)
    put("radiusM", radiusM)
    put("ssids", JSONArray(ssids))
}

private fun JSONObject.toPlace() = Place(
    id = getLong("id"),
    name = getString("name"),
    lat = getDouble("lat"),
    lon = getDouble("lon"),
    radiusM = optInt("radiusM", GeoLogic.DEFAULT_RADIUS_M),
    ssids = optJSONArray("ssids")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
)

/**
 * Kleines Protokoll der Ortserkennung (Geofence, Standort, WLAN, Auslösungen), zum Nachsehen
 * im Orte-Bildschirm, falls eine Erinnerung nicht oder falsch kam.
 */
object GeoLog {
    private const val MAX = 150
    private const val KEY = "lines"

    val version = MutableStateFlow(0)

    @SuppressLint("ApplySharedPref")
    @Synchronized
    fun add(ctx: Context, line: String) {
        val prefs = ctx.applicationContext.getSharedPreferences("geolog", Context.MODE_PRIVATE)
        val time = SimpleDateFormat("dd.MM. HH:mm:ss", Locale.GERMANY).format(Date())
        val lines = (listOf("$time  $line") + read(ctx)).take(MAX)
        prefs.edit().putString(KEY, JSONArray(lines).toString()).commit()
        version.value = version.value + 1
    }

    /** Neueste zuerst. */
    fun read(ctx: Context): List<String> {
        val raw = ctx.applicationContext.getSharedPreferences("geolog", Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList())
    }

    @SuppressLint("ApplySharedPref")
    fun clear(ctx: Context) {
        ctx.applicationContext.getSharedPreferences("geolog", Context.MODE_PRIVATE)
            .edit().remove(KEY).commit()
        version.value = version.value + 1
    }
}
