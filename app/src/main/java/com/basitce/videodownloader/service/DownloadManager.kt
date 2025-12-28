package com.basitce.videodownloader.service

import android.content.Context
import com.basitce.videodownloader.data.AppPreferences
import com.basitce.videodownloader.data.model.DownloadItem
import com.basitce.videodownloader.data.model.DownloadStatus
import com.basitce.videodownloader.data.model.VideoQuality
import com.basitce.videodownloader.data.model.Platform
import com.basitce.videodownloader.data.repository.DownloadRepository
import com.basitce.videodownloader.data.scraper.VideoDownloader
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * İndirme kuyruğunu yöneten manager
 */
class DownloadManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: DownloadManager? = null

        fun getInstance(context: Context): DownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloadManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        const val MAX_CONCURRENT_DOWNLOADS = 2
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val videoDownloader = VideoDownloader(context)
    private val downloadRepository = DownloadRepository(context)
    private val appPreferences = AppPreferences(context)
    
    private val downloadQueue = ConcurrentLinkedQueue<QueuedDownload>()
    private val activeDownloads = mutableMapOf<Long, Job>()
    
    private val _queueState = MutableStateFlow<QueueState>(QueueState.Idle)
    val queueState: StateFlow<QueueState> = _queueState

    /**
     * İndirme kuyruğuna ekle
     */
    suspend fun enqueue(
        url: String,
        videoUrl: String,
        platform: Platform,
        title: String,
        thumbnailUrl: String?,
        quality: VideoQuality,
        customFileName: String
    ): Long {
        // DownloadItem oluştur - doğru field isimleriyle
        val downloadItem = DownloadItem(
            originalUrl = url,
            platform = platform,
            videoTitle = title,
            customFileName = customFileName,
            thumbnailUrl = thumbnailUrl,
            duration = null,
            author = null,
            quality = quality,
            downloadUrl = videoUrl,
            filePath = null,
            fileSize = null,
            status = DownloadStatus.PENDING
        )
        val downloadId = downloadRepository.insert(downloadItem)
        
        // Kuyruğa ekle
        downloadQueue.add(QueuedDownload(
            id = downloadId,
            videoUrl = videoUrl,
            fileName = customFileName,
            item = downloadItem.copy(id = downloadId)
        ))
        
        // Kuyruğu işle
        processQueue()
        
        return downloadId
    }

    private fun processQueue() {
        scope.launch {
            while (downloadQueue.isNotEmpty() && activeDownloads.size < MAX_CONCURRENT_DOWNLOADS) {
                val queued = downloadQueue.poll() ?: continue
                startDownload(queued)
            }
            updateQueueState()
        }
    }

    private fun startDownload(queued: QueuedDownload) {
        val job = scope.launch {
            try {
                downloadRepository.updateStatus(queued.id, DownloadStatus.DOWNLOADING)
                NotificationHelper.showDownloadProgress(context, queued.id, queued.fileName, 0)
                
                val result = videoDownloader.downloadVideo(
                    videoUrl = queued.videoUrl,
                    fileName = queued.fileName,
                    onProgress = { progress ->
                        scope.launch {
                            downloadRepository.updateProgress(queued.id, progress, 0)
                            NotificationHelper.showDownloadProgress(context, queued.id, queued.fileName, progress)
                        }
                    }
                )
                
                result.onSuccess { filePath ->
                    downloadRepository.markCompleted(queued.id, filePath, 0)
                    appPreferences.incrementDownloadCount()
                    NotificationHelper.showDownloadComplete(context, queued.id, queued.fileName)
                }.onFailure { error ->
                    val errorMessage = error.message ?: "Bilinmeyen hata"
                    downloadRepository.markFailed(queued.id, errorMessage)
                    NotificationHelper.showDownloadFailed(context, queued.id, queued.fileName, errorMessage)
                }
                
            } catch (e: CancellationException) {
                downloadRepository.updateStatus(queued.id, DownloadStatus.PAUSED)
                NotificationHelper.cancelNotification(context, queued.id)
            } catch (e: Exception) {
                val errorMessage = e.message ?: "Bilinmeyen hata"
                downloadRepository.markFailed(queued.id, errorMessage)
                NotificationHelper.showDownloadFailed(context, queued.id, queued.fileName, errorMessage)
            } finally {
                activeDownloads.remove(queued.id)
                processQueue()
            }
        }
        
        activeDownloads[queued.id] = job
        updateQueueState()
    }

    fun cancelDownload(downloadId: Long) {
        activeDownloads[downloadId]?.cancel()
        activeDownloads.remove(downloadId)
        downloadQueue.removeIf { it.id == downloadId }
        
        scope.launch {
            downloadRepository.updateStatus(downloadId, DownloadStatus.FAILED)
        }
        
        NotificationHelper.cancelNotification(context, downloadId)
        updateQueueState()
    }

    fun cancelAll() {
        activeDownloads.values.forEach { it.cancel() }
        activeDownloads.clear()
        downloadQueue.clear()
        NotificationHelper.cancelAllNotifications(context)
        updateQueueState()
    }

    private fun updateQueueState() {
        _queueState.value = when {
            activeDownloads.isNotEmpty() -> QueueState.Downloading(
                activeCount = activeDownloads.size,
                queuedCount = downloadQueue.size
            )
            downloadQueue.isNotEmpty() -> QueueState.Queued(downloadQueue.size)
            else -> QueueState.Idle
        }
    }

    fun getActiveCount(): Int = activeDownloads.size
    fun getQueuedCount(): Int = downloadQueue.size

    data class QueuedDownload(
        val id: Long,
        val videoUrl: String,
        val fileName: String,
        val item: DownloadItem
    )

    sealed class QueueState {
        object Idle : QueueState()
        data class Queued(val count: Int) : QueueState()
        data class Downloading(val activeCount: Int, val queuedCount: Int) : QueueState()
    }
}
