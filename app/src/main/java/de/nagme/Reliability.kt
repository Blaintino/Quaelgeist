package de.nagme

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat

/**
 * Eine Voraussetzung für zuverlässiges Nagging.
 * [ok] = null: lässt sich nicht automatisch prüfen (nur Anleitung).
 */
data class Check(
    val title: String,
    val ok: Boolean?,
    val detail: String,
    val button: String? = null,
    val intent: ((Context) -> Intent)? = null,
)

object Reliability {

    private const val HUAWEI_HEALTH = "com.huawei.health"

    private fun pkgUri(ctx: Context) = Uri.fromParts("package", ctx.packageName, null)

    private fun appDetails(ctx: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri(ctx))

    @SuppressLint("BatteryLife") // private App, Ausnahme von der Akku-Optimierung ist gewollt
    fun checks(ctx: Context): List<Check> {
        val nm = NotificationManagerCompat.from(ctx)
        val sysNm = ctx.getSystemService(NotificationManager::class.java)
        Notifier.ensureChannel(ctx)
        val channel = sysNm.getNotificationChannel(Notifier.CHANNEL)
        val list = mutableListOf<Check>()

        list += Check(
            "Benachrichtigungen erlaubt",
            nm.areNotificationsEnabled(),
            "Ohne diese Erlaubnis kann NagMe gar nichts anzeigen.",
            "Einstellungen öffnen",
        ) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, it.packageName)
        }

        list += Check(
            "Kanal „Erinnerungen“ auf „Dringend“",
            channel != null && channel.importance >= NotificationManager.IMPORTANCE_HIGH,
            "Nur mit hoher Wichtigkeit gibt es Pop-up, Ton und Vibration. " +
                "Bitte dort auch Ton und Vibration eingeschaltet lassen.",
            "Kanal öffnen",
        ) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, it.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, Notifier.CHANNEL)
        }

        if (Build.VERSION.SDK_INT >= 31) {
            list += Check(
                "Exakte Wecker-Alarme",
                Scheduler.canScheduleExact(ctx),
                "Damit meldet sich NagMe pünktlich wie ein Wecker – auch im Tiefschlaf.",
                "Erlauben",
            ) { Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri(it)) }
        }

        val pm = ctx.getSystemService(PowerManager::class.java)
        list += Check(
            "Akku: „Nicht eingeschränkt“",
            pm.isIgnoringBatteryOptimizations(ctx.packageName),
            "Samsung beendet sonst gern Apps im Hintergrund.",
            "Ausnahme erlauben",
        ) { Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri(it)) }

        if (Build.VERSION.SDK_INT >= 28) {
            val am = ctx.getSystemService(ActivityManager::class.java)
            list += Check(
                "Hintergrundnutzung erlaubt",
                !am.isBackgroundRestricted,
                "In den App-Infos unter „Akku“ darf die Hintergrundnutzung nicht gesperrt sein.",
                "App-Infos öffnen",
                ::appDetails,
            )
        }

        if (Build.VERSION.SDK_INT >= 34) {
            list += Check(
                "Vollbild-Alarm",
                sysNm.canUseFullScreenIntent(),
                "Weckt bei gesperrtem Handy den Bildschirm und zeigt die Erinnerung groß an.",
                "Erlauben",
            ) { Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkgUri(it)) }
        }

        if (Build.VERSION.SDK_INT >= 30) {
            list += Check(
                "Bei Nichtnutzung nicht pausieren",
                ctx.packageManager.isAutoRevokeWhitelisted,
                "Android pausiert sonst Apps, die du länger nicht öffnest, und entzieht " +
                    "Berechtigungen. Schalter „App-Aktivität pausieren, wenn unbenutzt“ aus.",
                "Einstellung öffnen",
            ) { Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, pkgUri(it)) }
        }

        list += Check(
            "Samsung: „Nie schlafende Apps“",
            null,
            "Einstellungen → Akku → Hintergrundnutzungslimits → „Nie schlafende Apps“ → " +
                "NagMe hinzufügen. Dort am besten auch „Nicht verwendete Apps in Standby " +
                "versetzen“ ausschalten. Lässt sich von der App aus nicht prüfen.",
        )

        list += Check(
            "Huawei Band: Meldungen von NagMe",
            null,
            "In Huawei Health: Geräte → Band 11 → Benachrichtigungen → NagMe einschalten. " +
                "Huawei Health selbst braucht ebenfalls Akku „Nicht eingeschränkt“ und den " +
                "Benachrichtigungszugriff. Viele Bänder leiten nur weiter, solange der " +
                "Handy-Bildschirm aus ist – zum Testen also das Handy sperren.",
            "Huawei Health öffnen",
        ) {
            it.packageManager.getLaunchIntentForPackage(HUAWEI_HEALTH) ?: appDetails(it)
        }

        list += Check(
            "„Nicht stören“ / Schlafenszeit",
            null,
            "NagMe-Meldungen gelten als Alarm und kommen durch, solange bei „Nicht stören“ " +
                "Alarme erlaubt sind (Standard). Im Lautlos-Modus bleibt der Ton aus, " +
                "vibriert wird trotzdem.",
        )
        return list
    }

    /** Alle automatisch prüfbaren Punkte in Ordnung? */
    fun allOk(ctx: Context): Boolean = checks(ctx).all { it.ok != false }

    fun open(ctx: Context, check: Check) {
        val intent = check.intent?.invoke(ctx) ?: return
        runCatching { ctx.startActivity(intent) }
            .onFailure { runCatching { ctx.startActivity(appDetails(ctx)) } }
    }

    /** Legt eine Test-Erinnerung an, die in 15 Sekunden kommt und dreimal im Minutentakt nervt. */
    fun scheduleTest(ctx: Context) {
        val at = System.currentTimeMillis() + 15_000
        val r = Reminder(
            id = ReminderStore.newId(),
            title = "NagMe-Test",
            baseAt = at,
            fireAt = at,
            nagIntervalMin = 1,
            maxNags = 2,
        )
        ReminderStore(ctx).upsert(r)
        Scheduler.schedule(ctx, r)
    }
}

