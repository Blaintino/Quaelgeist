package de.quaelgeist

import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Einstellbare Werte (Wunsch des Nutzers: ändern ohne neue Version). Vorgaben = bisherige feste
 * Werte, damit sich nach dem Update nichts ändert. Zuverlässigkeit (Alarmart, Vibration, Kanal)
 * bleibt bewusst fest. Neue Vorgaben gelten nur für neu angelegte Erinnerungen.
 */
data class AppSettings(
    val presets: List<LocalTime> = QuickTimes.PRESETS, // feste Uhrzeiten, 2–4
    val snoozeMinutes: List<Int> = listOf(10, 60), // Verschieben-Dauern, genau 2
    val nagIntervalMin: Int = 10,
    val weekendTimeMin: Int = Reminder.DEFAULT_WEEKEND_TIME_MIN,
    val placeRadiusM: Int = GeoLogic.DEFAULT_RADIUS_M,
    val placeIntervalMin: Int = GeoLogic.DEFAULT_INTERVAL_MIN,
    val placeWindowStartMin: Int = 8 * 60,
    val placeWindowEndMin: Int = 20 * 60,
    val autoAdvance: Boolean = true, // Verschieben-Bildschirm: danach die nächste offene zeigen
    val showOthers: Boolean = true, // Verschieben-Bildschirm: Liste der weiteren offenen
    val startTab: StartTab = StartTab.ONCE,
)

enum class StartTab(val label: String) { RECURRING("Wiederkehrend"), ONCE("Einmalig"), PLACES("Orte") }

/** Regeln ohne Android, testbar in `SettingsTest`. */
object SettingsRules {
    const val MIN_PRESETS = 2
    const val MAX_PRESETS = 4

    /** Sortiert, ohne Doppelte; zu wenige → Vorgaben auffüllen, zu viele → die ersten vier. */
    fun normalizePresets(list: List<LocalTime>): List<LocalTime> {
        val clean = list.map { it.withSecond(0).withNano(0) }.distinct().sorted().take(MAX_PRESETS)
        if (clean.size >= MIN_PRESETS) return clean
        return (clean + QuickTimes.PRESETS).distinct().sorted().take(MIN_PRESETS)
    }

    /** „10 Min“, „1 Std“, „1 Std 30 Min“. */
    fun durationLabel(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "$m Min"
            m == 0 -> "$h Std"
            else -> "$h Std $m Min"
        }
    }

    /** Zahl aus einem Eingabefeld, nur innerhalb [range], sonst null. */
    fun parse(text: String, range: IntRange): Int? = text.trim().toIntOrNull()?.takeIf { it in range }

    val SNOOZE_RANGE = 1..1440
    val NAG_RANGE = 0..1440
    val RADIUS_RANGE = 30..5000
    val INTERVAL_RANGE = GeoLogic.MIN_INTERVAL_MIN..240
}

/** Speichert die Einstellungen als JSON in SharedPreferences (wie [ReminderStore]). */
object Settings {
    private const val KEY = "settings"

    /** Zähler, der sich bei jeder Änderung erhöht – die Oberfläche lädt dann neu. */
    val version = MutableStateFlow(0)

    fun get(ctx: Context): AppSettings {
        val raw = ctx.applicationContext.getSharedPreferences(KEY, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return AppSettings()
        return runCatching { JSONObject(raw).toSettings() }.getOrDefault(AppSettings())
    }

    @SuppressLint("ApplySharedPref")
    fun save(ctx: Context, s: AppSettings) {
        ctx.applicationContext.getSharedPreferences(KEY, Context.MODE_PRIVATE).edit()
            .putString(KEY, s.toJson().toString()).commit()
        version.value = version.value + 1
    }

    fun update(ctx: Context, change: (AppSettings) -> AppSettings) = save(ctx, change(get(ctx)))

    @SuppressLint("ApplySharedPref")
    fun reset(ctx: Context) {
        ctx.applicationContext.getSharedPreferences(KEY, Context.MODE_PRIVATE).edit().remove(KEY).commit()
        version.value = version.value + 1
    }
}

private fun AppSettings.toJson() = JSONObject().apply {
    put("presets", JSONArray(presets.map { it.hour * 60 + it.minute }))
    put("snoozeMinutes", JSONArray(snoozeMinutes))
    put("nagIntervalMin", nagIntervalMin)
    put("weekendTimeMin", weekendTimeMin)
    put("placeRadiusM", placeRadiusM)
    put("placeIntervalMin", placeIntervalMin)
    put("placeWindowStartMin", placeWindowStartMin)
    put("placeWindowEndMin", placeWindowEndMin)
    put("autoAdvance", autoAdvance)
    put("showOthers", showOthers)
    put("startTab", startTab.name)
}

private fun JSONObject.ints(key: String): List<Int>? =
    optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getInt(it) } }

