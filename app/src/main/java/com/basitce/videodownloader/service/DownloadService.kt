package com.basitce.videodownloader.service

import android.app.Notification
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
import com.basitce.videodownloader.data.AppPreferences
import com.basitce.videodownloader.data.GallerySaver
import com.basitce.videodownloader.data.model.DownloadItem
import com.basitce.videodownloader.data.model.DownloadStatus
import com.basitce.videodownloader.data.repository.DownloadRepository
import com.basitce.videodownloader.data.scraper.VideoDownloader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class DownloadService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "download_service"
        private const val CHANNEL_NAME = "Indirme servisi"

        private const val ACTION_PROCESS_QUEUE = "com.basitce.videodownloader.action.PROCESS_QUEUE"
        private const val ACTION_CANCEL_DOWNLOAD = "com.basitce.videodownloader.action.CANCEL_DOWNLOAD"
        private const val ACTION_STOP_SERVICE = "com.basitce.videodownloader.action.STOP_SERVICE"

        private const val EXTRA_DOWNLOAD_ID = "extra_download_id"

        fun createProcessQueueIntent(context: Context): Intent {
            return Intent(context, DownloadService::class.java).apply {
                action = ACTION_PROCESS_QUEUE
            }
        }

        fun cancelDownload(context: Context, downloadId: Long) {
            context.startService(
                Intent(context, DownloadService::class.java).apply {
                    action = ACTION_CANCEL_DOWNLOAD
                    putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                }
            )
        }

        fun stopService(context: Context) {
            context.startService(
                Intent(context, DownloadService::class.java).apply {
                    action = ACTION_STOP_SERVICE
                }
            )
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val processingMutex = Mutex()

    private lateinit var downloadRepository: DownloadRepository
    private lateinit var videoDownloader: VideoDownloader
    private lateinit var appPreferences: AppPreferences

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        NotificationHelper.createNotificationChannels(this)
        downloadRepository = DownloadRepository(this)
        videoDownloader = VideoDownloader(this)
        appPreferences = AppPreferences(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL_DOWNLOAD -> {
                val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L)
                if (downloadId > 0) {
                    cancelDownload(downloadId)
                }
            }

            ACTION_STOP_SERVICE -> {
                activeJobs.values.forEach { it.cancel() }
                activeJobs.clear()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            else -> {
                startForeground(NOTIFICATION_ID, createForegroundNotification(0, 0))
                serviceScope.launch {
                    processQueue()
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun processQueue() {
        processingMutex.withLock {
            downloadRepository.reconcileCompletedDownloads()
            downloadRepository.requeueInterruptedDownloads()

            while (true) {
                fillAvailableSlots()
                updateForegroundNotification()

                val hasDbWork = downloadRepository.getInFlightCount() > 0
                if (!hasDbWork && activeJobs.isEmpty()) {
                    break
                }

                delay(350)
            }
        }

        stopIfIdle()
    }

    private suspend fun fillAvailableSlots() {
        val availableSlots = DownloadManager.MAX_CONCURRENT_DOWNLOADS - activeJobs.size
        if (availableSlots <= 0) {
            return
        }

        val queued = downloadRepository.getDownloadsForProcessing()
            .filter { it.id !in activeJobs.keys }
            .take(availableSlots)

        queued.forEach { startDownload(it) }
    }

    private fun startDownload(item: DownloadItem) {
        val job = serviceScope.launch {
            val terminalStateReached = AtomicBoolean(false)
            var lastProgressJob: Job? = null

            try {
                downloadRepository.updateStatus(
                    id = item.id,
                    status = DownloadStatus.DOWNLOADING,
                    progress = item.progress.coerceIn(0, 99),
                    clearError = true
                )
                NotificationHelper.showDownloadProgress(this@DownloadService, item.id, item.customFileName, item.progress)

                val result = videoDownloader.downloadVideo(
                    sourceUrl = item.originalUrl,
                    formatSelector = item.downloadUrl,
                    extractorArgs = item.downloadExtractorArgs,
                    strictSelection = item.strictSelection,
                    fileName = item.customFileName
                ) { progress ->
                    if (!terminalStateReached.get()) {
                        lastProgressJob = serviceScope.launch {
                            if (terminalStateReached.get()) {
                                return@launch
                            }

                            downloadRepository.updateProgress(item.id, progress, 0L)
                            NotificationHelper.showDownloadProgress(
                                this@DownloadService,
                                item.id,
                                item.customFileName,
                                progress
                            )
                        }
                    }
                }

                if (result.isSuccess) {
                    val downloadResult = result.getOrThrow()
                    terminalStateReached.set(true)
                    lastProgressJob?.join()
                    downloadRepository.markCompleted(item.id, downloadResult.filePath, downloadResult.fileSize)

                    if (appPreferences.galleryAutoSaveEnabled) {
                        GallerySaver.scanIntoGallery(this@DownloadService, downloadResult.filePath)
                            ?.toString()
                            ?.let { galleryUri ->
                                downloadRepository.markGallerySaved(item.id, galleryUri)
                            }
                    }

                    appPreferences.incrementDownloadCount()
                    NotificationHelper.cancelNotification(this@DownloadService, item.id)
                    NotificationHelper.showDownloadComplete(this@DownloadService, item.id, item.customFileName)
                } else {
                    val error = result.exceptionOrNull() ?: IllegalStateException("Bilinmeyen hata")
                    terminalStateReached.set(true)
                    lastProgressJob?.join()
                    val errorMessage = error.message ?: "Bilinmeyen hata"
                    downloadRepository.markFailed(item.id, errorMessage)
                    NotificationHelper.showDownloadFailed(
                        this@DownloadService,
                        item.id,
                        item.customFileName,
                        errorMessage
                    )
                }
            } catch (e: CancellationException) {
                terminalStateReached.set(true)
                lastProgressJob?.join()
                downloadRepository.markFailed(item.id, "Iptal edildi")
                NotificationHelper.cancelNotification(this@DownloadService, item.id)
            } catch (e: Exception) {
                terminalStateReached.set(true)
                lastProgressJob?.join()
                val errorMessage = e.message ?: "Bilinmeyen hata"
                downloadRepository.markFailed(item.id, errorMessage)
                NotificationHelper.showDownloadFailed(
                    this@DownloadService,
                    item.id,
                    item.customFileName,
                    errorMessage
                )
            } finally {
                activeJobs.remove(item.id)
                updateForegroundNotification()
            }
        }

        activeJobs[item.id] = job
    }

    private fun cancelDownload(downloadId: Long) {
        activeJobs.remove(downloadId)?.cancel()
        serviceScope.launch {
            downloadRepository.markFailed(downloadId, "Iptal edildi")
            NotificationHelper.cancelNotification(this@DownloadService, downloadId)
            updateForegroundNotification()
            stopIfIdle()
        }
    }

    private suspend fun stopIfIdle() {
        if (activeJobs.isEmpty() && downloadRepository.getInFlightCount() == 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Arka plan indirmeleri"
                setShowBadge(false)
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private suspend fun updateForegroundNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            NOTIFICATION_ID,
            createForegroundNotification(
                activeCount = activeJobs.size,
                queuedCount = downloadRepository.getDownloadsForProcessing()
                    .count { it.status == DownloadStatus.PENDING && it.id !in activeJobs.keys }
            )
        )
    }

    private fun createForegroundNotification(activeCount: Int, queuedCount: Int): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "downloads")
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = when {
            activeCount > 0 && queuedCount > 0 -> "$activeCount indiriliyor, $queuedCount bekliyor"
            activeCount > 0 -> "$activeCount aktif indirme"
            queuedCount > 0 -> "$queuedCount kuyrukta"
            else -> "Indirmeler tamamlanmak uzere"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Video Downloader")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
