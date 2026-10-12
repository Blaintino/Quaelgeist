package de.quaelgeist

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

// ---------------------------------------------------------------- Theme

@Composable
fun NagTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(ctx)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

// ---------------------------------------------------------------- Helfer

private val zone: ZoneId get() = ZoneId.systemDefault()

private fun fmt(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(zone).format(
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(Locale.getDefault()),
    )

internal fun defaultAt(): Long =
    ZonedDateTime.now(zone).plusHours(1).withMinute(0).withSecond(0).withNano(0)
        .toInstant().toEpochMilli()

private val DAY_NAMES = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

private fun typeLabel(t: RepeatType) = when (t) {
    RepeatType.NONE -> "Einmalig"
    RepeatType.DAILY -> "Täglich"
    RepeatType.WEEKLY -> "Wöchentlich"
    RepeatType.MONTHLY_DAY -> "Monatlich (am Datum)"
    RepeatType.MONTHLY_NTH_WEEKDAY -> "Monatlich (z. B. 3. Mittwoch)"
}

private fun unitLabel(t: RepeatType) = when (t) {
    RepeatType.DAILY -> "Tage"
    RepeatType.WEEKLY -> "Wochen"
    else -> "Monate"
}

private fun repeatLabel(r: Reminder): String {
    val n = r.repeatEvery
    return when (r.repeatType) {
        RepeatType.NONE -> ""
        RepeatType.DAILY -> if (n == 1) "täglich" else "alle $n Tage"
        RepeatType.WEEKLY -> {
            val base = if (n == 1) "wöchentlich" else "alle $n Wochen"
            val days = DAY_NAMES.filterIndexed { i, _ -> (r.weekdayMask and (1 shl i)) != 0 }
            if (days.isEmpty()) base else "$base (${days.joinToString(", ")})"
        }
        RepeatType.MONTHLY_DAY -> if (n == 1) "monatlich" else "alle $n Monate"
        RepeatType.MONTHLY_NTH_WEEKDAY -> {
            val d = Instant.ofEpochMilli(r.anchorAt).atZone(zone)
            val nth = Recurrence.nthOf(d.toLocalDate())
            val dow: DayOfWeek = d.dayOfWeek
            val name = dow.getDisplayName(TextStyle.FULL, Locale.getDefault())
            val which = if (nth >= 5) "letzten" else "$nth."
            if (n == 1) "jeden $which $name im Monat" else "alle $n Monate: $which $name"
        }
    }
}

private fun nagLabel(r: Reminder): String = when {
    r.nagIntervalMin <= 0 -> "keine Wiederholung"
    r.maxNags == 0 -> "erinnert alle ${r.nagIntervalMin} Min, bis erledigt"
    else -> "erinnert alle ${r.nagIntervalMin} Min (max. ${r.maxNags}×)"
}

// ---------------------------------------------------------------- Hauptansicht

@Composable
fun App() {
    val ctx = LocalContext.current
    val store = remember { ReminderStore(ctx) }
    val version by ReminderStore.version.collectAsState()
    val all = remember(version) { store.all() }
    val open = remember(all) { all.filter { !it.done }.sortedBy { it.fireAt } }
    // Neueste zuerst; alte Einträge ohne Erledigt-Zeitpunkt nach Termin.
    val archive = remember(all) {
        all.filter { it.done }.sortedWith(
            compareByDescending<Reminder> { it.doneAt }.thenByDescending { it.baseAt },
        )
    }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Reminder?>(null) }
    var reopening by remember { mutableStateOf<Reminder?>(null) }
    var creatingTitle by remember { mutableStateOf<String?>(null) } // null = kein Dialog
    var confirmClear by remember { mutableStateOf(false) }
    var showReliability by remember { mutableStateOf(false) }
    val tick by MainActivity.resumeTick.collectAsState()
    val reliable = remember(tick, showReliability) { Reliability.allOk(ctx) }

    // „Mehr Optionen" aus dem Schnelleingabe-Widget: Editor mit dem getippten Titel öffnen.
    val newTitleRequest by MainActivity.newTitleRequest.collectAsState()
    LaunchedEffect(newTitleRequest) {
        newTitleRequest?.let {
            showReliability = false
            tab = 0
            creatingTitle = it
            MainActivity.newTitleRequest.value = null
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    fun save(r: Reminder) {
        Notifier.cancel(ctx, r.id)
        store.upsert(r)
        Scheduler.schedule(ctx, r)
    }

    if (showReliability) {
        BackHandler { showReliability = false }
        Scaffold { padding ->
            ReliabilityScreen(tick, onBack = { showReliability = false }, contentPadding = padding)
        }
        return
    }

    Scaffold(
        floatingActionButton = {
            if (tab == 0) {
                FloatingActionButton(onClick = { creatingTitle = "" }) {
                    Icon(Icons.Default.Add, contentDescription = "Neue Erinnerung")
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 4.dp),
            ) {
                Text(
                    "Quälgeist",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showReliability = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Zuverlässigkeit")
                }
            }
            if (!reliable) {
                Card(
                    onClick = { showReliability = true },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Erinnerungen könnten blockiert werden",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text("Tippen, um die Einstellungen zu prüfen.")
                    }
                }
            }
            PrimaryTabRow(selectedTabIndex = tab, modifier = Modifier.padding(top = 8.dp)) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("Offen (${open.size})") },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("Archiv (${archive.size})") },
                )
            }
            if (tab == 0) {
                if (open.isEmpty()) {
                    Text(
                        "Keine offenen Erinnerungen. Tippe auf +, um eine anzulegen.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(open, key = { it.id }) { r ->
                        ReminderCard(r, now, onEdit = { editing = r })
                    }
                }
            } else {
                if (archive.isEmpty()) {
                    Text(
                        "Hier landen erledigte einmalige Erinnerungen.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (archive.isNotEmpty()) {
                        item {
                            OutlinedButton(onClick = { confirmClear = true }) {
                                Text("Archiv leeren")
                            }
                        }
                    }
                    items(archive, key = { it.id }) { r ->
                        ArchiveCard(r, onReopen = { reopening = r })
                    }
                }
            }
        }
    }

    creatingTitle?.let { prefill ->
        EditDialog(
            initial = null,
            prefillTitle = prefill,
            onDismiss = { creatingTitle = null },
            onSave = { save(it); creatingTitle = null },
        )
    }
    editing?.let { current ->
        EditDialog(
            initial = current,
            onDismiss = { editing = null },
            onSave = { save(it); editing = null },
        )
    }
    reopening?.let { archived ->
        // Wieder öffnen = bearbeiten mit neuem Termin; Speichern macht sie wieder offen.
        val at = defaultAt()
        EditDialog(
            initial = archived.copy(baseAt = at, fireAt = at, anchorAt = at, done = false),
            heading = "Wieder öffnen",
            onDismiss = { reopening = null },
            onSave = { save(it); reopening = null; tab = 0 },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Archiv leeren?") },
            text = { Text("Alle ${archive.size} erledigten Erinnerungen werden endgültig gelöscht.") },
            confirmButton = {
                TextButton(onClick = { Actions.clearArchive(ctx); confirmClear = false }) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Abbrechen") }
            },
        )
    }
}

