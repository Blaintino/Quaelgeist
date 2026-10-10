package de.nagme

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

object Scheduler {

    const val EXTRA_ID = "id"

    private fun firePendingIntent(ctx: Context, id: Long): PendingIntent {
        val intent = Intent(ctx, AlarmReceiver::class.java).apply {
            data = Uri.parse("nagme://fire/$id")
            putExtra(EXTRA_ID, id)
        }
        return PendingIntent.getBroadcast(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Plant den Alarm für [Reminder.fireAt]; erledigte Erinnerungen werden abgemeldet. */
    fun schedule(ctx: Context, r: Reminder) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = firePendingIntent(ctx, r.id)
        if (r.done) {
            am.cancel(pi)
            return
        }
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.fireAt, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.fireAt, pi)
        }
    }

    fun cancel(ctx: Context, id: Long) {
        ctx.getSystemService(AlarmManager::class.java).cancel(firePendingIntent(ctx, id))
    }

    fun rescheduleAll(ctx: Context) {
        ReminderStore(ctx).all().filter { !it.done }.forEach { schedule(ctx, it) }
    }
}