private fun JSONObject.toSettings(): AppSettings {
    val d = AppSettings()
    return AppSettings(
        presets = ints("presets")?.map { LocalTime.of(it / 60 % 24, it % 60) }
            ?.let(SettingsRules::normalizePresets) ?: d.presets,
        snoozeMinutes = ints("snoozeMinutes")?.takeIf { it.size == 2 } ?: d.snoozeMinutes,
        nagIntervalMin = optInt("nagIntervalMin", d.nagIntervalMin),
        weekendTimeMin = optInt("weekendTimeMin", d.weekendTimeMin),
        placeRadiusM = optInt("placeRadiusM", d.placeRadiusM),
        placeIntervalMin = optInt("placeIntervalMin", d.placeIntervalMin),
        placeWindowStartMin = optInt("placeWindowStartMin", d.placeWindowStartMin),
        placeWindowEndMin = optInt("placeWindowEndMin", d.placeWindowEndMin),
        autoAdvance = optBoolean("autoAdvance", d.autoAdvance),
        showOthers = optBoolean("showOthers", d.showOthers),
        startTab = runCatching { StartTab.valueOf(optString("startTab")) }.getOrDefault(d.startTab),
    )
}

/** Aktuelle Einstellungen in Compose; lädt bei jeder Änderung neu. */
@Composable
fun rememberSettings(): AppSettings {
    val ctx = LocalContext.current
    val v by Settings.version.collectAsState()
    return remember(v) { Settings.get(ctx) }
}

// ---------------------------------------------------------------- Bildschirm

private fun timeText(t: LocalTime): String = t.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

private fun minText(min: Int): String = timeText(LocalTime.of(min / 60 % 24, min % 60))

private fun pickTime(ctx: Context, start: LocalTime, onPicked: (LocalTime) -> Unit) {
    TimePickerDialog(
        ctx, { _, h, m -> onPicked(LocalTime.of(h, m)) },
        start.hour, start.minute, DateFormat.is24HourFormat(ctx),
    ).show()
}

