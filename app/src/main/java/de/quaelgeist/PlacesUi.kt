package de.quaelgeist

import android.Manifest
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// ---------------------------------------------------------------- Helfer

private const val GOOGLE_MAPS = "com.google.android.apps.maps"

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

private fun coords(lat: Double, lon: Double) = String.format(Locale.ROOT, "%.6f, %.6f", lat, lon)

private fun timeLabel(min: Int): String =
    LocalTime.of(min / 60 % 24, min % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

private fun triggerLabel(t: PlaceTrigger, place: String) = when (t) {
    PlaceTrigger.ARRIVE -> "Beim Ankommen: $place"
    PlaceTrigger.LEAVE -> "Beim Verlassen: $place"
}

private fun windowLabel(r: Reminder): String? =
    if (r.windowStartMin < 0 || r.windowEndMin < 0) null
    else "nur ${timeLabel(r.windowStartMin)}–${timeLabel(r.windowEndMin)}"

/**
 * Google Maps (sonst eine andere Karten-App) öffnen: mit Ort an dieser Stelle, ohne Ort einfach
 * so. Dort lange auf den Punkt drücken und Koordinaten oder Plus Code kopieren.
 */
private fun openMaps(ctx: Context, lat: Double?, lon: Double?) {
    val geo = if (lat != null && lon != null) {
        Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon"))
    } else {
        null
    }
    val attempts = listOfNotNull(
        geo?.let { Intent(it).setPackage(GOOGLE_MAPS) },
        if (geo == null) ctx.packageManager.getLaunchIntentForPackage(GOOGLE_MAPS) else null,
        geo ?: Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?z=14")),
    )
    for (i in attempts) {
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            // Nächsten Versuch.
        }
    }
    Toast.makeText(ctx, "Keine Karten-App gefunden", Toast.LENGTH_LONG).show()
}

// ---------------------------------------------------------------- Tab „Orte“

