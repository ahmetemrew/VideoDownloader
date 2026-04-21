package com.basitce.videodownloader.service

import android.content.Context
import androidx.core.content.ContextCompat
import com.basitce.videodownloader.data.AppPreferences
import com.basitce.videodownloader.data.model.DownloadItem
import com.basitce.videodownloader.data.model.DownloadProfile
import com.basitce.videodownloader.data.model.DownloadStatus
import com.basitce.videodownloader.data.model.Platform
import com.basitce.videodownloader.data.model.VideoQuality
import com.basitce.videodownloader.data.repository.DownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DownloadManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: DownloadManager? = null

        const val MAX_CONCURRENT_DOWNLOADS = 2

        fun getInstance(context: Context): DownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloadManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val downloadRepository = DownloadRepository(context)
    private val appPreferences = AppPreferences(context)

    init {
        NotificationHelper.createNotificationChannels(context)
        NotificationHelper.cancelStaleProgressNotifications(context)

        scope.launch {
            downloadRepository.reconcileCompletedDownloads()
            downloadRepository.requeueInterruptedDownloads()
            if (downloadRepository.getInFlightCount() > 0) {
                ensureServiceRunning()
            }
        }
    }

    suspend fun enqueue(
        url: String,
        downloadSelector: String,
        downloadExtractorArgs: String?,
        strictSelection: Boolean,
        platform: Platform,
        title: String,
        thumbnailUrl: String?,
        quality: VideoQuality,
        customFileName: String,
        downloadProfile: DownloadProfile = DownloadProfile.fromVideoQuality(quality)
    ): Long {
        val downloadItem = DownloadItem(
            originalUrl = url,
            platform = platform,
            videoTitle = title,
            customFileName = customFileName,
            thumbnailUrl = thumbnailUrl,
            duration = null,
            author = null,
            quality = quality,
            downloadProfile = downloadProfile,
            downloadUrl = downloadSelector,
            downloadExtractorArgs = downloadExtractorArgs,
            strictSelection = strictSelection,
            filePath = null,
            fileSize = null,
            galleryUri = null,
            status = DownloadStatus.PENDING
        )

        val downloadId = downloadRepository.insert(downloadItem)
        ensureServiceRunning()
        return downloadId
    }

    suspend fun enqueueBatch(requests: List<QueuedDownloadRequest>): List<Long> {
        val ids = mutableListOf<Long>()
        requests.forEach { request ->
            val id = downloadRepository.insert(
                DownloadItem(
                    originalUrl = request.sourceUrl,
                    platform = request.platform,
                    videoTitle = request.title,
                    customFileName = request.fileName,
                    thumbnailUrl = request.thumbnailUrl,
                    duration = null,
                    author = null,
                    quality = request.quality,
                    downloadProfile = request.downloadProfile,
                    downloadUrl = request.downloadSelector,
                    downloadExtractorArgs = request.downloadExtractorArgs,
                    strictSelection = request.strictSelection,
                    filePath = null,
                    fileSize = null,
                    galleryUri = null,
                    status = DownloadStatus.PENDING
                )
            )
            ids += id
        }

        if (ids.isNotEmpty()) {
            ensureServiceRunning()
        }
        return ids
    }

    fun cancelDownload(downloadId: Long) {
        scope.launch {
            downloadRepository.markFailed(downloadId, "Iptal edildi")
        }
        DownloadService.cancelDownload(context, downloadId)
        NotificationHelper.cancelNotification(context, downloadId)
    }

    fun cancelAll() {
        DownloadService.stopService(context)
        NotificationHelper.cancelAllNotifications(context)
    }

    suspend fun getActiveCount(): Int {
        return downloadRepository.getDownloadsForProcessing()
            .count { it.status == DownloadStatus.DOWNLOADING }
    }

    suspend fun getQueuedCount(): Int {
        return downloadRepository.getDownloadsForProcessing()
            .count { it.status == DownloadStatus.PENDING }
    }

    private fun ensureServiceRunning() {
        ContextCompat.startForegroundService(
            context,
            DownloadService.createProcessQueueIntent(context)
        )
    }

    data class QueuedDownloadRequest(
        val sourceUrl: String,
        val downloadSelector: String,
        val downloadExtractorArgs: String? = null,
        val strictSelection: Boolean = false,
        val platform: Platform,
        val title: String,
        val thumbnailUrl: String?,
        val quality: VideoQuality,
        val downloadProfile: DownloadProfile,
        val fileName: String
    )
}