/** Einstellungen; ganz oben der Weg zur Prüfliste „Zuverlässigkeit“. */
@Composable
fun SettingsScreen(
    reliable: Boolean,
    onReliability: () -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    val ctx = LocalContext.current
    val s = rememberSettings()
    var confirmReset by remember { mutableStateOf(false) }
    fun change(block: (AppSettings) -> AppSettings) = Settings.update(ctx, block)

    Column(Modifier.padding(contentPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
            }
            Text("Einstellungen", style = MaterialTheme.typography.headlineSmall)
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    onClick = onReliability,
                    colors = if (reliable) CardDefaults.cardColors()
                    else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Zuverlässigkeit", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (reliable) "✓ Alles eingerichtet. Prüfliste und Test-Erinnerung."
                            else "Erinnerungen könnten blockiert werden – tippen zum Prüfen.",
                        )
                    }
                }
            }

            item {
                SettingsCard("Feste Uhrzeiten") {
                    SettingsHint(
                        "Schnellwahl im Editor, beim Verschieben und in der Schnelleingabe " +
                            "(heute, falls noch nicht vorbei, sonst morgen). 2 bis 4 Uhrzeiten.",
                    )
                    // Je zwei pro Reihe wie die Knöpfe beim Verschieben; vier passen nicht nebeneinander.
                    s.presets.withIndex().chunked(2).forEach { pair -> Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        pair.forEach { (i, t) ->
                            InputChip(
                                selected = false,
                                onClick = {
                                    pickTime(ctx, t) { new ->
                                        change {
                                            it.copy(
                                                presets = SettingsRules.normalizePresets(
                                                    it.presets.toMutableList().also { l -> l[i] = new },
                                                ),
                                            )
                                        }
                                    }
                                },
                                label = { Text(timeText(t)) },
                                trailingIcon = if (s.presets.size > SettingsRules.MIN_PRESETS) {
                                    {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Entfernen",
                                            modifier = Modifier.clickable {
                                                change { it.copy(presets = SettingsRules.normalizePresets(it.presets - t)) }
                                            },
                                        )
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                    } }
                    if (s.presets.size < SettingsRules.MAX_PRESETS) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            OutlinedButton(onClick = {
                                pickTime(ctx, LocalTime.of(12, 0)) { new ->
                                    change { it.copy(presets = SettingsRules.normalizePresets(it.presets + new)) }
                                }
                            }) { Text("Uhrzeit hinzufügen") }
                        }
                    }
                }
            }

            item {
                SettingsCard("Verschieben um") {
                    SettingsHint("Die beiden Dauern beim Verschieben, in der Schnelleingabe und auf den Karten.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        s.snoozeMinutes.forEachIndexed { i, m ->
                            NumberField(
                                "Dauer ${i + 1} (Minuten)", m, SettingsRules.SNOOZE_RANGE,
                                Modifier.weight(1f),
                            ) { v ->
                                change { it.copy(snoozeMinutes = it.snoozeMinutes.toMutableList().also { l -> l[i] = v }) }
                            }
                        }
                    }
                }
            }

            item {
                SettingsCard("Vorgaben für neue Erinnerungen") {
                    NumberField(
                        "Erinnern alle … Minuten (0 = einmalig)", s.nagIntervalMin, SettingsRules.NAG_RANGE,
                        Modifier.fillMaxWidth(),
                    ) { v -> change { it.copy(nagIntervalMin = v) } }
                    SettingRow("Wochenend-Uhrzeit") {
                        OutlinedButton(onClick = {
                            pickTime(ctx, LocalTime.of(s.weekendTimeMin / 60, s.weekendTimeMin % 60)) { t ->
                                change { it.copy(weekendTimeMin = t.hour * 60 + t.minute) }
                            }
                        }) { Text(minText(s.weekendTimeMin)) }
                    }
                    SectionLabel("Orte")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField("Radius (m)", s.placeRadiusM, SettingsRules.RADIUS_RANGE, Modifier.weight(1f)) { v ->
                            change { it.copy(placeRadiusM = v) }
                        }
                        NumberField(
                            "Prüfen alle … Min", s.placeIntervalMin, SettingsRules.INTERVAL_RANGE, Modifier.weight(1f),
                        ) { v -> change { it.copy(placeIntervalMin = v) } }
                    }
                    SettingRow("Zeitfenster") {
                        OutlinedButton(onClick = {
                            pickTime(ctx, LocalTime.of(s.placeWindowStartMin / 60, s.placeWindowStartMin % 60)) { t ->
                                change { it.copy(placeWindowStartMin = t.hour * 60 + t.minute) }
                            }
                        }) { Text(minText(s.placeWindowStartMin)) }
                        Text(" – ")
                        OutlinedButton(onClick = {
                            pickTime(ctx, LocalTime.of(s.placeWindowEndMin / 60, s.placeWindowEndMin % 60)) { t ->
                                change { it.copy(placeWindowEndMin = t.hour * 60 + t.minute) }
                            }
                        }) { Text(minText(s.placeWindowEndMin)) }
                    }
                }
            }

            item {
                SettingsCard("Verschieben-Bildschirm") {
                    SwitchRow("Danach die nächste offene zeigen", s.autoAdvance) { v ->
                        change { it.copy(autoAdvance = v) }
                    }
                    SwitchRow("Weitere offene unten auflisten", s.showOthers) { v ->
                        change { it.copy(showOthers = v) }
                    }
                }
            }

            item {
                SettingsCard("Start-Tab beim Öffnen") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        StartTab.entries.forEach { t ->
                            FilterChip(
                                selected = s.startTab == t,
                                onClick = { change { it.copy(startTab = t) } },
                                label = { Text(t.label) },
                            )
                        }
                    }
                }
            }

            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = { confirmReset = true }) { Text("Auf Standard zurücksetzen") }
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Einstellungen zurücksetzen?") },
            text = {
                Text(
                    "Alle Werte auf dieser Seite gehen auf die Vorgaben zurück. Bestehende " +
                        "Erinnerungen bleiben unverändert.",
                )
            },
            confirmButton = {
                TextButton(onClick = { Settings.reset(ctx); confirmReset = false }) { Text("Zurücksetzen") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SettingsHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        content()
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(label) { Switch(checked = checked, onCheckedChange = onChange) }
}

/** Zahlenfeld: gespeichert wird bei jeder gültigen Eingabe, ungültige werden rot markiert. */
@Composable
private fun NumberField(
    label: String,
    value: Int,
    range: IntRange,
    modifier: Modifier,
    onValid: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val valid = SettingsRules.parse(text, range) != null
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t.filter(Char::isDigit).take(5)
            SettingsRules.parse(text, range)?.let { if (it != value) onValid(it) }
        },
        label = { Text(label) },
        isError = !valid,
        supportingText = if (valid) null else {
            { Text("${range.first}–${range.last}") }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier,
    )
}
