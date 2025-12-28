package com.basitce.videodownloader.data.model

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.basitce.videodownloader.R

/**
 * Desteklenen sosyal medya platformları
 */
enum class Platform(
    val displayName: String,
    val shortName: String,
    @ColorRes val colorRes: Int,
    @DrawableRes val iconRes: Int,
    val description: String
) {
    INSTAGRAM(
        displayName = "Instagram",
        shortName = "IG",
        colorRes = R.color.instagram_gradient_end,
        iconRes = R.drawable.ic_instagram,
        description = "Reels, Post, Story, IGTV"
    ),
    TIKTOK(
        displayName = "TikTok",
        shortName = "TT",
        colorRes = R.color.tiktok,
        iconRes = R.drawable.ic_tiktok,
        description = "Videolar (Watermark'sız)"
    ),
    TWITTER(
        displayName = "X (Twitter)",
        shortName = "X",
        colorRes = R.color.twitter,
        iconRes = R.drawable.ic_twitter,
        description = "Tweet videoları, GIF'ler"
    ),
    YOUTUBE(
        displayName = "YouTube",
        shortName = "YT",
        colorRes = R.color.youtube,
        iconRes = R.drawable.ic_youtube,
        description = "Video, Shorts"
    ),
    FACEBOOK(
        displayName = "Facebook",
        shortName = "FB",
        colorRes = R.color.facebook,
        iconRes = R.drawable.ic_facebook,
        description = "Video, Reels, Watch"
    ),
    PINTEREST(
        displayName = "Pinterest",
        shortName = "Pin",
        colorRes = R.color.pinterest,
        iconRes = R.drawable.ic_pinterest,
        description = "Video Pinler"
    ),
    UNKNOWN(
        displayName = "Bilinmeyen",
        shortName = "?",
        colorRes = R.color.unknown_platform,
        iconRes = R.drawable.ic_link,
        description = "Desteklenmeyen platform"
    );

    companion object {
        /**
         * Aktif olarak desteklenen platformları döndürür (UNKNOWN hariç)
         */
        fun getSupportedPlatforms(): List<Platform> = entries.filter { it != UNKNOWN }
    }
}
