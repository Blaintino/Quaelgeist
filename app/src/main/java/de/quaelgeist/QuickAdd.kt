package de.quaelgeist

import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.RemoteViews
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Homescreen-Widget: ein Knopf, der direkt die Schnelleingabe öffnet.
 * Klassische RemoteViews statt Glance – keine zusätzliche Abhängigkeit.
 */
class QuickAddWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, QuickAddActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val views = RemoteViews(ctx.packageName, R.layout.widget_quick_add).apply {
            setOnClickPendingIntent(R.id.widget_root, open)
        }
        manager.updateAppWidget(ids, views)
    }
}

/**
 * Kleines Eingabefenster über dem Homescreen: Titel tippen, Zeit-Knopf antippen, fertig.
 * Gespeichert wird mit den Standardwerten (alle 10 Min erinnern, keine Wiederholung);
 * „Mehr Optionen" öffnet stattdessen den vollen Editor in der App.
 */
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NagTheme {
                QuickAddDialog(onClose = ::finish)
            }
        }
    }
}

@Composable
private fun QuickAddDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val zone = ZoneId.systemDefault()
    val now = remember { ZonedDateTime.now(zone) }
    val ready = title.isNotBlank()

    fun saveAt(at: ZonedDateTime) {
        val ms = at.toInstant().toEpochMilli()
        val r = Reminder(id = ReminderStore.newId(), title = title.trim(), baseAt = ms, fireAt = ms)
        ReminderStore(ctx).upsert(r)
        Scheduler.schedule(ctx, r)
        val label = at.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
        Toast.makeText(ctx, "Erinnerung: $label", Toast.LENGTH_SHORT).show()
        onClose()
    }

    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Neue Erinnerung") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Woran erinnern?") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
                Text("Wann?", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeButton("In 1 Std", ready, Modifier.weight(1f)) {
                        saveAt(QuickTimes.inOneHour(ZonedDateTime.now(zone)))
                    }
                    val evening = QuickTimes.thisEvening(now)
                    if (evening != null) {
                        TimeButton("Heute 18:00", ready, Modifier.weight(1f)) {
                            saveAt(QuickTimes.thisEvening(ZonedDateTime.now(zone)) ?: evening)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeButton("Morgen 8:00", ready, Modifier.weight(1f)) {
                        saveAt(QuickTimes.tomorrowMorning(ZonedDateTime.now(zone)))
                    }
                    TimeButton("Uhrzeit…", ready, Modifier.weight(1f)) {
                        val start = ZonedDateTime.now(zone)
                        TimePickerDialog(
                            ctx,
                            { _, h, min -> saveAt(QuickTimes.atTime(ZonedDateTime.now(zone), h, min)) },
                            start.hour, start.minute, DateFormat.is24HourFormat(ctx),
                        ).show()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                ctx.startActivity(
                    Intent(ctx, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_NEW_TITLE, title.trim())
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP,
                        ),
                )
                onClose()
            }) { Text("Mehr Optionen") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Abbrechen") } },
    )
}

@Composable
private fun TimeButton(label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(label, maxLines = 1)
    }
}
