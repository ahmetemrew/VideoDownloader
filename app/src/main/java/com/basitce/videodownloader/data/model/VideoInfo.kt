package com.basitce.videodownloader.data.model

/**
 * API'den alınan video bilgisi
 */
data class VideoInfo(
    val id: String,
    val url: String,
    val platform: Platform,
    val title: String,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val duration: Long? = null, // saniye cinsinden
    val author: String? = null,
    val authorAvatarUrl: String? = null,
    val availableQualities: List<AvailableQuality> = emptyList(),
    val isAudioOnly: Boolean = false
) {
    /**
     * Okunabilir süre döndürür (örn: "3:45")
     */
    fun getFormattedDuration(): String {
        if (duration == null) return ""
        
        val hours = duration / 3600
        val minutes = (duration % 3600) / 60
        val seconds = duration % 60
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    /**
     * En yüksek kaliteyi döndürür
     */
    fun getBestQuality(): AvailableQuality? {
        return availableQualities.minByOrNull { it.quality.priority }
    }

    /**
     * Varsayılan kaliteyi döndürür (720p veya en yakın)
     */
    fun getDefaultQuality(): AvailableQuality? {
        return availableQualities.find { it.quality == VideoQuality.QUALITY_720P }
            ?: availableQualities.minByOrNull { 
                kotlin.math.abs(it.quality.priority - VideoQuality.QUALITY_720P.priority) 
            }
    }
}
