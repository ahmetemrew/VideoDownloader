package com.basitce.videodownloader.data.api

import com.basitce.videodownloader.data.LinkResolver
import com.basitce.videodownloader.data.model.*
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Video bilgisi almak için Mock API servisi
 * Gerçek API entegrasyonu için bu sınıf değiştirilecek
 */
object VideoApiService {

    /**
     * URL'den video bilgisi alır
     * @return VideoInfo veya null (başarısız olursa)
     */
    suspend fun fetchVideoInfo(url: String): Result<VideoInfo> {
        // URL'yi analiz et
        val resolved = LinkResolver.resolve(url)
        
        if (!resolved.isValid) {
            return Result.failure(Exception("Desteklenmeyen veya geçersiz URL"))
        }

        // Simüle edilmiş API gecikmesi
        delay(Random.nextLong(500, 1500))

        // Mock video verisi oluştur
        val videoInfo = generateMockVideoInfo(resolved)
        return Result.success(videoInfo)
    }

    /**
     * Test ve demo için mock video bilgisi oluşturur
     */
    private fun generateMockVideoInfo(resolved: LinkResolver.ResolveResult): VideoInfo {
        val platform = resolved.platform
        val videoId = resolved.videoId ?: "unknown_${System.currentTimeMillis()}"
        
        // Platform bazlı örnek başlıklar
        val title = when (platform) {
            Platform.INSTAGRAM -> "Instagram Video #${videoId.take(6)}"
            Platform.TIKTOK -> "TikTok - Viral Video 🔥"
            Platform.TWITTER -> "X/Twitter Video Post"
            Platform.YOUTUBE -> "YouTube Video - Amazing Content"
            Platform.FACEBOOK -> "Facebook Watch Video"
            Platform.PINTEREST -> "Pinterest Video Pin"
            Platform.UNKNOWN -> "Video"
        }

        // Platform bazlı örnek kullanıcı adları
        val author = when (platform) {
            Platform.INSTAGRAM -> "@instagram_user"
            Platform.TIKTOK -> "@tiktok_creator"
            Platform.TWITTER -> "@twitter_user"
            Platform.YOUTUBE -> "YouTube Channel"
            Platform.FACEBOOK -> "Facebook Page"
            Platform.PINTEREST -> "Pinterest Pinner"
            Platform.UNKNOWN -> "Unknown"
        }

        // Rastgele süre (10 saniye - 10 dakika)
        val duration = Random.nextLong(10, 600)

        // Mevcut kalite seçenekleri oluştur
        val availableQualities = generateMockQualities(platform)

        return VideoInfo(
            id = videoId,
            url = resolved.cleanUrl,
            platform = platform,
            title = title,
            description = "Bu video $platform platformundan indirildi.",
            thumbnailUrl = "https://picsum.photos/seed/$videoId/640/360", // Placeholder görsel
            duration = duration,
            author = author,
            authorAvatarUrl = "https://picsum.photos/seed/${author}/100/100",
            availableQualities = availableQualities
        )
    }

    /**
     * Platform bazlı mevcut kalite seçenekleri oluşturur
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
            // Kaliteye göre tahmini dosya boyutu
            val baseSize = when (quality) {
                VideoQuality.QUALITY_4K -> 500_000_000L // ~500MB
                VideoQuality.QUALITY_1440P -> 250_000_000L
                VideoQuality.QUALITY_1080P -> 100_000_000L // ~100MB
                VideoQuality.QUALITY_720P -> 50_000_000L // ~50MB
                VideoQuality.QUALITY_480P -> 25_000_000L
                VideoQuality.QUALITY_360P -> 15_000_000L
                VideoQuality.QUALITY_AUDIO_ONLY -> 5_000_000L
            }
            
            // Biraz rastgelelik ekle
            val fileSize = baseSize + Random.nextLong(-baseSize / 4, baseSize / 4)

            AvailableQuality(
                quality = quality,
                fileSize = fileSize,
                url = "https://example.com/download/${quality.resolution}"
            )
        }
    }
}