@Composable
fun PlacesTab(list: List<Reminder>, onEdit: (Reminder) -> Unit, onManagePlaces: () -> Unit) {
    val ctx = LocalContext.current
    val placesVersion by PlaceStore.version.collectAsState()
    val places = remember(placesVersion) { PlaceStore(ctx).all().associateBy { it.id } }
    // Ausgelöste (nerven gerade) oben, wartende nach Ort.
    val triggered = list.filter { !it.placeArmed }.sortedBy { it.fireAt }
    val waiting = list.filter { it.placeArmed }
        .sortedWith(compareBy({ places[it.placeId]?.name.orEmpty() }, { it.title }))

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedButton(onClick = onManagePlaces) {
                Text("Orte verwalten (${places.size})")
            }
        }
        if (list.isEmpty()) {
            item {
                Text(
                    "Keine Orts-Erinnerungen, z. B. „Toilettenpapier kaufen“ beim Verlassen der " +
                        "Arbeit. Tippe auf +, um eine anzulegen.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (triggered.isNotEmpty()) {
            item(key = "triggered") {
                Text(
                    "Ausgelöst",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            items(triggered, key = { it.id }) { r -> PlaceReminderCard(r, places[r.placeId], onEdit) }
        }
        if (waiting.isNotEmpty()) {
            item(key = "waiting") {
                Text(
                    "Wartet auf den Ort",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(waiting, key = { it.id }) { r -> PlaceReminderCard(r, places[r.placeId], onEdit) }
        }
    }
}

@Composable
private fun PlaceReminderCard(r: Reminder, place: Place?, onEdit: (Reminder) -> Unit) {
    val ctx = LocalContext.current
    Card(onClick = { onEdit(r) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    r.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { Actions.delete(ctx, r.id) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Löschen")
                }
            }
            Text(
                triggerLabel(r.placeTrigger, place?.name ?: "Ort gelöscht"),
                color = if (r.placeArmed) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.error,
            )
            val extra = if (r.placeArmed) {
                listOfNotNull(windowLabel(r), "prüft alle ${GeoLogic.intervalMin(r)} Min")
            } else {
                listOf("ausgelöst ${fmt(r.baseAt)}", nagLabel(r))
            }
            Text(
                extra.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------- Editor Orts-Erinnerung

@Composable
fun PlaceReminderDialog(
    initial: Reminder?,
    onDismiss: () -> Unit,
    onSave: (Reminder) -> Unit,
    heading: String? = null,
) {
    val ctx = LocalContext.current
    val placesVersion by PlaceStore.version.collectAsState()
    val places = remember(placesVersion) { PlaceStore(ctx).all().sortedBy { it.name.lowercase() } }
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var link by remember { mutableStateOf(initial?.link.orEmpty()) }
    var placeId by remember { mutableLongStateOf(initial?.placeId ?: places.firstOrNull()?.id ?: 0L) }
    var trigger by remember { mutableStateOf(initial?.placeTrigger ?: PlaceTrigger.ARRIVE) }
    var windowOn by remember { mutableStateOf((initial?.windowStartMin ?: -1) >= 0) }
    var windowStart by remember { mutableIntStateOf(initial?.windowStartMin?.takeIf { it >= 0 } ?: (8 * 60)) }
    var windowEnd by remember { mutableIntStateOf(initial?.windowEndMin?.takeIf { it >= 0 } ?: (20 * 60)) }
    var interval by remember {
        mutableStateOf((initial?.checkIntervalMin ?: GeoLogic.DEFAULT_INTERVAL_MIN).toString())
    }
    var nag by remember { mutableStateOf((initial?.nagIntervalMin ?: 10).toString()) }
    var maxNags by remember { mutableStateOf((initial?.maxNags ?: 0).toString()) }
    var menu by remember { mutableStateOf(false) }
    var newPlace by remember { mutableStateOf(false) }

    val linkValid = Links.normalize(link) != null
    val place = places.firstOrNull { it.id == placeId }
    val canSave = title.isNotBlank() && linkValid && place != null
    val number = KeyboardOptions(keyboardType = KeyboardType.Number)

    // Ohne Standortrecht keine Erkennung: beim Speichern gleich danach fragen.
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { MainActivity.resumeTick.value = MainActivity.resumeTick.value + 1 }

    fun save() {
        if (!canSave || place == null) return
        val (cleanTitle, rawLink) = Links.split(title, link)
        val now = System.currentTimeMillis()
        // Nur Text/Nagging geändert: Zustand (wartend oder ausgelöst) behalten.
        val keep = initial != null && initial.placeId == place.id && initial.placeTrigger == trigger
        val base = Reminder(
            id = initial?.id ?: ReminderStore.newId(),
            title = cleanTitle,
            baseAt = now,
            fireAt = now,
            link = Links.normalize(rawLink) ?: "",
            nagIntervalMin = nag.toIntOrNull() ?: 0,
            maxNags = maxNags.toIntOrNull() ?: 0,
            placeId = place.id,
            placeTrigger = trigger,
            placeArmed = true,
            windowStartMin = if (windowOn) windowStart else -1,
            windowEndMin = if (windowOn) windowEnd else -1,
            checkIntervalMin = (interval.toIntOrNull() ?: GeoLogic.DEFAULT_INTERVAL_MIN)
                .coerceAtLeast(GeoLogic.MIN_INTERVAL_MIN),
        )
        val r = if (keep && initial != null) {
            base.copy(
                baseAt = initial.baseAt, fireAt = initial.fireAt, anchorAt = initial.anchorAt,
                nagCount = initial.nagCount, placeArmed = initial.placeArmed,
            )
        } else {
            base
        }
        if (!GeoWatch.hasFineLocation(ctx)) permission.launch(LOCATION_PERMISSIONS)
        onSave(r)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.padding(bottom = 16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp),
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Abbrechen")
                    }
                    Text(
                        heading ?: if (initial == null) "Neue Orts-Erinnerung" else "Orts-Erinnerung",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(enabled = canSave, onClick = { save() }) { Text("Speichern") }
                }
                Column(
                    Modifier
                        .padding(horizontal = 24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Titel") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { save() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it },
                        label = { Text("Link (optional)") },
                        singleLine = true,
                        isError = !linkValid,
                        supportingText = if (linkValid) null else {
                            { Text("Nur http(s)-Links") }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text("Ort", style = MaterialTheme.typography.labelLarge)
                    if (places.isEmpty()) {
                        Text(
                            "Noch keine Orte angelegt.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { newPlace = true }) { Text("Ort anlegen") }
                    } else {
                        Box {
                            OutlinedButton(onClick = { menu = true }) {
                                Text(place?.name ?: "Ort wählen")
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                places.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(p.name) },
                                        onClick = { placeId = p.id; menu = false },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Neuer Ort…") },
                                    onClick = { menu = false; newPlace = true },
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = trigger == PlaceTrigger.ARRIVE,
                            onClick = { trigger = PlaceTrigger.ARRIVE },
                            label = { Text("Beim Ankommen") },
                        )
                        FilterChip(
                            selected = trigger == PlaceTrigger.LEAVE,
                            onClick = { trigger = PlaceTrigger.LEAVE },
                            label = { Text("Beim Verlassen") },
                        )
                    }

                    Text("Zeitfenster", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !windowOn, onClick = { windowOn = false }, label = { Text("Immer") })
                        FilterChip(selected = windowOn, onClick = { windowOn = true }, label = { Text("Nur von–bis") })
                    }
                    if (windowOn) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(onClick = {
                                TimePickerDialog(
                                    ctx, { _, h, m -> windowStart = h * 60 + m },
                                    windowStart / 60, windowStart % 60, DateFormat.is24HourFormat(ctx),
                                ).show()
                            }) { Text(timeLabel(windowStart)) }
                            Text("bis")
                            OutlinedButton(onClick = {
                                TimePickerDialog(
                                    ctx, { _, h, m -> windowEnd = h * 60 + m },
                                    windowEnd / 60, windowEnd % 60, DateFormat.is24HourFormat(ctx),
                                ).show()
                            }) { Text(timeLabel(windowEnd)) }
                        }
                        Text(
                            "Außerhalb des Fensters wird nicht geprüft und nicht erinnert.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedTextField(
                        value = interval,
                        onValueChange = { interval = it.filter(Char::isDigit).take(3) },
                        label = { Text("Standort prüfen alle … Minuten (mind. ${GeoLogic.MIN_INTERVAL_MIN})") },
                        supportingText = { Text("Kürzer = schneller erkannt, braucht mehr Akku.") },
                        keyboardOptions = number,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text("Nagging", style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = nag,
                        onValueChange = { nag = it.filter(Char::isDigit).take(4) },
                        label = { Text("Alle … Minuten erinnern (0 = einmalig)") },
                        keyboardOptions = number,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = maxNags,
                        onValueChange = { maxNags = it.filter(Char::isDigit).take(3) },
                        label = { Text("Max. Wiederholungen (0 = unbegrenzt)") },
                        keyboardOptions = number,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (newPlace) {
        PlaceDialog(
            initial = null,
            onDismiss = { newPlace = false },
            onSaved = { placeId = it.id; newPlace = false },
        )
    }
}

// ---------------------------------------------------------------- Orte verwalten

@Composable
fun PlacesScreen(
    sharedText: String?,
    onSharedHandled: () -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    val ctx = LocalContext.current
    val placesVersion by PlaceStore.version.collectAsState()
    val logVersion by GeoLog.version.collectAsState()
    val reminderVersion by ReminderStore.version.collectAsState()
    val store = remember { PlaceStore(ctx) }
    val places = remember(placesVersion) { store.all().sortedBy { it.name.lowercase() } }
    val watched = remember(placesVersion) { store.watched }
    val log = remember(logVersion) { GeoLog.read(ctx) }
    val inUse = remember(reminderVersion) {
        ReminderStore(ctx).all().filter { it.isPlace && !it.done }.groupingBy { it.placeId }.eachCount()
    }
    var editing by remember { mutableStateOf<Place?>(null) }
    var creating by remember { mutableStateOf(false) }
    var prefill by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sharedText) {
        if (sharedText != null) {
            prefill = sharedText
            creating = true
            onSharedHandled()
        }
    }

    Column(Modifier.padding(contentPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
            }
            Text("Orte", style = MaterialTheme.typography.headlineSmall)
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Button(onClick = { prefill = null; creating = true }) { Text("Ort hinzufügen") }
            }
            if (places.isEmpty()) {
                item {
                    Text(
                        "Ein Ort lässt sich vor Ort mit „Aktueller Standort“ anlegen oder von " +
                            "unterwegs: in Google Maps lange auf den Punkt drücken, Koordinaten " +
                            "oder Plus Code antippen (kopiert) und hier einfügen. Teilen aus einer " +
                            "Karten-App („Als Ort in Quälgeist“) geht auch.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(places, key = { it.id }) { p ->
                Card(onClick = { editing = p }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${coords(p.lat, p.lon)} · ${p.radiusM} m",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (p.ssids.isNotEmpty()) {
                            Text("WLAN: " + p.ssids.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                        }
                        val n = inUse[p.id] ?: 0
                        val status = listOfNotNull(
                            if (n > 0) "$n Erinnerung(en)" else null,
                            if (p.id in watched) "überwacht: ${GeoWatch.label(store.presence(p.id))}" else null,
                        )
                        if (status.isNotEmpty()) {
                            Text(
                                status.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        "Protokoll der Ortserkennung",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (log.isNotEmpty()) {
                        TextButton(onClick = { GeoLog.clear(ctx) }) { Text("Leeren") }
                    }
                }
                if (log.isEmpty()) {
                    Text("Noch keine Einträge.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(log) { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }

    if (creating) {
        PlaceDialog(
            initial = null,
            prefillText = prefill,
            onDismiss = { creating = false },
            onSaved = { creating = false },
        )
    }
    editing?.let { p ->
        PlaceDialog(
            initial = p,
            inUse = inUse[p.id] ?: 0,
            onDismiss = { editing = null },
            onSaved = { editing = null },
        )
    }
}

// ---------------------------------------------------------------- Ort anlegen/bearbeiten

@Composable
private fun PlaceDialog(
    initial: Place?,
    onDismiss: () -> Unit,
    onSaved: (Place) -> Unit,
    prefillText: String? = null,
    inUse: Int = 0,
) {
    val ctx = LocalContext.current
    var name by remember {
        mutableStateOf(initial?.name ?: prefillText?.let(PlaceInput::suggestName).orEmpty())
    }
    var where by remember {
        mutableStateOf(initial?.let { coords(it.lat, it.lon) } ?: prefillText.orEmpty())
    }
    var radius by remember { mutableStateOf((initial?.radiusM ?: GeoLogic.DEFAULT_RADIUS_M).toString()) }
    var ssids by remember { mutableStateOf(initial?.ssids.orEmpty()) }
    var newSsid by remember { mutableStateOf("") }
    var locating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Bezugspunkt für kurze Plus Codes: letzter bekannter Standort, sonst ein gespeicherter Ort.
    val reference = remember {
        GeoWatch.anyLastLocation(ctx)?.let { it.latitude to it.longitude }
            ?: (initial ?: PlaceStore(ctx).all().firstOrNull())?.let { it.lat to it.lon }
    }
    val parsed = remember(where, reference) {
        PlaceInput.parse(where, reference?.first, reference?.second)
    }
    val found = parsed as? PlaceInput.Result.Found
    val radiusM = radius.toIntOrNull()?.takeIf { it in 30..5000 }
    val canSave = name.isNotBlank() && found != null && radiusM != null

    fun takeCurrent() {
        locating = true
        GeoWatch.currentLocation(ctx) { loc ->
            locating = false
            if (loc == null) {
                Toast.makeText(ctx, "Kein Standort – Standortdienst an?", Toast.LENGTH_LONG).show()
            } else {
                where = coords(loc.latitude, loc.longitude)
                Toast.makeText(ctx, "Übernommen (±${loc.accuracy.toInt()} m)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun addCurrentWifi() {
        val ssid = GeoWatch.currentSsid(ctx)
        if (ssid == null) {
            Toast.makeText(ctx, "Kein WLAN erkannt (Standortdienst an?)", Toast.LENGTH_LONG).show()
        } else if (ssids.none { it.equals(ssid, ignoreCase = true) }) {
            ssids = ssids + ssid
        }
    }

    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (GeoWatch.hasFineLocation(ctx)) pendingAction?.invoke()
        pendingAction = null
    }

    fun withLocation(action: () -> Unit) {
        if (GeoWatch.hasFineLocation(ctx)) {
            action()
        } else {
            pendingAction = action
            permission.launch(LOCATION_PERMISSIONS)
        }
    }

    fun save() {
        if (!canSave || found == null || radiusM == null) return
        val p = Place(
            id = initial?.id ?: ReminderStore.newId(),
            name = name.trim(),
            lat = found.lat,
            lon = found.lon,
            radiusM = radiusM,
            ssids = ssids,
        )
        PlaceStore(ctx).upsert(p)
        GeoWatch.refresh(ctx)
        onSaved(p)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.padding(bottom = 16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp),
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Abbrechen")
                    }
                    Text(
                        if (initial == null) "Neuer Ort" else "Ort bearbeiten",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(enabled = canSave, onClick = { save() }) { Text("Speichern") }
                }
                Column(
                    Modifier
                        .padding(horizontal = 24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name, z. B. Zuhause") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = where,
                        onValueChange = { where = it },
                        label = { Text("Koordinaten oder Plus Code") },
                        isError = where.isNotBlank() && found == null,
                        supportingText = {
                            Text(
                                when (parsed) {
                                    is PlaceInput.Result.Found ->
                                        "✓ ${coords(parsed.lat, parsed.lon)} (${parsed.source})"
                                    is PlaceInput.Result.NeedsReference ->
                                        "Kurzer Plus Code: erst einmal „Aktueller Standort“ " +
                                            "nutzen oder den vollen Code (8 Zeichen vor dem +) eingeben."
                                    is PlaceInput.Result.Error ->
                                        if (where.isBlank()) "In Google Maps lange auf den Ort drücken, " +
                                            "Koordinaten oder Plus Code antippen, hier einfügen."
                                        else parsed.message
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !locating, onClick = { withLocation(::takeCurrent) }) {
                            Text(if (locating) "Suche…" else "Aktueller Standort")
                        }
                        OutlinedButton(onClick = { openMaps(ctx, found?.lat, found?.lon) }) {
                            Text("Google Maps")
                        }
                    }
                    OutlinedTextField(
                        value = radius,
                        onValueChange = { radius = it.filter(Char::isDigit).take(4) },
                        label = { Text("Radius in Metern (30–5000)") },
                        isError = radiusM == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text("WLANs (optional)", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Verbunden mit einem davon gilt als „da“; ein Wechsel zwischen ihnen löst " +
                            "nichts aus. Hilft vor allem beim Verlassen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ssids.forEach { s ->
                        InputChip(
                            selected = false,
                            onClick = { ssids = ssids - s },
                            label = { Text(s) },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Entfernen") },
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newSsid,
                            onValueChange = { newSsid = it },
                            label = { Text("WLAN-Name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            enabled = Ssid.normalize(newSsid) != null,
                            onClick = {
                                Ssid.normalize(newSsid)?.let { s ->
                                    if (ssids.none { it.equals(s, ignoreCase = true) }) ssids = ssids + s
                                }
                                newSsid = ""
                            },
                        ) { Text("Hinzufügen") }
                    }
                    OutlinedButton(onClick = { withLocation(::addCurrentWifi) }) {
                        Text("Aktuelles WLAN hinzufügen")
                    }
                    if (initial != null) {
                        TextButton(onClick = { confirmDelete = true }) { Text("Ort löschen") }
                    }
                }
            }
        }
    }

    if (confirmDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("„${initial.name}“ löschen?") },
            text = {
                Text(
                    if (inUse > 0) "$inUse offene Erinnerung(en) nutzen diesen Ort. Bitte erst " +
                        "diese löschen oder einem anderen Ort zuordnen."
                    else "Der Ort wird entfernt.",
                )
            },
            confirmButton = {
                if (inUse == 0) {
                    TextButton(onClick = {
                        PlaceStore(ctx).delete(initial.id)
                        GeoWatch.replan(ctx)
                        confirmDelete = false
                        onDismiss()
                    }) { Text("Löschen") }
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(if (inUse > 0) "OK" else "Abbrechen")
                }
            },
        )
    }
}
