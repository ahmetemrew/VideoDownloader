package com.basitce.videodownloader.data.model

object DownloadSelectorFactory {

    fun selectorForProfile(platform: Platform, profile: DownloadProfile): String {
        return when (profile) {
            DownloadProfile.MAX -> {
                if (platform == Platform.YOUTUBE) {
                    "bestvideo*+bestaudio/best[ext=mp4]/best"
                } else {
                    "best"
                }
            }

            DownloadProfile.QUALITY_2160P -> selectorForHeight(platform, 2160)
            DownloadProfile.QUALITY_1440P -> selectorForHeight(platform, 1440)
            DownloadProfile.QUALITY_1080P -> selectorForHeight(platform, 1080)
            DownloadProfile.QUALITY_720P -> selectorForHeight(platform, 720)
            DownloadProfile.QUALITY_480P -> selectorForHeight(platform, 480)
            DownloadProfile.QUALITY_360P -> selectorForHeight(platform, 360)
            DownloadProfile.AUDIO_ONLY -> "bestaudio[ext=m4a]/bestaudio/best"
            DownloadProfile.MIN -> "worst/best"
        }
    }

    fun fallbackQuality(profile: DownloadProfile): VideoQuality {
        return profile.toVideoQualityOrNull()
            ?: when (profile) {
                DownloadProfile.MIN -> VideoQuality.QUALITY_360P
                DownloadProfile.AUDIO_ONLY -> VideoQuality.QUALITY_AUDIO_ONLY
                else -> VideoQuality.QUALITY_1080P
            }
    }

    private fun selectorForHeight(platform: Platform, height: Int): String {
        return when (platform) {
            Platform.YOUTUBE -> (
                "bestvideo*[height<=$height]+bestaudio/" +
                    "best*[height<=$height][ext=mp4]/" +
                    "best*[height<=$height]/best"
                )

            else -> "best[height<=$height]/best"
        }
    }
}
