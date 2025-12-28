package com.basitce.videodownloader.data.db

import androidx.room.*
import com.basitce.videodownloader.data.model.DownloadItem
import com.basitce.videodownloader.data.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    
    /**
     * Tüm indirmeleri tarihe göre sıralı olarak alır (en yeni önce)
     */
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadItem>>

    /**
     * Belirli durumdaki indirmeleri alır
     */
    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY createdAt DESC")
    fun getDownloadsByStatus(status: DownloadStatus): Flow<List<DownloadItem>>

    /**
     * Tamamlanan indirmeleri alır
     */
    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY completedAt DESC")
    fun getCompletedDownloads(status: DownloadStatus = DownloadStatus.COMPLETED): Flow<List<DownloadItem>>

    /**
     * Aktif indirmeleri alır (bekleyen veya devam eden)
     */
    @Query("SELECT * FROM downloads WHERE status IN (:statuses) ORDER BY createdAt DESC")
    fun getActiveDownloads(
        statuses: List<DownloadStatus> = listOf(DownloadStatus.PENDING, DownloadStatus.DOWNLOADING)
    ): Flow<List<DownloadItem>>

    /**
     * ID ile indirme alır
     */
    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadById(id: Long): DownloadItem?

    /**
     * Yeni indirme ekler
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadItem): Long

    /**
     * İndirme günceller
     */
    @Update
    suspend fun update(download: DownloadItem)

    /**
     * İndirme durumunu günceller
     */
    @Query("UPDATE downloads SET status = :status, progress = :progress WHERE id = :id")
    suspend fun updateStatus(id: Long, status: DownloadStatus, progress: Int)

    /**
     * İndirme tamamlandığında günceller
     */
    @Query("UPDATE downloads SET status = :status, progress = 100, filePath = :filePath, fileSize = :fileSize, completedAt = :completedAt WHERE id = :id")
    suspend fun markCompleted(
        id: Long,
        status: DownloadStatus = DownloadStatus.COMPLETED,
        filePath: String,
        fileSize: Long,
        completedAt: Long = System.currentTimeMillis()
    )

    /**
     * İndirme başarısız olduğunda günceller
     */
    @Query("UPDATE downloads SET status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun markFailed(
        id: Long,
        status: DownloadStatus = DownloadStatus.FAILED,
        errorMessage: String
    )

    /**
     * Dosya adını günceller
     */
    @Query("UPDATE downloads SET customFileName = :newName WHERE id = :id")
    suspend fun updateFileName(id: Long, newName: String)

    /**
     * İndirme siler
     */
    @Delete
    suspend fun delete(download: DownloadItem)

    /**
     * ID ile indirme siler
     */
    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Tamamlanan tüm indirmeleri siler
     */
    @Query("DELETE FROM downloads WHERE status = :status")
    suspend fun deleteAllCompleted(status: DownloadStatus = DownloadStatus.COMPLETED)

    /**
     * Başarısız tüm indirmeleri siler
     */
    @Query("DELETE FROM downloads WHERE status = :status")
    suspend fun deleteAllFailed(status: DownloadStatus = DownloadStatus.FAILED)

    /**
     * İndirme sayısını alır
     */
    @Query("SELECT COUNT(*) FROM downloads")
    suspend fun getDownloadCount(): Int

    /**
     * Platform bazlı indirme sayısı
     */
    @Query("SELECT COUNT(*) FROM downloads WHERE platform = :platform")
    suspend fun getDownloadCountByPlatform(platform: String): Int
}
