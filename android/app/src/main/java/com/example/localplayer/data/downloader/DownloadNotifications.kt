package com.example.localplayer.data.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.localplayer.MainActivity

/** Уведомления загрузчика: один канал, одно foreground-уведомление на задачу. */
object DownloadNotifications {

    const val CHANNEL_ID = "downloads"

    /** 1001 занят уведомлением Media3 — иначе одно перетирало бы другое. */
    const val PROGRESS_NOTIFICATION_ID = 2001
    private const val DONE_NOTIFICATION_ID = 2002

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Загрузки",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Скачивание треков и обработка файлов"
                setShowBadge(false)
            },
        )
    }

    fun progress(
        context: Context,
        title: String,
        status: String,
        fraction: Float,
    ): Notification {
        val percent = (fraction.coerceIn(0f, 1f) * 100).toInt()
        val indeterminate = fraction <= 0f

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(status)
            .setProgress(100, percent, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openAppIntent(context))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    fun done(context: Context, title: String) {
        notifySafely(
            context,
            DONE_NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Скачано")
                .setContentText(title)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent(context))
                .build(),
        )
    }

    fun failed(context: Context, title: String, message: String) {
        notifySafely(
            context,
            DONE_NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .setContentIntent(openAppIntent(context))
                .build(),
        )
    }

    private fun notifySafely(context: Context, id: Int, notification: Notification) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        runCatching { manager.notify(id, notification) }
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Строка для уведомления: обрезаем простыню из вывода yt-dlp. */
    fun statusLine(raw: String): String {
        val cleaned = raw.substringAfter("]").trim().ifEmpty { raw.trim() }
        return cleaned.take(120).ifEmpty { "Скачивание" }
    }
}
