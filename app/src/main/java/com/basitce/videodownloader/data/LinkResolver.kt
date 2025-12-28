package com.basitce.videodownloader.data

import com.basitce.videodownloader.data.model.Platform

/**
 * URL'leri analiz edip platform ve video ID'sini çıkaran akıllı link çözümleyici
 * Tüm link formatlarını destekler: mobil, kısa link, embed, farklı domainler
 */
object LinkResolver {

    /**
     * Link analiz sonucu
     */
    data class ResolveResult(
        val platform: Platform,
        val videoId: String?,
        val cleanUrl: String,
        val isValid: Boolean
    )

    // Platform regex desenleri - EN KAPSAMLI HALI
    private val platformPatterns: Map<Platform, List<Regex>> = mapOf(
        
        // INSTAGRAM - Tüm formatlar
        Platform.INSTAGRAM to listOf(
            // Post, Reel, TV, Reels
            Regex("(?:https?://)?(?:www\\.)?instagram\\.com/(?:p|reel|tv|reels)/([A-Za-z0-9_-]+)/?.*", RegexOption.IGNORE_CASE),
            // Kısa link
            Regex("(?:https?://)?(?:www\\.)?instagr\\.am/(?:p|reel)/([A-Za-z0-9_-]+)/?.*", RegexOption.IGNORE_CASE),
            // Stories
            Regex("(?:https?://)?(?:www\\.)?instagram\\.com/stories/([^/]+)/([0-9]+)/?.*", RegexOption.IGNORE_CASE),
            // Profil video (IGTV)
            Regex("(?:https?://)?(?:www\\.)?instagram\\.com/([^/]+)/(?:channel|igtv)/([A-Za-z0-9_-]+)/?.*", RegexOption.IGNORE_CASE),
            // Share link formatı
            Regex("(?:https?://)?(?:www\\.)?instagram\\.com/share/([A-Za-z0-9_-]+)/?.*", RegexOption.IGNORE_CASE)
        ),
        
        // TIKTOK - Tüm formatlar
        Platform.TIKTOK to listOf(
            // Standart video linki
            Regex("(?:https?://)?(?:www\\.)?tiktok\\.com/@([^/]+)/video/([0-9]+)/?.*", RegexOption.IGNORE_CASE),
            // VM kısa link
            Regex("(?:https?://)?(?:vm|vt)\\.tiktok\\.com/([A-Za-z0-9]+)/?.*", RegexOption.IGNORE_CASE),
            // T kısa link
            Regex("(?:https?://)?(?:www\\.)?tiktok\\.com/t/([A-Za-z0-9]+)/?.*", RegexOption.IGNORE_CASE),
            // Mobil link
            Regex("(?:https?://)?(?:m\\.)?tiktok\\.com/v/([0-9]+)/?.*", RegexOption.IGNORE_CASE),
            // @username/video formatı (parametre ile)
            Regex("(?:https?://)?(?:www\\.)?tiktok\\.com/@[^/]+/video/([0-9]+)\\?.*", RegexOption.IGNORE_CASE),
            // TikTok.com/@ formatı (sadece kullanıcı adı)
            Regex("(?:https?://)?(?:www\\.)?tiktok\\.com/@([^/?]+)/?.*", RegexOption.IGNORE_CASE),
            // Lite versiyon
            Regex("(?:https?://)?(?:www\\.)?tiktok\\.com/share/video/([0-9]+)/?.*", RegexOption.IGNORE_CASE)
        ),
        
        // TWITTER/X - Tüm formatlar
        Platform.TWITTER to listOf(
            // x.com standart
            Regex("(?:https?://)?(?:www\\.)?x\\.com/([^/]+)/status/([0-9]+)/?.*", RegexOption.IGNORE_CASE),
            // twitter.com standart
            Regex("(?:https?://)?(?:www\\.)?twitter\\.com/([^/]+)/status/([0-9]+)/?.*", RegexOption.IGNORE_CASE),
            // Mobil twitter
            Regex("(?:https?://)?mobile\\.twitter\\.com/([^/]+)/status/([0-9]+)/?.*", RegexOption.IGNORE_CASE),
            // Mobil x
            Regex("(?:https?://)?mobile\\.x\\.com/([^/]+)/status/([0-9]+)/?.*", RegexOption.IGNORE_CASE),
            // fxtwitter/vxtwitter (embed fix servisleri)
            Regex("(?:https?://)?(?:www\\.)?(?:fx|vx)twitter\\.com/([^/]+)/status/([0-9]+)/?.*", RegexOption.IGNORE_CASE),
            // Nitter (alternatif frontend)
            Regex("(?:https?://)?(?:www\\.)?nitter\\.[a-z]+/([^/]+)/status/([0-9]+)/?.*", RegexOption.IGNORE_CASE),
            // t.co kısa link
            Regex("(?:https?://)?t\\.co/([A-Za-z0-9]+)/?.*", RegexOption.IGNORE_CASE)
        ),
        
        // YOUTUBE - Tüm formatlar  
        Platform.YOUTUBE to listOf(
            // Standart watch
            Regex("(?:https?://)?(?:www\\.)?youtube\\.com/watch\\?v=([A-Za-z0-9_-]{11}).*", RegexOption.IGNORE_CASE),
            // youtu.be kısa link
            Regex("(?:https?://)?youtu\\.be/([A-Za-z0-9_-]{11}).*", RegexOption.IGNORE_CASE),
            // Shorts
            Regex("(?:https?://)?(?:www\\.)?youtube\\.com/shorts/([A-Za-z0-9_-]{11}).*", RegexOption.IGNORE_CASE),
            // Mobil
            Regex("(?:https?://)?m\\.youtube\\.com/watch\\?v=([A-Za-z0-9_-]{11}).*", RegexOption.IGNORE_CASE),
            // Embed
            Regex("(?:https?://)?(?:www\\.)?youtube\\.com/embed/([A-Za-z0-9_-]{11}).*", RegexOption.IGNORE_CASE),
            // v/ formatı
            Regex("(?:https?://)?(?:www\\.)?youtube\\.com/v/([A-Za-z0-9_-]{11}).*", RegexOption.IGNORE_CASE),
            // Attribution link
            Regex("(?:https?://)?(?:www\\.)?youtube\\.com/attribution_link.*v%3D([A-Za-z0-9_-]{11}).*", RegexOption.IGNORE_CASE),
            // Live
            Regex("(?:https?://)?(?:www\\.)?youtube\\.com/live/([A-Za-z0-9_-]{11}).*", RegexOption.IGNORE_CASE),
            // Clip
            Regex("(?:https?://)?(?:www\\.)?youtube\\.com/clip/([A-Za-z0-9_-]+).*", RegexOption.IGNORE_CASE),
            // Nocookie embed
            Regex("(?:https?://)?(?:www\\.)?youtube-nocookie\\.com/embed/([A-Za-z0-9_-]{11}).*", RegexOption.IGNORE_CASE),
            // Music
            Regex("(?:https?://)?music\\.youtube\\.com/watch\\?v=([A-Za-z0-9_-]{11}).*", RegexOption.IGNORE_CASE)
        ),
        
        // FACEBOOK - Tüm formatlar
        Platform.FACEBOOK to listOf(
            // Watch sayfası
            Regex("(?:https?://)?(?:www\\.)?facebook\\.com/watch/\\?v=([0-9]+).*", RegexOption.IGNORE_CASE),
            // Kullanıcı videoları
            Regex("(?:https?://)?(?:www\\.)?facebook\\.com/([^/]+)/videos/([0-9]+)/?.*", RegexOption.IGNORE_CASE),
            // fb.watch kısa link
            Regex("(?:https?://)?fb\\.watch/([A-Za-z0-9_-]+)/?.*", RegexOption.IGNORE_CASE),
            // Reel
            Regex("(?:https?://)?(?:www\\.)?facebook\\.com/reel/([0-9]+)/?.*", RegexOption.IGNORE_CASE),
            // Video ID direkt
            Regex("(?:https?://)?(?:www\\.)?facebook\\.com/video\\.php\\?v=([0-9]+).*", RegexOption.IGNORE_CASE),
            // Story
            Regex("(?:https?://)?(?:www\\.)?facebook\\.com/stories/([0-9]+)/?.*", RegexOption.IGNORE_CASE),
            // Mobil
            Regex("(?:https?://)?m\\.facebook\\.com/watch/\\?v=([0-9]+).*", RegexOption.IGNORE_CASE),
            // Mobil video
            Regex("(?:https?://)?m\\.facebook\\.com/([^/]+)/videos/([0-9]+)/?.*", RegexOption.IGNORE_CASE),
            // Share formatı
            Regex("(?:https?://)?(?:www\\.)?facebook\\.com/share/v/([A-Za-z0-9_-]+)/?.*", RegexOption.IGNORE_CASE),
            // fb.gg gaming
            Regex("(?:https?://)?fb\\.gg/v/([0-9]+)/?.*", RegexOption.IGNORE_CASE)
        ),
        
        // PINTEREST - Tüm formatlar
        Platform.PINTEREST to listOf(
            // Standart pin
            Regex("(?:https?://)?(?:www\\.)?pinterest\\.[a-z.]+/pin/([0-9]+)/?.*", RegexOption.IGNORE_CASE),
            // Kısa link
            Regex("(?:https?://)?pin\\.it/([A-Za-z0-9]+)/?.*", RegexOption.IGNORE_CASE),
            // Pin ile başlık
            Regex("(?:https?://)?(?:www\\.)?pinterest\\.[a-z.]+/pin/([0-9]+)/[^/]+/?.*", RegexOption.IGNORE_CASE),
            // Türk domain dahil birçok ülke domaini
            Regex("(?:https?://)?(?:tr|de|fr|es|it|br|jp|kr|in|uk|au|ca|mx)\\.pinterest\\.com/pin/([0-9]+)/?.*", RegexOption.IGNORE_CASE),
            // Video pin
            Regex("(?:https?://)?(?:www\\.)?pinterest\\.[a-z.]+/pin/create/video/([0-9]+)/?.*", RegexOption.IGNORE_CASE)
        )
    )

