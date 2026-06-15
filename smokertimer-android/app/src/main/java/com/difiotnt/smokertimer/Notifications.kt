package com.difiotnt.smokertimer

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object SmokingNotifications {
    const val CHANNEL_ID = "smoking_allowed"
    private const val CHANNEL_NAME = "Smoking allowed"
    const val ACTION_SMOKING_ALLOWED = "com.difiotnt.smokertimer.SMOKING_ALLOWED"
    const val EXTRA_ALLOWED_AT = "extra_allowed_at"
    const val EXTRA_INTERVAL_MINUTES = "extra_interval_minutes"
    const val EXTRA_LAST_NOTE = "extra_last_note"
    const val NOTIFICATION_ID = 7001
    private const val REQUEST_CODE = 7001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Pengingat bahwa smoking sudah boleh lagi"
        }
        manager.createNotificationChannel(channel)
    }

    fun scheduleReminder(
        context: Context,
        entry: SmokingEntry?,
        settings: SmokingSettings,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntent(context, entry, settings)

        if (entry == null || !settings.notificationsEnabled) {
            alarmManager.cancel(pendingIntent)
            return
        }

        val allowedAt = entry.timestampMillis + settings.intervalMinutes * 60_000L
        alarmManager.cancel(pendingIntent)

        if (allowedAt <= System.currentTimeMillis()) {
            context.sendBroadcast(
                Intent(context, SmokingAllowedReceiver::class.java).apply {
                    action = ACTION_SMOKING_ALLOWED
                    putExtra(EXTRA_ALLOWED_AT, allowedAt)
                    putExtra(EXTRA_INTERVAL_MINUTES, settings.intervalMinutes)
                    putExtra(EXTRA_LAST_NOTE, entry.note)
                },
            )
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, allowedAt, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, allowedAt, pendingIntent)
        }
    }

    fun cancelReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildPendingIntent(context, null, null))
    }

    fun buildNotification(context: Context, title: String, content: String): Notification {
        ensureChannel(context)

        val launchIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(Notification.BigTextStyle().bigText(content))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
    }

    private fun buildPendingIntent(
        context: Context,
        entry: SmokingEntry?,
        settings: SmokingSettings?,
    ): PendingIntent {
        val intent = Intent(context, SmokingAllowedReceiver::class.java).apply {
            action = ACTION_SMOKING_ALLOWED
            if (entry != null && settings != null) {
                putExtra(EXTRA_ALLOWED_AT, entry.timestampMillis + settings.intervalMinutes * 60_000L)
                putExtra(EXTRA_INTERVAL_MINUTES, settings.intervalMinutes)
                putExtra(EXTRA_LAST_NOTE, entry.note)
            }
        }

        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class SmokingAllowedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val allowedAt = intent.getLongExtra(SmokingNotifications.EXTRA_ALLOWED_AT, System.currentTimeMillis())
        val intervalMinutes = intent.getIntExtra(SmokingNotifications.EXTRA_INTERVAL_MINUTES, 60)
        val lastNote = intent.getStringExtra(SmokingNotifications.EXTRA_LAST_NOTE).orEmpty()
        val allowedTime = Instant.ofEpochMilli(allowedAt)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(DateTimeFormatter.ofPattern("HH:mm", Locale("id", "ID")))

        val message = buildString {
            append("Smoking sudah boleh lagi.")
            append(" Waktu berikutnya: ")
            append(allowedTime)
            append(".")
            append(" Interval: ")
            append(intervalMinutes)
            append(" menit.")
            if (lastNote.isNotBlank()) {
                append(" Catatan terakhir: ")
                append(lastNote)
                append(".")
            }
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            SmokingNotifications.NOTIFICATION_ID,
            SmokingNotifications.buildNotification(
                context = context,
                title = "Smoking allowed",
                content = message,
            ),
        )
    }
}
