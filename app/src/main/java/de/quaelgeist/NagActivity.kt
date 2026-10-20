package de.quaelgeist

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Bildschirm für eine fällige Erinnerung: Erledigt oder Verschieben.
 * Öffnet sich beim Tippen auf die Meldung und über die Schaltfläche „Verschieben…".
 * Bewusst nicht über dem Sperrbildschirm: Android verlangt vorher das Entsperren, damit
 * nichts versehentlich abgehakt oder verschoben wird.
 */
class NagActivity : ComponentActivity() {

    private val reminderId = mutableLongStateOf(-1L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reminderId.longValue = intent.getLongExtra(Scheduler.EXTRA_ID, -1)
        setContent {
            NagTheme {
                NagScreen(
                    reminderId.longValue,
                    onClose = ::finish,
                    onSwitch = { reminderId.longValue = it },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        reminderId.longValue = intent.getLongExtra(Scheduler.EXTRA_ID, -1)
    }
}

/**
 * Zwei unabhängige Hilfen für mehrere offene Erinnerungen (Wunsch des Nutzers: erst beide
 * ausprobieren, dann ggf. eine entfernen – jede lässt sich hier einzeln abschalten bzw. samt
 * ihrem Block löschen):
 * - [AUTO_ADVANCE]: Nach Erledigt/Verschieben gleich die nächste offene zeigen statt schließen.
 * - [SHOW_OTHERS]: Unten eine Liste der weiteren offenen; Tippen wechselt dorthin.
 */
private const val AUTO_ADVANCE = true
private const val SHOW_OTHERS = true

private fun othersNow(ctx: Context, currentId: Long): List<Reminder> =
    OpenReminders.others(ReminderStore(ctx).all(), currentId, System.currentTimeMillis())

@Composable
private fun NagScreen(id: Long, onClose: () -> Unit, onSwitch: (Long) -> Unit) {
    val ctx = LocalContext.current
    val version by ReminderStore.version.collectAsState()
    val r = remember(id, version) { ReminderStore(ctx).get(id) }
    // Termin, für den der Bildschirm geöffnet wurde. Wird er anderswo erledigt (Schaltfläche
    // in der Meldung, Uhr, Hauptansicht), schließt sich der Bildschirm von selbst.
    val openedFor = remember(id) { r?.baseAt }

    if (r == null || r.done || r.baseAt != openedFor) {
        LaunchedEffect(id, version) { onClose() }
        return
    }

    val zone = ZoneId.systemDefault()
    val due = Instant.ofEpochMilli(r.baseAt).atZone(zone).format(
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT),
    )

    fun act(block: () -> Unit) {
        block()
        // AUTO_ADVANCE: weiter zur nächsten offenen Erinnerung, sonst schließen.
        val next = if (AUTO_ADVANCE) othersNow(ctx, r.id).firstOrNull() else null
        if (next != null) onSwitch(next.id) else onClose()
    }
    // Weitere offene (für den Hinweis bei AUTO_ADVANCE und die Liste bei SHOW_OTHERS);
    // neu berechnet, sobald sich Erinnerungen ändern.
    val others = remember(id, version) { othersNow(ctx, id) }

    // key: beim Wechsel zur nächsten Erinnerung wieder oben anfangen.
    key(id) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                r.title,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            Text("Fällig: $due", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (AUTO_ADVANCE && others.isNotEmpty()) {
                Text(
                    "Danach noch ${others.size} offen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { act { Actions.markDone(ctx, r.id, r.baseAt) } },
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) { Text("Erledigt", style = MaterialTheme.typography.titleLarge) }

            Spacer(Modifier.height(8.dp))
            Text("Verschieben um", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SnoozeButton("10 Min", Modifier.weight(1f)) {
                    act { Actions.snooze(ctx, r.id, 10, r.baseAt) }
                }
                SnoozeButton("30 Min", Modifier.weight(1f)) {
                    act { Actions.snooze(ctx, r.id, 30, r.baseAt) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SnoozeButton("1 Std", Modifier.weight(1f)) {
                    act { Actions.snooze(ctx, r.id, 60, r.baseAt) }
                }
                SnoozeButton("3 Std", Modifier.weight(1f)) {
                    act { Actions.snooze(ctx, r.id, 180, r.baseAt) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SnoozeButton("Morgen", Modifier.weight(1f)) {
                    act { Actions.snoozeTomorrow(ctx, r.id, r.baseAt) }
                }
                SnoozeButton("Uhrzeit…", Modifier.weight(1f)) {
                    val now = Instant.now().atZone(zone)
                    TimePickerDialog(
                        ctx,
                        { _, h, min ->
                            // Liegt die Uhrzeit heute schon zurück, ist morgen gemeint.
                            var at = now.withHour(h).withMinute(min).withSecond(0).withNano(0)
                            if (!at.isAfter(now)) at = at.plusDays(1)
                            act {
                                Actions.snoozeUntil(
                                    ctx, r.id, at.toInstant().toEpochMilli(), r.baseAt,
                                )
                            }
                        },
                        now.hour, now.minute, DateFormat.is24HourFormat(ctx),
                    ).show()
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SnoozeButton("Montag", Modifier.weight(1f)) {
                    act { Actions.snoozeNextMonday(ctx, r.id, r.baseAt) }
                }
                SnoozeButton("Anderer Tag…", Modifier.weight(1f)) {
                    pickDayAndTime(ctx, r) { at ->
                        act { Actions.snoozeUntil(ctx, r.id, at, r.baseAt) }
                    }
                }
            }
            // Für echte Änderungen (Titel, Wiederholung, Termin der Serie) den Editor öffnen.
            TextButton(onClick = {
                ctx.startActivity(
                    Intent(ctx, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_EDIT_ID, r.id)
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP,
                        ),
                )
                onClose()
            }) { Text("In der App bearbeiten") }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onClose) {
                Text(
                    if (r.canNag) "Schließen (erinnert weiter alle ${r.nagIntervalMin} Min)"
                    else "Schließen",
                )
            }

            if (SHOW_OTHERS && others.isNotEmpty()) OtherOpen(others, onSwitch)
        }
    } }
}

/** SHOW_OTHERS: weitere offene Erinnerungen, Tippen wechselt zu ihr. */
@Composable
private fun OtherOpen(others: List<Reminder>, onSwitch: (Long) -> Unit) {
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Text(
        "Weitere offene Erinnerungen (${others.size})",
        style = MaterialTheme.typography.labelLarge,
    )
    others.forEach { o ->
        OutlinedButton(onClick = { onSwitch(o.id) }, modifier = Modifier.fillMaxWidth()) {
            Text(o.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * „Anderer Tag…": erst das Datum, dann die Uhrzeit (vorbelegt mit der des ursprünglichen
 * Termins). Liegt der Zeitpunkt schon zurück, gilt der nächste Tag zu dieser Uhrzeit.
 */
private fun pickDayAndTime(ctx: Context, r: Reminder, onPicked: (Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    val now = ZonedDateTime.now(zone)
    val original = Instant.ofEpochMilli(r.baseAt).atZone(zone)
    val start = now.plusDays(1)
    DatePickerDialog(
        ctx,
        { _, y, m, d ->
            TimePickerDialog(
                ctx,
                { _, h, min ->
                    val at = QuickTimes.onDayOrNext(
                        LocalDate.of(y, m + 1, d), LocalTime.of(h, min), ZonedDateTime.now(zone),
                    )
                    onPicked(at.toInstant().toEpochMilli())
                },
                original.hour, original.minute, DateFormat.is24HourFormat(ctx),
            ).show()
        },
        start.year, start.monthValue - 1, start.dayOfMonth,
    ).apply { datePicker.minDate = now.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli() }
        .show()
}

@Composable
private fun SnoozeButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(52.dp)) { Text(label) }
}
