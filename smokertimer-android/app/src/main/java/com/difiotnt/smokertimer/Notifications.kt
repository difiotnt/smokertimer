package com.difiotnt.smokertimer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.Locale

object SmokingNotifications {
    const val CHANNEL_ID = "smoking_allowed"
    private const val CHANNEL_NAME = "Smoking allowed"
    const val WORK_NAME = "smoking_allowed_reminder"
    const val NOTIFICATION_ID = 7001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
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
        val workManager = WorkManager.getInstance(context)

        if (entry == null || !settings.notificationsEnabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        val allowedAt = entry.timestampMillis + settings.intervalMinutes * 60_000L
        val delayMillis = (allowedAt - System.currentTimeMillis()).coerceAtLeast(0L)

        val request = OneTimeWorkRequestBuilder<SmokingAllowedWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    SmokingAllowedWorker.KEY_ENTRY_ID to entry.id,
                    SmokingAllowedWorker.KEY_ALLOWED_AT to allowedAt,
                    SmokingAllowedWorker.KEY_INTERVAL_MINUTES to settings.intervalMinutes,
                    SmokingAllowedWorker.KEY_LAST_NOTE to entry.note,
                ),
            )
            .build()

        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelReminder(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
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
            .setPriority(Notification.PRIORITY_HIGH)
            .build()
    }
}

class SmokingAllowedWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    override fun doWork(): Result {
        val allowedAt = inputData.getLong(KEY_ALLOWED_AT, System.currentTimeMillis())
        val intervalMinutes = inputData.getInt(KEY_INTERVAL_MINUTES, 60)
        val lastNote = inputData.getString(KEY_LAST_NOTE).orEmpty()
        val allowedTime = Instant.ofEpochMilli(allowedAt)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(DateTimeFormatter.ofPattern("HH:mm", Locale("id", "ID")))

        val content = buildString {
            append("Smoking sudah boleh lagi.")
            append(" Interval selesai setelah ")
            append(intervalMinutes)
            append(" menit.")
            append(" Waktu berikutnya: ")
            append(allowedTime)
            append(".")
            if (lastNote.isNotBlank()) {
                append(" Catatan terakhir: ")
                append(lastNote)
                append(".")
            }
        }

        val notification = SmokingNotifications.buildNotification(
            applicationContext,
            title = "Smoking allowed",
            content = content,
        )

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
        return Result.success()
    }

    companion object {
        const val KEY_ENTRY_ID = "entry_id"
        const val KEY_ALLOWED_AT = "allowed_at"
        const val KEY_INTERVAL_MINUTES = "interval_minutes"
        const val KEY_LAST_NOTE = "last_note"
        const val NOTIFICATION_ID = SmokingNotifications.NOTIFICATION_ID
    }
}
