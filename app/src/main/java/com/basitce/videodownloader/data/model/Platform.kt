package com.basitce.videodownloader.data.model

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.basitce.videodownloader.R

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
        description = "Reels, gönderi, hikâye ve IGTV"
    ),
    TIKTOK(
        displayName = "TikTok",
        shortName = "TT",
        colorRes = R.color.tiktok,
        iconRes = R.drawable.ic_tiktok,
        description = "Filigransız video"
    ),
    TWITTER(
        displayName = "X",
        shortName = "X",
        colorRes = R.color.twitter,
        iconRes = R.drawable.ic_twitter,
        description = "Gönderi videoları ve GIF içerikleri"
    ),
    YOUTUBE(
        displayName = "YouTube",
        shortName = "YT",
        colorRes = R.color.youtube,
        iconRes = R.drawable.ic_youtube,
        description = "Videolar ve Shorts içerikleri"
    ),
    FACEBOOK(
        displayName = "Facebook",
        shortName = "FB",
        colorRes = R.color.facebook,
        iconRes = R.drawable.ic_facebook,
        description = "Video, Reels ve Watch içerikleri"
    ),
    PINTEREST(
        displayName = "Pinterest",
        shortName = "Pin",
        colorRes = R.color.pinterest,
        iconRes = R.drawable.ic_pinterest,
        description = "Video pinleri"
    ),
    UNKNOWN(
        displayName = "Diğer",
        shortName = "OT",
        colorRes = R.color.unknown_platform,
        iconRes = R.drawable.ic_link,
        description = "Genel bağlantı"
    );

    companion object {
        fun getSupportedPlatforms(): List<Platform> = entries.filter { it != UNKNOWN }
    }
}
