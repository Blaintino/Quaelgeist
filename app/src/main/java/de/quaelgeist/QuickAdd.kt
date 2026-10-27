package de.quaelgeist

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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

/**
 * Aufbau wie die Editoren (✕ und Überschrift oben), Zeitknöpfe wie der Verschieben-Bildschirm
 * (Wunsch des Nutzers: einheitliche Fenster). Alles Genauere im Editor.
 */
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
        // Link im Titel wandert ins Linkfeld, wie im Editor.
        val (cleanTitle, link) = Links.split(title, "")
        val r = Reminder(
            id = ReminderStore.newId(), title = cleanTitle, baseAt = ms, fireAt = ms, link = link,
        )
        ReminderStore(ctx).upsert(r)
        Scheduler.schedule(ctx, r)
        val label = at.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
        Toast.makeText(ctx, "Erinnerung: $label", Toast.LENGTH_SHORT).show()
        onClose()
    }

    fun openEditor() {
        ctx.startActivity(
            Intent(ctx, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_NEW_TITLE, title.trim())
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                ),
        )
        onClose()
    }

    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }

    Dialog(onDismissRequest = onClose) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.padding(bottom = 16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp),
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Abbrechen")
                    }
                    Text(
                        "Neue Erinnerung",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                }
                Column(
                    Modifier.padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
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
                    SectionLabel("Wann?")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SnoozeButton("10 Min", Modifier.weight(1f), enabled = ready) {
                            saveAt(QuickTimes.inMinutes(ZonedDateTime.now(zone), 10))
                        }
                        SnoozeButton("1 Std", Modifier.weight(1f), enabled = ready) {
                            saveAt(QuickTimes.inOneHour(ZonedDateTime.now(zone)))
                        }
                    }
                    // Feste Uhrzeiten 9:30 und 17:00: heute, falls noch nicht vorbei, sonst morgen.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickTimes.PRESETS.forEach { preset ->
                            val at = QuickTimes.atTime(now, preset)
                            SnoozeButton(
                                preset.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
                                Modifier.weight(1f),
                                if (at.toLocalDate() == now.toLocalDate()) "Heute" else "Morgen",
                                enabled = ready,
                            ) { saveAt(QuickTimes.atTime(ZonedDateTime.now(zone), preset)) }
                        }
                    }
                    SnoozeButton("Im Editor öffnen", Modifier.fillMaxWidth(), onClick = ::openEditor)
                }
            }
        }
    }
}
