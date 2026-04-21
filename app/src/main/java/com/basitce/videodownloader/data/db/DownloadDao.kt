package com.basitce.videodownloader.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.basitce.videodownloader.data.model.DownloadItem
import com.basitce.videodownloader.data.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY createdAt DESC")
    fun getDownloadsByStatus(status: DownloadStatus): Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY completedAt DESC, createdAt DESC")
    fun getCompletedDownloads(status: DownloadStatus = DownloadStatus.COMPLETED): Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE status IN (:statuses) ORDER BY createdAt DESC")
    fun getActiveDownloads(
        statuses: List<DownloadStatus> = listOf(DownloadStatus.PENDING, DownloadStatus.DOWNLOADING)
    ): Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE id IN (:ids) ORDER BY createdAt DESC")
    fun observeDownloadsByIds(ids: List<Long>): Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE id IN (:ids) ORDER BY createdAt DESC")
    suspend fun getDownloadsByIds(ids: List<Long>): List<DownloadItem>

    @Query("SELECT * FROM downloads WHERE status IN (:statuses) ORDER BY createdAt ASC")
    suspend fun getDownloadsForProcessing(statuses: List<DownloadStatus>): List<DownloadItem>

    @Query("SELECT COUNT(*) FROM downloads WHERE status IN (:statuses)")
    suspend fun countByStatuses(statuses: List<DownloadStatus>): Int

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadById(id: Long): DownloadItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadItem): Long

    @Update
    suspend fun update(download: DownloadItem)

    @Query(
        """
        UPDATE downloads
        SET status = :status,
            progress = :progress,
            errorMessage = CASE WHEN :clearError THEN NULL ELSE errorMessage END
        WHERE id = :id
        """
    )
    suspend fun updateStatus(
        id: Long,
        status: DownloadStatus,
        progress: Int,
        clearError: Boolean = false
    )

    @Query(
        """
        UPDATE downloads
        SET status = :status,
            progress = :progress
        WHERE id = :id
          AND status NOT IN (:terminalStatuses)
        """
    )
    suspend fun updateProgressIfActive(
        id: Long,
        status: DownloadStatus,
        progress: Int,
        terminalStatuses: List<DownloadStatus>
    )

    @Query(
        """
        UPDATE downloads
        SET status = :status,
            progress = 100,
            filePath = :filePath,
            fileSize = :fileSize,
            completedAt = :completedAt,
            errorMessage = NULL
        WHERE id = :id
        """
    )
    suspend fun markCompleted(
        id: Long,
        status: DownloadStatus = DownloadStatus.COMPLETED,
        filePath: String,
        fileSize: Long,
        completedAt: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE downloads
        SET status = :status,
            errorMessage = :errorMessage,
            progress = CASE WHEN progress >= 100 THEN 99 ELSE progress END
        WHERE id = :id
        """
    )
    suspend fun markFailed(
        id: Long,
        status: DownloadStatus = DownloadStatus.FAILED,
        errorMessage: String
    )

    @Query(
        """
        UPDATE downloads
        SET status = :completedStatus
        WHERE status = :downloadingStatus
          AND progress >= 100
          AND filePath IS NOT NULL
        """
    )
    suspend fun reconcileCompletedStatuses(
        completedStatus: DownloadStatus = DownloadStatus.COMPLETED,
        downloadingStatus: DownloadStatus = DownloadStatus.DOWNLOADING
    )

    @Query(
        """
        UPDATE downloads
        SET status = :pendingStatus,
            progress = CASE WHEN progress >= 100 THEN 0 ELSE progress END
        WHERE status = :downloadingStatus
          AND filePath IS NULL
        """
    )
    suspend fun requeueInterruptedDownloads(
        pendingStatus: DownloadStatus = DownloadStatus.PENDING,
        downloadingStatus: DownloadStatus = DownloadStatus.DOWNLOADING
    )

    @Query("UPDATE downloads SET customFileName = :newName WHERE id = :id")
    suspend fun updateFileName(id: Long, newName: String)

    @Query("UPDATE downloads SET galleryUri = :galleryUri WHERE id = :id")
    suspend fun updateGalleryUri(id: Long, galleryUri: String)

    @Delete
    suspend fun delete(download: DownloadItem)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM downloads WHERE status = :status")
    suspend fun deleteAllCompleted(status: DownloadStatus = DownloadStatus.COMPLETED)

    @Query("DELETE FROM downloads WHERE status = :status")
    suspend fun deleteAllFailed(status: DownloadStatus = DownloadStatus.FAILED)

    @Query("SELECT COUNT(*) FROM downloads")
    suspend fun getDownloadCount(): Int

    @Query("SELECT COUNT(*) FROM downloads WHERE platform = :platform")
    suspend fun getDownloadCountByPlatform(platform: String): Int
}
