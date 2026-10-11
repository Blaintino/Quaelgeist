package de.nagme

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
     * nach dem Anlegen nicht mehr ändern lässt. Der alte Kanal `nag` wird entfernt.
     */
    const val CHANNEL = "nag_alarm"
    private const val OLD_CHANNEL = "nag"

    /** Kräftiges Muster: dreimal lang. Wird für Kanal und direkte Vibration genutzt. */
    private val PATTERN = longArrayOf(0, 800, 400, 800, 400, 800)

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel(OLD_CHANNEL)
        val channel = NotificationChannel(
            CHANNEL, "Erinnerungen", NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Hartnäckige Erinnerungen, bis du sie abhakst oder verschiebst"
            enableVibration(true)
            vibrationPattern = PATTERN
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
            data = Uri.parse("nagme://action/${r.id}/$action")
            putExtra(Scheduler.EXTRA_ID, r.id)
            putExtra(Actions.EXTRA_BASE, r.baseAt)
        }
        return PendingIntent.getBroadcast(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Öffnet den Nag-Bildschirm (Erledigt / Verschieben) – auch über dem Sperrbildschirm. */
    fun nagScreenIntent(ctx: Context, r: Reminder): PendingIntent {
        val intent = Intent(ctx, NagActivity::class.java).apply {
            data = Uri.parse("nagme://nag/${r.id}")
            putExtra(Scheduler.EXTRA_ID, r.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Zeigt die Meldung. Bewusst so gebaut, dass sie möglichst überall ankommt:
     * - Kategorie ALARM: kommt bei „Nicht stören" durch, solange dort Alarme erlaubt sind
     *   (Standard).
     * - Vollbild-Intent: weckt bei gesperrtem Handy den Bildschirm wie ein Wecker.
     * - Nicht „laufend" (ongoing) und jedes Mal neu gepostet statt aktualisiert: Uhren-Apps
     *   wie Huawei Health spiegeln laufende oder nur aktualisierte Meldungen oft nicht.
     */
    @SuppressLint("MissingPermission")
    fun show(ctx: Context, r: Reminder) {
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
            .setVibrate(PATTERN)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_LIGHTS)
            .setOnlyAlertOnce(false)
            .setAutoCancel(false)
            .setOngoing(false)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)
            .addAction(0, "Erledigt", actionIntent(ctx, r, Actions.DONE))
            .addAction(0, "Verschieben…", screen)
            .build()
        // Erst entfernen, dann neu posten: So zählt jede Wiederholung als neue Meldung
        // (Ton, Vibration, Weiterleitung an die Uhr) statt als stilles Update.
        nm.cancel(r.id.toInt())
        nm.notify(r.id.toInt(), n)
    }

    /**
     * Vibriert zusätzlich direkt als Alarm-Vibration. Die läuft auch im Lautlos-Modus und wenn
     * der Hersteller die Vibration von Benachrichtigungen dämpft – das war bei anderen
     * Erinnerungs-Apps der Grund für unzuverlässigen Vibrationsalarm.
     */
    @Suppress("DEPRECATION")
    fun buzz(ctx: Context) {
        val effect = VibrationEffect.createWaveform(PATTERN, -1)
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
