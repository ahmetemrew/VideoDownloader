package com.basitce.videodownloader.data.repository

import android.content.Context
import com.basitce.videodownloader.data.db.AppDatabase
import com.basitce.videodownloader.data.model.DownloadItem
import com.basitce.videodownloader.data.model.DownloadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

class DownloadRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).downloadDao()

    fun getAllDownloads(): Flow<List<DownloadItem>> = dao.getAllDownloads()

    fun getActiveDownloads(): Flow<List<DownloadItem>> = dao.getActiveDownloads()

    fun getCompletedDownloads(): Flow<List<DownloadItem>> = dao.getCompletedDownloads()

    fun getFailedDownloads(): Flow<List<DownloadItem>> = dao.getDownloadsByStatus(DownloadStatus.FAILED)

    fun observeDownloadsByIds(ids: List<Long>): Flow<List<DownloadItem>> {
        return if (ids.isEmpty()) {
            flowOf(emptyList())
        } else {
            dao.observeDownloadsByIds(ids)
        }
    }

    suspend fun insert(item: DownloadItem): Long = withContext(Dispatchers.IO) {
        dao.insert(item)
    }

    suspend fun update(item: DownloadItem) = withContext(Dispatchers.IO) {
        dao.update(item)
    }

    suspend fun updateStatus(
        id: Long,
        status: DownloadStatus,
        progress: Int = 0,
        clearError: Boolean = false
    ) = withContext(Dispatchers.IO) {
        dao.updateStatus(id, status, progress, clearError)
    }

    suspend fun updateProgress(id: Long, progress: Int, downloadedBytes: Long) = withContext(Dispatchers.IO) {
        dao.updateProgressIfActive(
            id = id,
            status = DownloadStatus.DOWNLOADING,
            progress = progress,
            terminalStatuses = listOf(DownloadStatus.COMPLETED, DownloadStatus.FAILED)
        )
    }

    suspend fun markCompleted(id: Long, filePath: String, fileSize: Long) = withContext(Dispatchers.IO) {
        dao.markCompleted(id, DownloadStatus.COMPLETED, filePath, fileSize)
    }

    suspend fun markFailed(id: Long, errorMessage: String) = withContext(Dispatchers.IO) {
        dao.markFailed(id, DownloadStatus.FAILED, errorMessage)
    }

    suspend fun markGallerySaved(id: Long, galleryUri: String) = withContext(Dispatchers.IO) {
        dao.updateGalleryUri(id, galleryUri)
    }

    suspend fun delete(item: DownloadItem) = withContext(Dispatchers.IO) {
        dao.delete(item)
    }

    suspend fun getById(id: Long): DownloadItem? = withContext(Dispatchers.IO) {
        dao.getDownloadById(id)
    }

    suspend fun getByIds(ids: List<Long>): List<DownloadItem> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) emptyList() else dao.getDownloadsByIds(ids)
    }

    suspend fun getDownloadsForProcessing(): List<DownloadItem> = withContext(Dispatchers.IO) {
        dao.getDownloadsForProcessing(listOf(DownloadStatus.PENDING, DownloadStatus.DOWNLOADING))
    }

    suspend fun getInFlightCount(): Int = withContext(Dispatchers.IO) {
        dao.countByStatuses(listOf(DownloadStatus.PENDING, DownloadStatus.DOWNLOADING))
    }

    suspend fun getCompletedCount(): Int = withContext(Dispatchers.IO) {
        dao.getDownloadCount()
    }

    suspend fun clearFailed() = withContext(Dispatchers.IO) {
        dao.deleteAllFailed()
    }

    suspend fun clearCompleted() = withContext(Dispatchers.IO) {
        dao.deleteAllCompleted()
    }

    suspend fun reconcileCompletedDownloads() = withContext(Dispatchers.IO) {
        dao.reconcileCompletedStatuses()
    }

    suspend fun requeueInterruptedDownloads() = withContext(Dispatchers.IO) {
        dao.requeueInterruptedDownloads()
    }
}