@Composable
private fun ArchiveCard(r: Reminder, onReopen: () -> Unit) {
    val ctx = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
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
                if (r.doneAt > 0) "Erledigt am ${fmt(r.doneAt)}" else "Erledigt",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "War fällig: ${fmt(r.baseAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onReopen, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text("Wieder öffnen")
            }
        }
    }
}

@Composable
private fun ReminderCard(r: Reminder, now: Long, onEdit: () -> Unit) {
    val ctx = LocalContext.current
    val overdue = !r.done && r.fireAt <= now
    Card(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    r.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (r.done) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { Actions.delete(ctx, r.id) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Löschen")
                }
            }
            if (r.done) {
                Text("Erledigt", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    "Fällig: ${fmt(r.baseAt)}" + if (overdue) " – überfällig" else "",
                    color = if (overdue) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                )
                if (r.fireAt != r.baseAt) {
                    Text(
                        "Nächste Meldung: ${fmt(r.fireAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val extra = listOf(repeatLabel(r), nagLabel(r)).filter { it.isNotEmpty() }
                    .joinToString(" · ")
                Text(
                    extra,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth()) {
                    QuickButton("Erledigt", Modifier.weight(1f)) { Actions.markDone(ctx, r.id) }
                    QuickButton("+10 Min", Modifier.weight(1f)) { Actions.snooze(ctx, r.id, 10) }
                    QuickButton("+1 Std", Modifier.weight(1f)) { Actions.snooze(ctx, r.id, 60) }
                    QuickButton("Morgen", Modifier.weight(1f)) { Actions.snoozeTomorrow(ctx, r.id) }
                }
            }
        }
    }
}

