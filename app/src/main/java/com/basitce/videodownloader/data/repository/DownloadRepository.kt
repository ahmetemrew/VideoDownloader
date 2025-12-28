package com.basitce.videodownloader.data.repository

import android.content.Context
import com.basitce.videodownloader.data.db.AppDatabase
import com.basitce.videodownloader.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * İndirme işlemlerini yöneten repository
 */
class DownloadRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).downloadDao()

    // === Flow Queries ===
    
    fun getAllDownloads(): Flow<List<DownloadItem>> = dao.getAllDownloads()
    
    fun getActiveDownloads(): Flow<List<DownloadItem>> = dao.getActiveDownloads()
    
    fun getCompletedDownloads(): Flow<List<DownloadItem>> = dao.getDownloadsByStatus(DownloadStatus.COMPLETED)
    
    fun getFailedDownloads(): Flow<List<DownloadItem>> = dao.getDownloadsByStatus(DownloadStatus.FAILED)

    // === CRUD Operations ===

    /**
     * Yeni indirme ekle
     */
    suspend fun insert(item: DownloadItem): Long = withContext(Dispatchers.IO) {
        dao.insert(item)
    }

    /**
     * İndirme durumunu güncelle
     */
    suspend fun updateStatus(id: Long, status: DownloadStatus) = withContext(Dispatchers.IO) {
        dao.updateStatus(id, status, 0)
    }

    /**
     * İndirme ilerlemesini güncelle
     */
    suspend fun updateProgress(id: Long, progress: Int, downloadedBytes: Long) = withContext(Dispatchers.IO) {
        dao.updateStatus(id, DownloadStatus.DOWNLOADING, progress)
    }

    /**
     * İndirme tamamlandı olarak işaretle
     */
    suspend fun markCompleted(
        id: Long, 
        filePath: String, 
        fileSize: Long,
        duration: Long? = null
    ) = withContext(Dispatchers.IO) {
        dao.markCompleted(id, DownloadStatus.COMPLETED, filePath, fileSize)
    }

    /**
     * İndirme başarısız olarak işaretle
     */
    suspend fun markFailed(id: Long, errorMessage: String) = withContext(Dispatchers.IO) {
        dao.markFailed(id, DownloadStatus.FAILED, errorMessage)
    }

    /**
     * İndirmeyi sil
     */
    suspend fun delete(item: DownloadItem) = withContext(Dispatchers.IO) {
        dao.delete(item)
    }

    /**
     * ID ile indirme bul
     */
    suspend fun getById(id: Long): DownloadItem? = withContext(Dispatchers.IO) {
        dao.getDownloadById(id)
    }

    /**
     * Tamamlanan indirme sayısı
     */
    suspend fun getCompletedCount(): Int = withContext(Dispatchers.IO) {
        dao.getDownloadCount()
    }

    /**
     * Tüm başarısız indirmeleri temizle
     */
    suspend fun clearFailed() = withContext(Dispatchers.IO) {
        dao.deleteAllFailed()
    }

    /**
     * Tüm indirmeleri temizle
     */
    suspend fun clearCompleted() = withContext(Dispatchers.IO) {
        dao.deleteAllCompleted()
    }
}
