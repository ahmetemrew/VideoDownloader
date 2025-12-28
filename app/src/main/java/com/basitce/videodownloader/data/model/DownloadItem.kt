package com.basitce.videodownloader.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * İndirme durumu
 */
enum class DownloadStatus {
    PENDING,      // Beklemede
    DOWNLOADING,  // İndiriliyor
    PAUSED,       // Duraklatıldı
    COMPLETED,    // Tamamlandı
    FAILED        // Başarısız
}

/**
 * İndirilen video kaydı (Room Entity)
 */
@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val originalUrl: String,
    val platform: Platform,
    val videoTitle: String,
    val customFileName: String, // Kullanıcının belirlediği dosya adı
    val thumbnailUrl: String?,
    val duration: Long?, // saniye
    val author: String?,
    
    val quality: VideoQuality,
    val downloadUrl: String,
    val filePath: String?, // İndirildikten sonra dolu
    val fileSize: Long?, // bytes
    
    val status: DownloadStatus = DownloadStatus.PENDING,
    val progress: Int = 0, // 0-100
    val errorMessage: String? = null,
    
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
) {
    /**
     * Okunabilir dosya boyutu döndürür
     */
    fun getFormattedSize(): String {
        if (fileSize == null) return ""
        
        return when {
            fileSize < 1024 -> "$fileSize B"
            fileSize < 1024 * 1024 -> String.format("%.1f KB", fileSize / 1024.0)
            fileSize < 1024 * 1024 * 1024 -> String.format("%.1f MB", fileSize / (1024.0 * 1024))
            else -> String.format("%.2f GB", fileSize / (1024.0 * 1024 * 1024))
        }
    }

    /**
     * Okunabilir süre döndürür
     */
    fun getFormattedDuration(): String {
        if (duration == null) return ""
        
        val minutes = duration / 60
        val seconds = duration % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    /**
     * Durum metni döndürür
     */
    fun getStatusText(): String {
        return when (status) {
            DownloadStatus.PENDING -> "Beklemede"
            DownloadStatus.DOWNLOADING -> "%$progress"
            DownloadStatus.PAUSED -> "Duraklatıldı"
            DownloadStatus.COMPLETED -> "Tamamlandı"
            DownloadStatus.FAILED -> "Başarısız"
        }
    }
}