@Composable
fun ReliabilityScreen(tick: Int, onBack: () -> Unit, contentPadding: PaddingValues) {
    val ctx = LocalContext.current
    val checks = remember(tick) { Reliability.checks(ctx) }
    var testScheduled by remember { mutableStateOf(false) }

    Column(Modifier.padding(contentPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
            }
            Text("Zuverlässigkeit", style = MaterialTheme.typography.headlineSmall)
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Test", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Legt „NagMe-Test“ an: kommt in 15 Sekunden und dann noch zweimal " +
                                "im Minutentakt. Handy danach sperren und auf Handy und Band " +
                                "achten.",
                        )
                        Button(onClick = {
                            Reliability.scheduleTest(ctx)
                            testScheduled = true
                        }) { Text(if (testScheduled) "Noch ein Test" else "Test starten") }
                        if (testScheduled) {
                            Text(
                                "Test geplant. Jetzt Handy sperren.",
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            items(checks, key = { it.title }) { c -> CheckCard(c) }
        }
    }
}

@Composable
private fun CheckCard(c: Check) {
    val ctx = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val (mark, color) = when (c.ok) {
        true -> "✓" to colors.primary
        false -> "✗" to colors.error
        null -> "ⓘ" to colors.onSurfaceVariant
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = if (c.ok == false) {
            CardDefaults.cardColors(containerColor = colors.errorContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(mark, color = color, style = MaterialTheme.typography.titleLarge)
                Text(
                    c.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(c.detail, style = MaterialTheme.typography.bodyMedium)
            if (c.button != null && c.intent != null) {
                if (c.ok == false) {
                    Button(onClick = { Reliability.open(ctx, c) }) { Text(c.button) }
                } else {
                    OutlinedButton(onClick = { Reliability.open(ctx, c) }) { Text(c.button) }
                }
            }
        }
    }
}