    /**
     * Verilen URL'yi analiz eder ve platform bilgisini döndürür
     */
    fun resolve(url: String): ResolveResult {
        val cleanedUrl = url.trim()
        
        // Her platform için desenleri kontrol et
        for ((platform, patterns) in platformPatterns) {
            for (pattern in patterns) {
                val match = pattern.find(cleanedUrl)
                if (match != null) {
                    // Video ID'yi çıkar (ilk veya ikinci grup olabilir)
                    val videoId = match.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }
                        ?: match.groupValues.getOrNull(1)
                    return ResolveResult(
                        platform = platform,
                        videoId = videoId,
                        cleanUrl = normalizeUrl(cleanedUrl),
                        isValid = true
                    )
                }
            }
        }

        // Hiçbir platforma uymadı
        return ResolveResult(
            platform = Platform.UNKNOWN,
            videoId = null,
            cleanUrl = cleanedUrl,
            isValid = false
        )
    }

    /**
     * URL'yi normalleştirir (https ekle, www. ekle vs)
     */
    private fun normalizeUrl(url: String): String {
        var normalized = url.trim()
        
        // Protokol yoksa ekle
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://$normalized"
        }
        
        // http'yi https'e çevir
        normalized = normalized.replace("http://", "https://")
        
        return normalized
    }

    /**
     * URL'nin geçerli bir desteklenen link olup olmadığını kontrol eder
     */
    fun isSupported(url: String): Boolean {
        return resolve(url).isValid
    }

    /**
     * URL'den platformu algılar
     */
    fun detectPlatform(url: String): Platform {
        return resolve(url).platform
    }

    /**
     * Paylaşılan metin içinden URL çıkarır
     * Birden fazla URL varsa ilk desteklenen URL'yi döndürür
     */
    fun extractUrlFromText(text: String): String? {
        val urlPattern = Regex("https?://[\\w\\-._~:/?#\\[\\]@!\$&'()*+,;=%]+")
        val urls = urlPattern.findAll(text).map { it.value }.toList()
        
        // Önce desteklenen URL'yi bul
        for (url in urls) {
            if (isSupported(url)) {
                return url
            }
        }
        
        // Desteklenen yoksa ilk URL'yi döndür
        return urls.firstOrNull()
    }

    /**
     * Platform için örnek URL döndürür (kullanıcıya yardım için)
     */
    fun getExampleUrl(platform: Platform): String {
        return when (platform) {
            Platform.INSTAGRAM -> "https://www.instagram.com/reel/ABC123xyz/"
            Platform.TIKTOK -> "https://www.tiktok.com/@user/video/1234567890"
            Platform.TWITTER -> "https://x.com/user/status/1234567890"
            Platform.YOUTUBE -> "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
            Platform.FACEBOOK -> "https://www.facebook.com/watch/?v=1234567890"
            Platform.PINTEREST -> "https://www.pinterest.com/pin/1234567890/"
            Platform.UNKNOWN -> ""
        }
    }

    /**
     * Tüm desteklenen platformları ve örnek URL'leri döndürür
     */
    fun getSupportedFormats(): Map<Platform, List<String>> {
        return mapOf(
            Platform.INSTAGRAM to listOf(
                "instagram.com/p/xxx",
                "instagram.com/reel/xxx",
                "instagram.com/reels/xxx",
                "instagram.com/tv/xxx",
                "instagram.com/stories/user/xxx",
                "instagr.am/p/xxx"
            ),
            Platform.TIKTOK to listOf(
                "tiktok.com/@user/video/xxx",
                "vm.tiktok.com/xxx",
                "vt.tiktok.com/xxx",
                "tiktok.com/t/xxx",
                "m.tiktok.com/v/xxx"
            ),
            Platform.TWITTER to listOf(
                "twitter.com/user/status/xxx",
                "x.com/user/status/xxx",
                "mobile.twitter.com/user/status/xxx",
                "t.co/xxx"
            ),
            Platform.YOUTUBE to listOf(
                "youtube.com/watch?v=xxx",
                "youtu.be/xxx",
                "youtube.com/shorts/xxx",
                "youtube.com/embed/xxx",
                "youtube.com/live/xxx",
                "music.youtube.com/watch?v=xxx"
            ),
            Platform.FACEBOOK to listOf(
                "facebook.com/watch/?v=xxx",
                "facebook.com/user/videos/xxx",
                "fb.watch/xxx",
                "facebook.com/reel/xxx",
                "m.facebook.com/watch/?v=xxx"
            ),
            Platform.PINTEREST to listOf(
                "pinterest.com/pin/xxx",
                "pin.it/xxx",
                "tr.pinterest.com/pin/xxx"
            )
        )
    }
}
