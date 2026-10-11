package de.quaelgeist

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Bildschirm für eine fällige Erinnerung: Erledigt oder Verschieben.
 * Öffnet sich über den Vollbild-Intent (auch über dem Sperrbildschirm), beim Tippen auf die
 * Meldung und über die Schaltfläche „Verschieben…".
 */
class NagActivity : ComponentActivity() {

    private val reminderId = mutableLongStateOf(-1L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        reminderId.longValue = intent.getLongExtra(Scheduler.EXTRA_ID, -1)
        setContent {
            NagTheme {
                NagScreen(reminderId.longValue, onClose = ::finish)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        reminderId.longValue = intent.getLongExtra(Scheduler.EXTRA_ID, -1)
    }

    @Suppress("DEPRECATION")
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }
}

@Composable
private fun NagScreen(id: Long, onClose: () -> Unit) {
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
        onClose()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onClose) {
                Text(
                    if (r.canNag) "Schließen (erinnert weiter alle ${r.nagIntervalMin} Min)"
                    else "Schließen",
                )
            }
        }
    }
}

@Composable
private fun SnoozeButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(52.dp)) { Text(label) }
}