@Composable
private fun QuickButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        Text(label, fontSize = 13.sp, maxLines = 1)
    }
}

// ---------------------------------------------------------------- Bearbeiten

@Composable
private fun EditDialog(
    initial: Reminder?,
    onDismiss: () -> Unit,
    onSave: (Reminder) -> Unit,
    prefillTitle: String = "",
    heading: String? = null,
) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(initial?.title ?: prefillTitle) }
    var at by remember { mutableStateOf(initial?.baseAt ?: defaultAt()) }
    var type by remember { mutableStateOf(initial?.repeatType ?: RepeatType.NONE) }
    var every by remember { mutableStateOf((initial?.repeatEvery ?: 1).toString()) }
    var mask by remember { mutableStateOf(initial?.weekdayMask ?: 0) }
    var nag by remember { mutableStateOf((initial?.nagIntervalMin ?: 10).toString()) }
    var maxNags by remember { mutableStateOf((initial?.maxNags ?: 0).toString()) }
    var menu by remember { mutableStateOf(false) }

    val dt = Instant.ofEpochMilli(at).atZone(zone)
    val number = KeyboardOptions(keyboardType = KeyboardType.Number)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(heading ?: if (initial == null) "Neue Erinnerung" else "Erinnerung bearbeiten")
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titel") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        DatePickerDialog(
                            ctx,
                            { _, y, m, d ->
                                at = ZonedDateTime.of(y, m + 1, d, dt.hour, dt.minute, 0, 0, zone)
                                    .toInstant().toEpochMilli()
                            },
                            dt.year, dt.monthValue - 1, dt.dayOfMonth,
                        ).show()
                    }) {
                        Text(dt.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
                    }
                    OutlinedButton(onClick = {
                        TimePickerDialog(
                            ctx,
                            { _, h, min ->
                                at = ZonedDateTime
                                    .of(dt.year, dt.monthValue, dt.dayOfMonth, h, min, 0, 0, zone)
                                    .toInstant().toEpochMilli()
                            },
                            dt.hour, dt.minute, DateFormat.is24HourFormat(ctx),
                        ).show()
                    }) {
                        Text(dt.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)))
                    }
                }

                Text("Wiederholung", style = MaterialTheme.typography.labelLarge)
                Box {
                    OutlinedButton(onClick = { menu = true }) { Text(typeLabel(type)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        RepeatType.entries.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(typeLabel(t)) },
                                onClick = { type = t; menu = false },
                            )
                        }
                    }
                }
                if (type != RepeatType.NONE) {
                    OutlinedTextField(
                        value = every,
                        onValueChange = { every = it.filter(Char::isDigit).take(2) },
                        label = { Text("Alle … ${unitLabel(type)}") },
                        keyboardOptions = number,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (type == RepeatType.WEEKLY) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        DAY_NAMES.forEachIndexed { i, name ->
                            DayToggle(name, (mask and (1 shl i)) != 0) { mask = mask xor (1 shl i) }
                        }
                    }
                    Text(
                        "Ohne Auswahl gilt der Wochentag des Termins.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

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
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    // Termin unverändert und gleiche Regel: ursprünglichen Serienstart behalten,
                    // damit z. B. „am 31." nach einem 30er-Monat nicht auf den 30. rutscht.
                    val keepAnchor = initial != null && at == initial.baseAt &&
                        type == initial.repeatType
                    onSave(
                        Reminder(
                            id = initial?.id ?: ReminderStore.newId(),
                            title = title.trim(),
                            baseAt = at,
                            fireAt = at,
                            anchorAt = initial?.takeIf { keepAnchor }?.anchorAt ?: at,
                            repeatType = type,
                            repeatEvery = (every.toIntOrNull() ?: 1).coerceAtLeast(1),
                            weekdayMask = if (type == RepeatType.WEEKLY) mask else 0,
                            nagIntervalMin = nag.toIntOrNull() ?: 0,
                            maxNags = maxNags.toIntOrNull() ?: 0,
                        ).let(Recurrence::alignStart),
                    )
                },
            ) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun DayToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (selected) colors.primary else colors.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        )
    }
}
