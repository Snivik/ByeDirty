package dev.snivik.byedirty.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.snivik.byedirty.MainActivity
import dev.snivik.byedirty.R
import dev.snivik.byedirty.data.ShowerStore
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** A single "replace your filter" notification a few days before the estimated refill date. */
object Reminders {
    private const val CHANNEL_ID = "filter"
    private const val DAYS_BEFORE = 3
    private val AT = LocalTime.of(9, 0)

    /** Returns the date the reminder will fire on, or null if none is scheduled. */
    fun reschedule(context: Context, store: ShowerStore): LocalDate? {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = pendingIntent(context)
        alarms.cancel(pending)
        val estimate = store.estimate ?: return null
        if (!store.remindersEnabled) return null

        val fireDate = estimate.refillDate?.minusDays(DAYS_BEFORE.toLong()) ?: return null
        val fireAt = fireDate.atTime(AT).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (fireAt <= System.currentTimeMillis()) return null
        // Inexact is fine for a reminder days ahead and needs no exact-alarm permission.
        alarms.set(AlarmManager.RTC_WAKEUP, fireAt, pending)
        return fireDate
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun notify(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_drop)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(context.getString(R.string.reminder_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(1, notification)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // Alarms don't survive a reboot.
            Intent.ACTION_BOOT_COMPLETED -> Reminders.reschedule(context, ShowerStore(context))
            else -> Reminders.notify(context)
        }
    }
}
