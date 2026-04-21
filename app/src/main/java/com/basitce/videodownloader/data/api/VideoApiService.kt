package com.basitce.videodownloader.data.api

import com.basitce.videodownloader.data.LinkResolver
import com.basitce.videodownloader.data.model.AvailableQuality
import com.basitce.videodownloader.data.model.Platform
import com.basitce.videodownloader.data.model.VideoInfo
import com.basitce.videodownloader.data.model.VideoQuality
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Video bilgisi almak için kullanılan örnek API servisi.
 * Gerçek API entegrasyonu için bu sınıf değiştirilecektir.
 */
object VideoApiService {

    /**
     * URL'den video bilgisi alır.
     */
    suspend fun fetchVideoInfo(url: String): Result<VideoInfo> {
        val resolved = LinkResolver.resolve(url)

        if (!resolved.isValid) {
            return Result.failure(Exception("Desteklenmeyen veya geçersiz URL"))
        }

        delay(Random.nextLong(500, 1500))

        val videoInfo = generateMockVideoInfo(resolved)
        return Result.success(videoInfo)
    }

    /**
     * Test ve demo için örnek video bilgisi oluşturur.
     */
    private fun generateMockVideoInfo(resolved: LinkResolver.ResolveResult): VideoInfo {
        val platform = resolved.platform
        val videoId = resolved.videoId ?: "unknown_${System.currentTimeMillis()}"

        val title = when (platform) {
            Platform.INSTAGRAM -> "Instagram videosu #${videoId.take(6)}"
            Platform.TIKTOK -> "TikTok videosu"
            Platform.TWITTER -> "X gönderi videosu"
            Platform.YOUTUBE -> "YouTube videosu"
            Platform.FACEBOOK -> "Facebook Watch videosu"
            Platform.PINTEREST -> "Pinterest video pini"
            Platform.UNKNOWN -> "Video"
        }

        val author = when (platform) {
            Platform.INSTAGRAM -> "@instagram_kullanici"
            Platform.TIKTOK -> "@tiktok_uretici"
            Platform.TWITTER -> "@x_kullanici"
            Platform.YOUTUBE -> "YouTube kanalı"
            Platform.FACEBOOK -> "Facebook sayfası"
            Platform.PINTEREST -> "Pinterest profili"
            Platform.UNKNOWN -> "Bilinmiyor"
        }

        val duration = Random.nextLong(10, 600)
        val availableQualities = generateMockQualities(platform)

        return VideoInfo(
            id = videoId,
            url = resolved.cleanUrl,
            platform = platform,
            title = title,
            description = "Bu video ${platform.displayName} platformundan alındı.",
            thumbnailUrl = "https://picsum.photos/seed/$videoId/640/360",
            duration = duration,
            author = author,
            authorAvatarUrl = "https://picsum.photos/seed/${author}/100/100",
            availableQualities = availableQualities
        )
    }

    /**
     * Platform bazlı mevcut kalite seçenekleri oluşturur.
     */
    private fun generateMockQualities(platform: Platform): List<AvailableQuality> {
        val baseQualities = when (platform) {
            Platform.YOUTUBE -> listOf(
                VideoQuality.QUALITY_4K,
                VideoQuality.QUALITY_1080P,
                VideoQuality.QUALITY_720P,
                VideoQuality.QUALITY_480P,
                VideoQuality.QUALITY_360P
            )
            Platform.TIKTOK, Platform.INSTAGRAM -> listOf(
                VideoQuality.QUALITY_1080P,
                VideoQuality.QUALITY_720P,
                VideoQuality.QUALITY_480P
            )
            Platform.TWITTER, Platform.FACEBOOK, Platform.PINTEREST -> listOf(
                VideoQuality.QUALITY_720P,
                VideoQuality.QUALITY_480P,
                VideoQuality.QUALITY_360P
            )
            Platform.UNKNOWN -> listOf(VideoQuality.QUALITY_720P)
        }

        return baseQualities.map { quality ->
            val baseSize = when (quality) {
                VideoQuality.QUALITY_4K -> 500_000_000L
                VideoQuality.QUALITY_1440P -> 250_000_000L
                VideoQuality.QUALITY_1080P -> 100_000_000L
                VideoQuality.QUALITY_720P -> 50_000_000L
                VideoQuality.QUALITY_480P -> 25_000_000L
                VideoQuality.QUALITY_360P -> 15_000_000L
                VideoQuality.QUALITY_AUDIO_ONLY -> 5_000_000L
            }

            val fileSize = baseSize + Random.nextLong(-baseSize / 4, baseSize / 4)

            AvailableQuality(
                quality = quality,
                fileSize = fileSize,
                url = "https://example.com/download/${quality.resolution}",
                extractorArgs = null,
                strictSelection = false
            )
        }
    }
}
