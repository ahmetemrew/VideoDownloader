package com.basitce.videodownloader.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.basitce.videodownloader.MainActivity
import com.basitce.videodownloader.R

/**
 * Video indirme foreground servisi
 * Arka planda indirme işlemlerini yönetir
 */
class DownloadService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "download_channel"
        const val CHANNEL_NAME = "İndirmeler"

        const val ACTION_START_DOWNLOAD = "ACTION_START_DOWNLOAD"
        const val ACTION_PAUSE_DOWNLOAD = "ACTION_PAUSE_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "ACTION_CANCEL_DOWNLOAD"
        
        const val EXTRA_DOWNLOAD_ID = "EXTRA_DOWNLOAD_ID"
        const val EXTRA_DOWNLOAD_URL = "EXTRA_DOWNLOAD_URL"
        const val EXTRA_FILE_NAME = "EXTRA_FILE_NAME"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1)
                val url = intent.getStringExtra(EXTRA_DOWNLOAD_URL) ?: ""
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "video"
                
                if (downloadId != -1L && url.isNotEmpty()) {
                    startForeground(NOTIFICATION_ID, createNotification(fileName, 0))
                    // TODO: Gerçek indirme işlemi
                }
            }
            ACTION_PAUSE_DOWNLOAD -> {
                // TODO: İndirme duraklat
            }
            ACTION_CANCEL_DOWNLOAD -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Video indirme bildirimleri"
                setShowBadge(false)
            }
            
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(fileName: String, progress: Int): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("İndiriliyor")
            .setContentText(fileName)
            .setSmallIcon(R.drawable.ic_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * İlerleme bildirimini günceller
     */
    private fun updateProgress(fileName: String, progress: Int) {
        val notification = createNotification(fileName, progress)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * İndirme tamamlandı bildirimi gösterir
     */
    private fun showCompletedNotification(fileName: String) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("İndirme tamamlandı")
            .setContentText(fileName)
            .setSmallIcon(R.drawable.ic_check_circle)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID + 1, notification)
        
        stopSelf()
    }
}
