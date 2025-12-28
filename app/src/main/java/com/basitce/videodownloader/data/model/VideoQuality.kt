package com.basitce.videodownloader.data.model

/**
 * Video kalite seçenekleri
 */
enum class VideoQuality(
    val label: String,
    val resolution: String,
    val priority: Int // Düşük = daha iyi kalite
) {
    QUALITY_4K("4K Ultra HD", "2160p", 1),
    QUALITY_1440P("2K QHD", "1440p", 2),
    QUALITY_1080P("Full HD", "1080p", 3),
    QUALITY_720P("HD", "720p", 4),
    QUALITY_480P("SD", "480p", 5),
    QUALITY_360P("Düşük", "360p", 6),
    QUALITY_AUDIO_ONLY("Sadece Ses", "MP3", 10);

    companion object {
        fun fromResolution(resolution: String): VideoQuality {
            return entries.find { it.resolution.equals(resolution, ignoreCase = true) }
                ?: QUALITY_720P
        }

        fun getVideoQualities(): List<VideoQuality> = entries.filter { it != QUALITY_AUDIO_ONLY }
    }
}

/**
 * Belirli bir video için mevcut kalite seçeneği
 */
data class AvailableQuality(
    val quality: VideoQuality,
    val fileSize: Long? = null, // bytes cinsinden
    val url: String
) {
    /**
     * Okunabilir dosya boyutu döndürür (örn: "12.5 MB")
     */
    fun getFormattedSize(): String {
        if (fileSize == null) return "Bilinmiyor"
        
        return when {
            fileSize < 1024 -> "$fileSize B"
            fileSize < 1024 * 1024 -> String.format("%.1f KB", fileSize / 1024.0)
            fileSize < 1024 * 1024 * 1024 -> String.format("%.1f MB", fileSize / (1024.0 * 1024))
            else -> String.format("%.2f GB", fileSize / (1024.0 * 1024 * 1024))
        }
    }
}
