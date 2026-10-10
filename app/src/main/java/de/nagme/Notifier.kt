package de.nagme

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Date

object Notifier {

    private const val CHANNEL = "nag"

    fun ensureChannel(ctx: Context) {
        val channel = NotificationChannel(
            CHANNEL, "Erinnerungen", NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Hartnäckige Erinnerungen, bis du sie abhakst"
            enableVibration(true)
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun actionIntent(ctx: Context, r: Reminder, action: String, minutes: Int = 0): PendingIntent {
        val intent = Intent(ctx, ActionReceiver::class.java).apply {
            this.action = action
            data = Uri.parse("nagme://action/${r.id}/$action/$minutes")
            putExtra(Scheduler.EXTRA_ID, r.id)
            putExtra(Actions.EXTRA_BASE, r.baseAt)
            putExtra(Actions.EXTRA_MIN, minutes)
        }
        return PendingIntent.getBroadcast(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    @SuppressLint("MissingPermission")
    fun show(ctx: Context, r: Reminder) {
        ensureChannel(ctx)
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return

        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val time = DateFormat.getTimeFormat(ctx).format(Date(r.baseAt))
        val text = buildString {
            append("Fällig: ").append(time)
            if (r.nagCount > 0) append(" · Erinnerung ").append(r.nagCount + 1)
        }

        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(r.title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setOnlyAlertOnce(false)
            .setAutoCancel(false)
            .setContentIntent(open)
            .addAction(0, "Erledigt", actionIntent(ctx, r, Actions.DONE))
            .addAction(0, "+10 Min", actionIntent(ctx, r, Actions.SNOOZE, 10))
            .addAction(0, "+1 Std", actionIntent(ctx, r, Actions.SNOOZE, 60))
            .build()
        nm.notify(r.id.toInt(), n)
    }

    fun cancel(ctx: Context, id: Long) {
        NotificationManagerCompat.from(ctx).cancel(id.toInt())
    }
}
