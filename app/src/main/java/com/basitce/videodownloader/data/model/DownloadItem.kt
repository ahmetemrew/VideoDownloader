package com.basitce.videodownloader.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}

@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val originalUrl: String,
    val platform: Platform,
    val videoTitle: String,
    val customFileName: String,
    val thumbnailUrl: String?,
    val duration: Long?,
    val author: String?,
    val quality: VideoQuality,
    val downloadProfile: DownloadProfile = DownloadProfile.MAX,
    val downloadUrl: String,
    val downloadExtractorArgs: String? = null,
    val strictSelection: Boolean = false,
    val filePath: String?,
    val fileSize: Long?,
    val galleryUri: String? = null,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val progress: Int = 0,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
) {
    fun getFormattedSize(): String {
        if (fileSize == null) return ""

        return when {
            fileSize < 1024 -> "$fileSize B"
            fileSize < 1024 * 1024 -> String.format("%.1f KB", fileSize / 1024.0)
            fileSize < 1024 * 1024 * 1024 -> String.format("%.1f MB", fileSize / (1024.0 * 1024))
            else -> String.format("%.2f GB", fileSize / (1024.0 * 1024 * 1024))
        }
    }

    fun getFormattedDuration(): String {
        if (duration == null) return ""

        val minutes = duration / 60
        val seconds = duration % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    fun getStatusText(): String {
        return when (status) {
            DownloadStatus.PENDING -> "Beklemede"
            DownloadStatus.DOWNLOADING -> "%$progress"
            DownloadStatus.PAUSED -> "Duraklatıldı"
            DownloadStatus.COMPLETED -> "Tamamlandı"
            DownloadStatus.FAILED -> "Başarısız"
        }
    }

    fun isSavedToGallery(): Boolean = !galleryUri.isNullOrBlank()

    fun getDisplayQualityLabel(): String {
        return when (downloadProfile) {
            DownloadProfile.MAX,
            DownloadProfile.MIN,
            DownloadProfile.AUDIO_ONLY -> downloadProfile.label

            else -> quality.resolution
        }
    }
}
