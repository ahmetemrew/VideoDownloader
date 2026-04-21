package com.basitce.videodownloader.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.basitce.videodownloader.MainActivity
import com.basitce.videodownloader.R

/**
 * İndirme bildirimlerini yöneten yardımcı sınıf.
 */
object NotificationHelper {

    private const val CHANNEL_ID_PROGRESS = "download_progress"
    private const val CHANNEL_ID_COMPLETE = "download_complete"
    private const val CHANNEL_NAME_PROGRESS = "İndirme ilerlemesi"
    private const val CHANNEL_NAME_COMPLETE = "İndirme tamamlandı"

    /**
     * Bildirim kanallarını oluşturur.
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val progressChannel = NotificationChannel(
                CHANNEL_ID_PROGRESS,
                CHANNEL_NAME_PROGRESS,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Aktif indirme ilerlemesi"
                setShowBadge(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(progressChannel)

            val completeChannel = NotificationChannel(
                CHANNEL_ID_COMPLETE,
                CHANNEL_NAME_COMPLETE,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Tamamlanan indirme bildirimleri"
                setShowBadge(true)
            }
            manager.createNotificationChannel(completeChannel)
        }
    }

    /**
     * İndirme ilerlemesi bildirimi gösterir.
     */
    fun showDownloadProgress(context: Context, downloadId: Long, fileName: String, progress: Int) {
        if (!hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            downloadId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_PROGRESS)
            .setContentTitle("İndiriliyor")
            .setContentText(fileName)
            .setSmallIcon(R.drawable.ic_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(downloadId.toInt(), notification)
        } catch (_: SecurityException) {
            // Bildirim izni yoksa sessizce devam et.
        }
    }

    /**
     * İndirme tamamlandı bildirimi gösterir.
     */
    fun showDownloadComplete(context: Context, downloadId: Long, fileName: String) {
        if (!hasNotificationPermission(context)) return

        cancelNotification(context, downloadId)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "downloads")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            downloadId.toInt() + 1000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_COMPLETE)
            .setContentTitle("İndirme tamamlandı")
            .setContentText(fileName)
            .setSmallIcon(R.drawable.ic_check_circle)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(downloadId.toInt() + 1000, notification)
        } catch (_: SecurityException) {
            // Bildirim izni yok.
        }
    }

    /**
     * İndirme başarısız bildirimi gösterir.
     */
    fun showDownloadFailed(context: Context, downloadId: Long, fileName: String, error: String) {
        if (!hasNotificationPermission(context)) return

        cancelNotification(context, downloadId)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_COMPLETE)
            .setContentTitle("İndirme başarısız")
            .setContentText(fileName)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$fileName\n$error"))
            .setSmallIcon(R.drawable.ic_error)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(downloadId.toInt() + 2000, notification)
        } catch (_: SecurityException) {
            // Bildirim izni yok.
        }
    }

    /**
     * Belirli bir bildirimi kaldırır.
     */
    fun cancelNotification(context: Context, downloadId: Long) {
        try {
            NotificationManagerCompat.from(context).cancel(downloadId.toInt())
        } catch (_: Exception) {
            // Ignore
        }
    }

    /**
     * Tüm bildirimleri kaldırır.
     */
    fun cancelAllNotifications(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancelAll()
        } catch (_: Exception) {
            // Ignore
        }
    }

    /**
     * Eski sürümlerden kalan takılı ilerleme bildirimlerini temizler.
     */
    fun cancelStaleProgressNotifications(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }

        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.activeNotifications
                ?.filter { it.notification.channelId == CHANNEL_ID_PROGRESS }
                ?.forEach { manager.cancel(it.id) }
        } catch (_: Exception) {
            // Ignore
        }
    }

    /**
     * Bildirim izni olup olmadığını kontrol eder.
     */
    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
