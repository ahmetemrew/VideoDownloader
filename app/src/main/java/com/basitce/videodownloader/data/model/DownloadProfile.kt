package com.basitce.videodownloader.data.model

enum class DownloadProfile(
    val label: String,
    val summary: String
) {
    MAX("En yüksek kalite", "Mevcut en iyi kalite"),
    QUALITY_2160P("2160p", "4K Ultra HD"),
    QUALITY_1440P("1440p", "2K QHD"),
    QUALITY_1080P("1080p", "Full HD"),
    QUALITY_720P("720p", "HD"),
    QUALITY_480P("480p", "SD"),
    QUALITY_360P("360p", "Düşük"),
    AUDIO_ONLY("Sadece ses", "Yalnızca ses indir"),
    MIN("En düşük kalite", "Mevcut en düşük kalite");

    fun toVideoQualityOrNull(): VideoQuality? {
        return when (this) {
            QUALITY_2160P -> VideoQuality.QUALITY_4K
            QUALITY_1440P -> VideoQuality.QUALITY_1440P
            QUALITY_1080P -> VideoQuality.QUALITY_1080P
            QUALITY_720P -> VideoQuality.QUALITY_720P
            QUALITY_480P -> VideoQuality.QUALITY_480P
            QUALITY_360P -> VideoQuality.QUALITY_360P
            AUDIO_ONLY -> VideoQuality.QUALITY_AUDIO_ONLY
            MAX,
            MIN -> null
        }
    }

    companion object {
        fun fromVideoQuality(quality: VideoQuality): DownloadProfile {
            return when (quality) {
                VideoQuality.QUALITY_4K -> QUALITY_2160P
                VideoQuality.QUALITY_1440P -> QUALITY_1440P
                VideoQuality.QUALITY_1080P -> QUALITY_1080P
                VideoQuality.QUALITY_720P -> QUALITY_720P
                VideoQuality.QUALITY_480P -> QUALITY_480P
                VideoQuality.QUALITY_360P -> QUALITY_360P
                VideoQuality.QUALITY_AUDIO_ONLY -> AUDIO_ONLY
            }
        }
    }
}
