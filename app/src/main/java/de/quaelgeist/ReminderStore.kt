package de.quaelgeist

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Speichert alle Erinnerungen lokal als JSON in SharedPreferences. */
class ReminderStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("reminders", Context.MODE_PRIVATE)

    @Synchronized
    fun all(): List<Reminder> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getJSONObject(it).toReminder() }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun get(id: Long): Reminder? = all().firstOrNull { it.id == id }

    @Synchronized
    fun upsert(r: Reminder) {
        val list = all().toMutableList()
        val i = list.indexOfFirst { it.id == r.id }
        if (i >= 0) list[i] = r else list.add(r)
        save(list)
    }

    @Synchronized
    fun delete(id: Long) {
        save(all().filter { it.id != id })
    }

    /** Entfernt alle erledigten Erinnerungen (Archiv leeren). */
    @Synchronized
    fun deleteDone() {
        save(all().filter { !it.done })
    }

    @SuppressLint("ApplySharedPref")
    private fun save(list: List<Reminder>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        // commit() statt apply(): Der AlarmReceiver läuft nur kurz, der nächste Nag-Termin
        // muss sicher auf der Platte stehen, bevor Android den Prozess beendet.
        prefs.edit().putString(KEY, arr.toString()).commit()
        version.value = version.value + 1
    }

    companion object {
        private const val KEY = "list"

        /** Zähler, der sich bei jeder Änderung erhöht – die Oberfläche lädt dann neu. */
        val version = MutableStateFlow(0)

        fun newId(): Long = System.currentTimeMillis() % Int.MAX_VALUE
    }
}

private fun Reminder.toJson() = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("baseAt", baseAt)
    put("fireAt", fireAt)
    put("anchorAt", anchorAt)
    put("repeatType", repeatType.name)
    put("repeatEvery", repeatEvery)
    put("weekdayMask", weekdayMask)
    put("nagIntervalMin", nagIntervalMin)
    put("maxNags", maxNags)
    put("nagCount", nagCount)
    put("done", done)
    put("doneAt", doneAt)
    put("weekendMode", weekendMode.name)
    put("weekendTimeMin", weekendTimeMin)
    put("link", link)
    put("placeId", placeId)
    put("placeTrigger", placeTrigger.name)
    put("placeArmed", placeArmed)
    put("windowStartMin", windowStartMin)
    put("windowEndMin", windowEndMin)
    put("checkIntervalMin", checkIntervalMin)
}

private fun JSONObject.toReminder() = Reminder(
    id = getLong("id"),
    title = getString("title"),
    baseAt = getLong("baseAt"),
    fireAt = getLong("fireAt"),
    anchorAt = optLong("anchorAt", getLong("baseAt")),
    repeatType = runCatching { RepeatType.valueOf(getString("repeatType")) }
        .getOrDefault(RepeatType.NONE),
    repeatEvery = optInt("repeatEvery", 1),
    weekdayMask = optInt("weekdayMask", 0),
    nagIntervalMin = optInt("nagIntervalMin", 10),
    maxNags = optInt("maxNags", 0),
    nagCount = optInt("nagCount", 0),
    done = optBoolean("done", false),
    doneAt = optLong("doneAt", 0),
    weekendMode = runCatching { WeekendMode.valueOf(optString("weekendMode", "NORMAL")) }
        .getOrDefault(WeekendMode.NORMAL),
    weekendTimeMin = optInt("weekendTimeMin", Reminder.DEFAULT_WEEKEND_TIME_MIN),
    link = optString("link", ""),
    placeId = optLong("placeId", 0),
    placeTrigger = runCatching { PlaceTrigger.valueOf(optString("placeTrigger", "ARRIVE")) }
        .getOrDefault(PlaceTrigger.ARRIVE),
    placeArmed = optBoolean("placeArmed", false),
    windowStartMin = optInt("windowStartMin", -1),
    windowEndMin = optInt("windowEndMin", -1),
    checkIntervalMin = optInt("checkIntervalMin", GeoLogic.DEFAULT_INTERVAL_MIN),
)
