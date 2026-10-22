package de.quaelgeist

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Date

object Notifier {

    /**
     * Neue Kanal-ID, weil Android Einstellungen eines bestehenden Kanals (Vibration, Ton)
     * nach dem Anlegen nicht mehr ändern lässt. Die alten Kanäle werden entfernt.
     */
    const val CHANNEL = "nag_alert"
    private val OLD_CHANNELS = listOf("nag", "nag_alarm")

    /**
     * Vibrationsmuster pro Meldung: dreimal lang (gut 3 s). Ein einzelner 1-s-Impuls ging beim
     * Benutzen des Handys unter (per adb geprüft, 2026-10-12).
     */
    private val BUZZ_PATTERN = longArrayOf(0, 800, 400, 800, 400, 800)

    /**
     * Werden mehrere Erinnerungen innerhalb dieses Abstands fällig (gleiche Uhrzeit oder
     * gleichzeitig laufende Nags), klingelt und vibriert nur die erste; die übrigen erscheinen
     * still als eigene Meldung und lassen sich einzeln abhaken oder verschieben.
     */
    private const val ALERT_WINDOW_MS = 15_000L
    private const val KEY_LAST_ALERT = "lastAlertAt"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        OLD_CHANNELS.forEach(nm::deleteNotificationChannel)
        val channel = NotificationChannel(
            CHANNEL, "Erinnerungen", NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Hartnäckige Erinnerungen, bis du sie abhakst oder verschiebst"
            // Vibriert wird nur über [buzz], genau einmal pro Meldung. Kanal-Vibration und
            // direkte Vibration zusammen ergaben sonst eine doppelte Vibration.
            enableVibration(false)
            enableLights(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(
                Settings.System.DEFAULT_NOTIFICATION_URI,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        nm.createNotificationChannel(channel)
    }

    private fun actionIntent(ctx: Context, r: Reminder, action: String): PendingIntent {
        val intent = Intent(ctx, ActionReceiver::class.java).apply {
            this.action = action
            data = Uri.parse("quaelgeist://action/${r.id}/$action")
            putExtra(Scheduler.EXTRA_ID, r.id)
            putExtra(Actions.EXTRA_BASE, r.baseAt)
        }
        return PendingIntent.getBroadcast(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Öffnet den Nag-Bildschirm (Erledigt / Verschieben). Bei gesperrtem Handy fragt Android
     * vorher nach dem Entsperren, damit nichts versehentlich abgehakt oder verschoben wird.
     */
    fun nagScreenIntent(ctx: Context, r: Reminder): PendingIntent {
        val intent = Intent(ctx, NagActivity::class.java).apply {
            data = Uri.parse("quaelgeist://nag/${r.id}")
            putExtra(Scheduler.EXTRA_ID, r.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * „Link öffnen": eine Activity statt eines Broadcasts, weil Android ab Version 12 aus einem
     * Receiver heraus keine Activity (den Browser) mehr starten darf.
     */
    private fun openLinkIntent(ctx: Context, r: Reminder): PendingIntent {
        val intent = Intent(ctx, OpenLinkActivity::class.java).apply {
            data = Uri.parse("quaelgeist://link/${r.id}")
            putExtra(Scheduler.EXTRA_ID, r.id)
            putExtra(Actions.EXTRA_BASE, r.baseAt)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Darf diese Meldung klingeln und vibrieren? Nein, wenn eine andere Erinnerung das gerade
     * erst getan hat (siehe [ALERT_WINDOW_MS]). Alarm-Broadcasts laufen nacheinander im
     * Hauptthread, daher reicht ein einfacher Zeitstempel. commit() wie im [ReminderStore].
     */
    @SuppressLint("ApplySharedPref")
    fun claimAlert(ctx: Context): Boolean {
        val prefs = ctx.applicationContext.getSharedPreferences("notifier", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_LAST_ALERT, 0)
        if (now - last in 0 until ALERT_WINDOW_MS) return false
        prefs.edit().putLong(KEY_LAST_ALERT, now).commit()
        return true
    }

    /**
     * Zeigt die Meldung. Bewusst so gebaut, dass sie möglichst überall ankommt:
     * - Kategorie ALARM: kommt bei „Nicht stören" durch, solange dort Alarme erlaubt sind
     *   (Standard).
     * - Kein Vollbild-Intent: Der hat bei gesperrtem Handy den Erledigt-/Verschieben-Bildschirm
     *   ohne Entsperren gezeigt – zu leicht versehentlich bedient.
     * - Nicht „laufend" (ongoing) und jedes Mal neu gepostet statt aktualisiert: Uhren-Apps
     *   wie Huawei Health spiegeln laufende oder nur aktualisierte Meldungen oft nicht.
     * - [alert] = false: still (kein Ton, kein Pop-up), weil gerade eine andere Erinnerung
     *   gemeldet wurde.
     */
    @SuppressLint("MissingPermission")
    fun show(ctx: Context, r: Reminder, alert: Boolean = true) {
        ensureChannel(ctx)
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return

        val time = DateFormat.getTimeFormat(ctx).format(Date(r.baseAt))
        val text = buildString {
            append("Fällig: ").append(time)
            if (r.nagCount > 0) append(" · ").append(r.nagCount + 1).append(". Erinnerung")
        }
        val screen = nagScreenIntent(ctx, r)

        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(r.title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_LIGHTS)
            .setSilent(!alert)
            .setOnlyAlertOnce(false)
            .setAutoCancel(false)
            .setOngoing(false)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setContentIntent(screen)
            .addAction(
                // Auf dem Sperrbildschirm erst nach dem Entsperren (ab Android 12), damit nichts
                // versehentlich abgehakt wird. Entsperrt gibt es keinen Extraschritt.
                NotificationCompat.Action.Builder(0, "Erledigt", actionIntent(ctx, r, Actions.DONE))
                    .setAuthenticationRequired(true)
                    .build(),
            )
            .addAction(0, "Verschieben…", screen)
            .apply {
                // Dritter Knopf nur mit Link. Öffnen hakt ab (bei Serien: diesen Termin), daher
                // wie *Erledigt* erst nach dem Entsperren.
                if (Links.isOpenable(r.link)) {
                    addAction(
                        NotificationCompat.Action.Builder(0, "Link öffnen", openLinkIntent(ctx, r))
                            .setAuthenticationRequired(true)
                            .build(),
                    )
                }
            }
            .build()
        // Erst entfernen, dann neu posten: So zählt jede Wiederholung als neue Meldung
        // (Ton, Vibration, Weiterleitung an die Uhr) statt als stilles Update.
        nm.cancel(r.id.toInt())
        nm.notify(r.id.toInt(), n)
    }

    /**
     * Vibriert direkt als Alarm-Vibration ([BUZZ_PATTERN]); die einzige Vibration pro Meldung. Die läuft auch im Lautlos-Modus und wenn
     * der Hersteller die Vibration von Benachrichtigungen dämpft – das war bei anderen
     * Erinnerungs-Apps der Grund für unzuverlässigen Vibrationsalarm.
     */
    @Suppress("DEPRECATION")
    fun buzz(ctx: Context) {
        val effect = VibrationEffect.createWaveform(BUZZ_PATTERN, -1)
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            ctx.getSystemService(Vibrator::class.java)
        }
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= 33) {
            vibrator.vibrate(
                effect,
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
            )
        } else {
            vibrator.vibrate(
                effect,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
            )
        }
    }

    fun cancel(ctx: Context, id: Long) {
        NotificationManagerCompat.from(ctx).cancel(id.toInt())
    }
}
