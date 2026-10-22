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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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

/** Datum + Uhrzeit aus dem Editor; schon vergangen → nächster Tag zu dieser Uhrzeit. */
private fun futureAt(date: LocalDate, time: LocalTime): Long =
    QuickTimes.onDayOrNext(date, time, ZonedDateTime.now(zone)).toInstant().toEpochMilli()

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

private const val WORKDAYS_LABEL = "Werktags (Mo–Fr)"

private fun unitLabel(t: RepeatType) = when (t) {
    RepeatType.DAILY -> "Tage"
    RepeatType.WEEKLY -> "Wochen"
    else -> "Monate"
}

private fun repeatLabel(r: Reminder): String {
    val base = ruleLabel(r)
    if (!Recurrence.weekendApplies(r)) return base
    return when (r.weekendMode) {
        WeekendMode.SKIP -> "$base · Wochenende aus"
        WeekendMode.OTHER_TIME -> "$base · Wochenende ${minutesLabel(r.weekendTimeMin)}"
        WeekendMode.NORMAL -> base
    }
}

private fun minutesLabel(min: Int): String =
    LocalTime.of(min / 60 % 24, min % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

/** Bits für Samstag und Sonntag in [Reminder.weekdayMask]. */
private const val WEEKEND_BITS = 0b1100000

private fun ruleLabel(r: Reminder): String {
    val n = r.repeatEvery
    return when (r.repeatType) {
        RepeatType.NONE -> ""
        RepeatType.DAILY -> if (n == 1) "täglich" else "alle $n Tage"
        RepeatType.WEEKLY -> {
            if (n == 1 && r.weekdayMask == Recurrence.WORKDAYS) return "werktags (Mo–Fr)"
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
    val recurring = remember(open) { open.filter { it.repeatType != RepeatType.NONE } }
    val once = remember(open) { open.filter { it.repeatType == RepeatType.NONE } }
    // Neueste zuerst; alte Einträge ohne Erledigt-Zeitpunkt nach Termin.
    val archive = remember(all) {
        all.filter { it.done }.sortedWith(
            compareByDescending<Reminder> { it.doneAt }.thenByDescending { it.baseAt },
        )
    }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var tab by rememberSaveable { mutableIntStateOf(TAB_ONCE) } // Start-Tab: Einmalig
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
            tab = TAB_ONCE
            creatingTitle = it
            MainActivity.newTitleRequest.value = null
        }
    }

    // „In der App bearbeiten" aus dem Verschieben-Bildschirm: Editor für diese Erinnerung.
    val editRequest by MainActivity.editRequest.collectAsState()
    LaunchedEffect(editRequest, all) {
        editRequest?.let { id ->
            all.firstOrNull { it.id == id && !it.done }?.let {
                showReliability = false
                tab = if (it.repeatType == RepeatType.NONE) TAB_ONCE else TAB_RECURRING
                editing = it
            }
            MainActivity.editRequest.value = null
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
        // Zum Tab wechseln, in dem die gespeicherte Erinnerung jetzt steht.
        tab = if (r.repeatType == RepeatType.NONE) TAB_ONCE else TAB_RECURRING
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
            if (tab != TAB_ARCHIVE) {
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
                TabLabel("Wiederkehrend", recurring.size, tab == TAB_RECURRING) {
                    tab = TAB_RECURRING
                }
                TabLabel("Einmalig", once.size, tab == TAB_ONCE) { tab = TAB_ONCE }
                TabLabel("Archiv", archive.size, tab == TAB_ARCHIVE) { tab = TAB_ARCHIVE }
            }
            if (tab != TAB_ARCHIVE) {
                val list = if (tab == TAB_RECURRING) recurring else once
                if (list.isEmpty()) {
                    Text(
                        if (tab == TAB_RECURRING) {
                            "Keine wiederkehrenden Erinnerungen, z. B. für Medikamente. " +
                                "Tippe auf +, um eine anzulegen."
                        } else {
                            "Keine offenen Aufgaben. Tippe auf + oder nutze das Widget."
                        },
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Abschnitte nach nächster Meldung; leere Gruppen entfallen.
                    val groups = DueGroups.group(list, Instant.ofEpochMilli(now).atZone(zone))
                    groups.forEach { (group, reminders) ->
                        item(key = "group-${group.name}") {
                            Text(
                                group.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (group == DueGroup.OVERDUE) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        items(reminders, key = { it.id }) { r ->
                            ReminderCard(r, now, onEdit = { editing = r })
                        }
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
            // Aus dem Tab „Wiederkehrend“ heraus: gleich mit täglicher Wiederholung starten.
            prefillType = if (tab == TAB_RECURRING) RepeatType.DAILY else RepeatType.NONE,
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
            onSave = { save(it); reopening = null },
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

private const val TAB_RECURRING = 0
private const val TAB_ONCE = 1
private const val TAB_ARCHIVE = 2

@Composable
private fun TabLabel(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, maxLines = 1)
                Text("$count", style = MaterialTheme.typography.labelSmall)
            }
        },
    )
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
    val overdue = DueGroups.isOverdue(r, now)
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
                // Zeitpunkt der nächsten Meldung (Termin, verschobener Termin oder nächster Nag).
                Text(
                    fmt(r.fireAt) + if (overdue) " – überfällig" else "",
                    color = if (overdue) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                )
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
    prefillType: RepeatType = RepeatType.NONE,
    heading: String? = null,
) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(initial?.title ?: prefillTitle) }
    var link by remember { mutableStateOf(initial?.link ?: "") }
    val linkValid = Links.normalize(link) != null
    val canSave = title.isNotBlank() && linkValid
    // Liegt der aktuelle Termin am Wochenende mit eigener Uhrzeit, zeigt der Editor trotzdem die
    // normale Uhrzeit (aus anchorAt) – sie ist die Hauptuhrzeit der Serie.
    var at by remember { mutableStateOf(initial?.let(::editorStart) ?: defaultAt()) }
    var type by remember { mutableStateOf(initial?.repeatType ?: prefillType) }
    var every by remember { mutableStateOf((initial?.repeatEvery ?: 1).toString()) }
    var mask by remember { mutableStateOf(initial?.weekdayMask ?: 0) }
    var nag by remember { mutableStateOf((initial?.nagIntervalMin ?: 10).toString()) }
    var maxNags by remember { mutableStateOf((initial?.maxNags ?: 0).toString()) }
    var menu by remember { mutableStateOf(false) }
    var weekendMode by remember { mutableStateOf(initial?.weekendMode ?: WeekendMode.NORMAL) }
    var weekendMin by remember {
        mutableIntStateOf(initial?.weekendTimeMin ?: Reminder.DEFAULT_WEEKEND_TIME_MIN)
    }

    val dt = Instant.ofEpochMilli(at).atZone(zone)
    // Wochenend-Modus nur, wenn die Regel überhaupt Sa/So trifft; „Aussetzen" nur bei täglich
    // (wöchentlich: Sa/So einfach abwählen).
    val effectiveMask = if (mask != 0) mask else 1 shl (dt.dayOfWeek.value - 1)
    val weekendPossible = type == RepeatType.DAILY ||
        (type == RepeatType.WEEKLY && effectiveMask and WEEKEND_BITS != 0)
    val skipPossible = type == RepeatType.DAILY
    val number = KeyboardOptions(keyboardType = KeyboardType.Number)

    fun save() {
        if (!canSave) return
        // Link im Titel wandert ins leere Linkfeld (siehe [Links.split]).
        val (cleanTitle, rawLink) = Links.split(title, link)
        // Termin unverändert und gleiche Regel: ursprünglichen Serienstart behalten,
        // damit z. B. „am 31." nach einem 30er-Monat nicht auf den 30. rutscht.
        val keepAnchor = initial != null && at == initial.baseAt &&
            type == initial.repeatType
        onSave(
            Reminder(
                id = initial?.id ?: ReminderStore.newId(),
                title = cleanTitle,
                baseAt = at,
                fireAt = at,
                anchorAt = initial?.takeIf { keepAnchor }?.anchorAt ?: at,
                repeatType = type,
                repeatEvery = (every.toIntOrNull() ?: 1).coerceAtLeast(1),
                weekdayMask = if (type == RepeatType.WEEKLY) mask else 0,
                nagIntervalMin = nag.toIntOrNull() ?: 0,
                maxNags = maxNags.toIntOrNull() ?: 0,
                weekendMode = when {
                    !weekendPossible -> WeekendMode.NORMAL
                    weekendMode == WeekendMode.SKIP && !skipPossible -> WeekendMode.NORMAL
                    else -> weekendMode
                },
                weekendTimeMin = weekendMin,
                link = Links.normalize(rawLink) ?: "",
            ).let(Recurrence::alignStart),
        )
    }

    // Kopfzeile mit Speichern oben: Die Tastatur verdeckt sonst die Knöpfe am unteren Rand.
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
                        heading ?: if (initial == null) "Neue Erinnerung" else "Erinnerung bearbeiten",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(enabled = canSave, onClick = { save() }) {
                        Text("Speichern")
                    }
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
                        // Eingabetaste = Speichern mit den übrigen Werten wie eingestellt.
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { save() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Ausgefüllt: Die Meldung bekommt „Link öffnen" (öffnet und hakt ab).
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it },
                        label = { Text("Link (optional)") },
                        placeholder = { Text("https://…") },
                        singleLine = true,
                        isError = !linkValid,
                        supportingText = if (linkValid) null else {
                            { Text("Nur http(s)-Links") }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { save() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            DatePickerDialog(
                                ctx,
                                { _, y, m, d ->
                                    at = futureAt(LocalDate.of(y, m + 1, d), dt.toLocalTime())
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
                                    at = futureAt(dt.toLocalDate(), LocalTime.of(h, min))
                                },
                                dt.hour, dt.minute, DateFormat.is24HourFormat(ctx),
                            ).show()
                        }) {
                            Text(dt.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)))
                        }
                    }
                    // Feste Uhrzeiten; wie bei der Auswahl oben rückt eine schon vergangene
                    // Uhrzeit auf den nächsten Tag.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickTimes.PRESETS.forEach { preset ->
                            FilterChip(
                                selected = dt.toLocalTime() == preset,
                                onClick = { at = futureAt(dt.toLocalDate(), preset) },
                                label = {
                                    Text(preset.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)))
                                },
                            )
                        }
                    }

                    Text("Wiederholung", style = MaterialTheme.typography.labelLarge)
                    Box {
                        // „Werktags" ist eine Voreinstellung für wöchentlich Mo–Fr, kein eigener Typ.
                        val workdays = type == RepeatType.WEEKLY && every == "1" &&
                            mask == Recurrence.WORKDAYS
                        OutlinedButton(onClick = { menu = true }) {
                            Text(if (workdays) WORKDAYS_LABEL else typeLabel(type))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            RepeatType.entries.forEach { t ->
                                DropdownMenuItem(
                                    text = { Text(typeLabel(t)) },
                                    onClick = { type = t; menu = false },
                                )
                                if (t == RepeatType.DAILY) {
                                    DropdownMenuItem(
                                        text = { Text(WORKDAYS_LABEL) },
                                        onClick = {
                                            type = RepeatType.WEEKLY
                                            every = "1"
                                            mask = Recurrence.WORKDAYS
                                            menu = false
                                        },
                                    )
                                }
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
                    if (weekendPossible) {
                        Text("Am Wochenende", style = MaterialTheme.typography.labelLarge)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            WeekendChip("Normal", weekendMode == WeekendMode.NORMAL) {
                                weekendMode = WeekendMode.NORMAL
                            }
                            if (skipPossible) {
                                WeekendChip("Aussetzen", weekendMode == WeekendMode.SKIP) {
                                    weekendMode = WeekendMode.SKIP
                                }
                            }
                            WeekendChip("Andere Uhrzeit", weekendMode == WeekendMode.OTHER_TIME) {
                                weekendMode = WeekendMode.OTHER_TIME
                            }
                        }
                        if (weekendMode == WeekendMode.OTHER_TIME) {
                            OutlinedButton(onClick = {
                                TimePickerDialog(
                                    ctx,
                                    { _, h, min -> weekendMin = h * 60 + min },
                                    weekendMin / 60, weekendMin % 60, DateFormat.is24HourFormat(ctx),
                                ).show()
                            }) { Text("Sa/So um ${minutesLabel(weekendMin)}") }
                        }
                        if (type == RepeatType.WEEKLY) {
                            Text(
                                "Zum Aussetzen Sa/So oben abwählen.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
            }
        }
    }
}

@Composable
private fun WeekendChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

/** Start im Editor: am Wochenende mit eigener Uhrzeit die normale Uhrzeit der Serie zeigen. */
private fun editorStart(r: Reminder): Long {
    val base = Instant.ofEpochMilli(r.baseAt).atZone(zone)
    if (!Recurrence.weekendApplies(r) || !Recurrence.isWeekend(base.toLocalDate())) return r.baseAt
    val time = Instant.ofEpochMilli(r.anchorAt).atZone(zone).toLocalTime()
    return base.toLocalDate().atTime(time).atZone(zone).toInstant().toEpochMilli()
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
