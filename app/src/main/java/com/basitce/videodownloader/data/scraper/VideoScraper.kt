package com.basitce.videodownloader.data.scraper

import android.content.Context
import com.basitce.videodownloader.data.LinkResolver
import com.basitce.videodownloader.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

/**
 * Gelişmiş web scraping ile video bilgisi çıkaran sınıf
 * Birden fazla yöntem dener: API, Embed, Jsoup, WebView
 */
class VideoScraper(private val context: Context) {

    private val webViewScraper = WebViewScraper(context)
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept-Encoding", "gzip, deflate")
                .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1")
                .build()
            chain.proceed(request)
        }
        .build()

    /**
     * URL'den video bilgisi çıkarır
     * Birden fazla yöntem dener
     */
    suspend fun scrapeVideoInfo(url: String): Result<VideoInfo> = withContext(Dispatchers.IO) {
        try {
            val resolved = LinkResolver.resolve(url)
            if (!resolved.isValid) {
                return@withContext Result.failure(Exception("Desteklenmeyen URL"))
            }

            var videoInfo: VideoInfo? = null
            
            // Platform'a göre özel yöntemler dene
            when (resolved.platform) {
                Platform.INSTAGRAM -> {
                    // 1. Embed API dene (en güvenilir)
                    videoInfo = scrapeInstagramEmbed(resolved.cleanUrl)
                    
                    // 2. Direkt sayfa scraping
                    if (videoInfo == null || videoInfo.availableQualities.isEmpty()) {
                        videoInfo = scrapeInstagramDirect(resolved.cleanUrl)
                    }
                }
                Platform.TIKTOK -> {
                    videoInfo = scrapeTikTokDirect(resolved.cleanUrl)
                }
                Platform.TWITTER -> {
                    // Twitter için vxtwitter/fxtwitter proxy kullan
                    videoInfo = scrapeTwitterProxy(resolved.cleanUrl)
                    if (videoInfo == null || videoInfo.availableQualities.isEmpty()) {
                        videoInfo = scrapeTwitterDirect(resolved.cleanUrl)
                    }
                }
                else -> {
                    videoInfo = scrapeGeneric(resolved.cleanUrl, resolved.platform)
                }
            }
            
            // 3. Son çare: WebView ile JavaScript render
            if (videoInfo == null || videoInfo.availableQualities.isEmpty()) {
                val webViewData = webViewScraper.scrapeWithWebView(resolved.cleanUrl)
                if (webViewData != null && webViewData.videoUrl.isNotBlank()) {
                    videoInfo = VideoInfo(
                        id = url.hashCode().toString(),
                        url = resolved.cleanUrl,
                        platform = resolved.platform,
                        title = webViewData.title.ifBlank { "${resolved.platform.displayName} Video" },
                        description = null,
                        thumbnailUrl = webViewData.thumbnail.ifBlank { null },
                        duration = null,
                        author = webViewData.author.ifBlank { null },
                        authorAvatarUrl = null,
                        availableQualities = listOf(
                            AvailableQuality(VideoQuality.QUALITY_720P, null, webViewData.videoUrl)
                        )
                    )
                }
            }

            if (videoInfo != null && videoInfo.availableQualities.isNotEmpty()) {
                Result.success(videoInfo)
            } else if (videoInfo != null) {
                Result.success(videoInfo.copy(availableQualities = emptyList()))
            } else {
                Result.failure(Exception("Video bilgisi alınamadı"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Instagram Embed API kullanarak video URL al
     */
    private fun scrapeInstagramEmbed(url: String): VideoInfo? {
        try {
            // URL'den shortcode çıkar
            val shortcodeMatch = Regex("/(p|reel|reels|tv)/([A-Za-z0-9_-]+)").find(url)
            val shortcode = shortcodeMatch?.groupValues?.get(2) ?: return null
            
            // Embed HTML sayfasını al
            val embedUrl = "https://www.instagram.com/p/$shortcode/embed/captioned/"
            val request = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36")
                .build()
            
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return null
            
            // Video URL'sini çıkar
            var videoUrl: String? = null
            
            // Yöntem 1: video_url JSON field
            val videoUrlMatch = Regex("\"video_url\"\\s*:\\s*\"([^\"]+)\"").find(html)
            if (videoUrlMatch != null) {
                videoUrl = videoUrlMatch.groupValues[1]
                    .replace("\\u0026", "&")
                    .replace("\\/", "/")
            }
            
            // Yöntem 2: data-video-url attribute
            if (videoUrl == null) {
                val dataVideoMatch = Regex("data-video-url=\"([^\"]+)\"").find(html)
                videoUrl = dataVideoMatch?.groupValues?.get(1)
                    ?.replace("&amp;", "&")
            }
            
            // Yöntem 3: video source elementi
            if (videoUrl == null) {
                val doc = Jsoup.parse(html)
                videoUrl = doc.selectFirst("video source")?.attr("src")
                    ?: doc.selectFirst("video")?.attr("src")
            }
            
            // Yöntem 4: Direkt mp4 URL ara
            if (videoUrl == null) {
                val mp4Match = Regex("(https://[^\"\\s]+\\.mp4[^\"\\s]*)").find(html)
                videoUrl = mp4Match?.groupValues?.get(1)
                    ?.replace("\\u0026", "&")
                    ?.replace("\\/", "/")
            }
            
            // Yöntem 5: cdninstagram URL ara
            if (videoUrl == null) {
                val cdnMatch = Regex("(https://[^\"\\s]*cdninstagram\\.com[^\"\\s]*\\.mp4[^\"\\s]*)").find(html)
                videoUrl = cdnMatch?.groupValues?.get(1)
                    ?.replace("\\u0026", "&")
                    ?.replace("\\/", "/")
            }
            
            // Yöntem 6: scontent URL ara
            if (videoUrl == null) {
                val scontentMatch = Regex("(https://scontent[^\"\\s]+\\.mp4[^\"\\s]*)").find(html)
                videoUrl = scontentMatch?.groupValues?.get(1)
                    ?.replace("\\u0026", "&")
                    ?.replace("\\/", "/")
            }
            
            // Başlık ve thumbnail
            val doc = Jsoup.parse(html)
            val title = doc.selectFirst(".Caption")?.text()?.take(100)
                ?: doc.selectFirst("meta[property=og:title]")?.attr("content")
                ?: "Instagram Video"
            
            val thumbnail = doc.selectFirst("img.EmbeddedMediaImage")?.attr("src")
                ?: doc.selectFirst("meta[property=og:image]")?.attr("content")
            
            val author = doc.selectFirst(".UsernameText")?.text()
                ?: doc.selectFirst("a[href*='instagram.com']")?.text()
            
            return if (videoUrl != null) {
                VideoInfo(
                    id = shortcode,
                    url = url,
                    platform = Platform.INSTAGRAM,
                    title = title,
                    description = null,
                    thumbnailUrl = thumbnail,
                    duration = null,
                    author = author,
                    authorAvatarUrl = null,
                    availableQualities = listOf(AvailableQuality(VideoQuality.QUALITY_720P, null, videoUrl))
                )
            } else null
            
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Instagram direkt sayfa scraping
     */
    private fun scrapeInstagramDirect(url: String): VideoInfo? {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1")
                .header("Cookie", "ig_did=; ig_nrcb=1;")
                .build()
            
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return null
            val doc = Jsoup.parse(html)
            
            val title = doc.selectFirst("meta[property=og:title]")?.attr("content")
                ?: doc.selectFirst("title")?.text()
                ?: "Instagram Video"
            
            val thumbnail = doc.selectFirst("meta[property=og:image]")?.attr("content")
            
            // Video URL in different patterns
            var videoUrl: String? = null
            
            // Pattern 1: og:video
            videoUrl = doc.selectFirst("meta[property=og:video]")?.attr("content")
                ?: doc.selectFirst("meta[property=og:video:url]")?.attr("content")
                ?: doc.selectFirst("meta[property=og:video:secure_url]")?.attr("content")
            
            // Pattern 2: JSON-LD
            if (videoUrl == null) {
                doc.select("script[type=application/ld+json]").forEach { script ->
                    val json = script.html()
                    if (json.contains("video") || json.contains("contentUrl")) {
                        val match = Regex("\"contentUrl\"\\s*:\\s*\"([^\"]+)\"").find(json)
                        videoUrl = match?.groupValues?.get(1)
                            ?.replace("\\u0026", "&")
                            ?.replace("\\/", "/")
                    }
                }
            }
            
            // Pattern 3: Script içinde video_url
            if (videoUrl == null) {
                val patterns = listOf(
                    Regex("\"video_url\"\\s*:\\s*\"([^\"]+)\""),
                    Regex("\"video_versions\"\\s*:\\s*\\[\\{\"type\":\\d+,\"url\":\"([^\"]+)\""),
                    Regex("\"playback_url\"\\s*:\\s*\"([^\"]+)\""),
                    Regex("(https://[^\"\\s]*\\.mp4[^\"\\s]*)")
                )
                
                for (pattern in patterns) {
                    val match = pattern.find(html)
                    if (match != null) {
                        videoUrl = match.groupValues.getOrNull(1) ?: match.value
                        videoUrl = videoUrl?.replace("\\u0026", "&")?.replace("\\/", "/")
                        break
                    }
                }
            }
            
            return if (videoUrl != null) {
                VideoInfo(
                    id = url.hashCode().toString(),
                    url = url,
                    platform = Platform.INSTAGRAM,
                    title = title.take(100),
                    description = null,
                    thumbnailUrl = thumbnail,
                    duration = null,
                    author = null,
                    authorAvatarUrl = null,
                    availableQualities = listOf(AvailableQuality(VideoQuality.QUALITY_720P, null, videoUrl))
                )
            } else {
                VideoInfo(
                    id = url.hashCode().toString(),
                    url = url,
                    platform = Platform.INSTAGRAM,
                    title = title.take(100),
                    description = null,
                    thumbnailUrl = thumbnail,
                    duration = null,
                    author = null,
                    authorAvatarUrl = null,
                    availableQualities = emptyList()
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * TikTok scraper
     */
    private fun scrapeTikTokDirect(url: String): VideoInfo? {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .build()
            
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return null
            val doc = Jsoup.parse(html)
            
            val title = doc.selectFirst("meta[property=og:title]")?.attr("content")
                ?: doc.selectFirst("title")?.text()
                ?: "TikTok Video"
            
            val thumbnail = doc.selectFirst("meta[property=og:image]")?.attr("content")
            
            var videoUrl = doc.selectFirst("meta[property=og:video]")?.attr("content")
                ?: doc.selectFirst("meta[property=og:video:url]")?.attr("content")
            
            // Script içinde video URL ara
            if (videoUrl == null) {
                val patterns = listOf(
                    Regex("\"downloadAddr\"\\s*:\\s*\"([^\"]+)\""),
                    Regex("\"playAddr\"\\s*:\\s*\"([^\"]+)\""),
                    Regex("\"play_addr\"\\s*:\\s*\\{[^}]*\"url_list\"\\s*:\\s*\\[\"([^\"]+)\"")
                )
                
                for (pattern in patterns) {
                    val match = pattern.find(html)
                    if (match != null) {
                        videoUrl = match.groupValues[1]
                            .replace("\\u002F", "/")
                            .replace("\\u0026", "&")
                            .replace("\\/", "/")
                        break
                    }
                }
            }
            
            return VideoInfo(
                id = url.hashCode().toString(),
                url = url,
                platform = Platform.TIKTOK,
                title = title.take(100),
                description = null,
                thumbnailUrl = thumbnail,
                duration = null,
                author = null,
                authorAvatarUrl = null,
                availableQualities = if (videoUrl != null) {
                    listOf(AvailableQuality(VideoQuality.QUALITY_720P, null, videoUrl))
                } else emptyList()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Twitter için vxtwitter/fxtwitter proxy kullan
     */
    private fun scrapeTwitterProxy(url: String): VideoInfo? {
        try {
            // URL'yi vxtwitter formatına çevir
            val proxyUrl = url
                .replace("twitter.com", "vxtwitter.com")
                .replace("x.com", "vxtwitter.com")
            
            val request = Request.Builder()
                .url(proxyUrl)
                .header("User-Agent", "Mozilla/5.0 (compatible; Discordbot/2.0; +https://discordapp.com)")
                .build()
            
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return null
            val doc = Jsoup.parse(html)
            
            val title = doc.selectFirst("meta[property=og:title]")?.attr("content")
                ?: doc.selectFirst("title")?.text()
                ?: "Twitter Video"
            
            val thumbnail = doc.selectFirst("meta[property=og:image]")?.attr("content")
            
            var videoUrl = doc.selectFirst("meta[property=og:video]")?.attr("content")
                ?: doc.selectFirst("meta[property=og:video:url]")?.attr("content")
            
            return if (videoUrl != null) {
                VideoInfo(
                    id = url.hashCode().toString(),
                    url = url,
                    platform = Platform.TWITTER,
                    title = title.take(100),
                    description = null,
                    thumbnailUrl = thumbnail,
                    duration = null,
                    author = null,
                    authorAvatarUrl = null,
                    availableQualities = listOf(AvailableQuality(VideoQuality.QUALITY_720P, null, videoUrl))
                )
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Twitter direkt scraping
     */
    private fun scrapeTwitterDirect(url: String): VideoInfo? {
        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return null
            val doc = Jsoup.parse(html)
            
            val title = doc.selectFirst("meta[property=og:title]")?.attr("content")
                ?: "Twitter Video"
            val thumbnail = doc.selectFirst("meta[property=og:image]")?.attr("content")
            
            var videoUrl = doc.selectFirst("meta[property=og:video]")?.attr("content")
            
            if (videoUrl == null) {
                val mp4Match = Regex("(https://video\\.twimg\\.com[^\"\\s]+\\.mp4[^\"\\s]*)").find(html)
                videoUrl = mp4Match?.groupValues?.get(1)
            }
            
            return VideoInfo(
                id = url.hashCode().toString(),
                url = url,
                platform = Platform.TWITTER,
                title = title.take(100),
                description = null,
                thumbnailUrl = thumbnail,
                duration = null,
                author = null,
                authorAvatarUrl = null,
                availableQualities = if (videoUrl != null) {
                    listOf(AvailableQuality(VideoQuality.QUALITY_720P, null, videoUrl))
                } else emptyList()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Generic scraper for YouTube, Facebook, Pinterest
     */
    private fun scrapeGeneric(url: String, platform: Platform): VideoInfo? {
        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return null
            val doc = Jsoup.parse(html)
            
            val title = doc.selectFirst("meta[property=og:title]")?.attr("content")
                ?: doc.selectFirst("title")?.text()
                ?: "${platform.displayName} Video"
            
            val thumbnail = doc.selectFirst("meta[property=og:image]")?.attr("content")
            val description = doc.selectFirst("meta[property=og:description]")?.attr("content")
            
            var videoUrl = doc.selectFirst("meta[property=og:video]")?.attr("content")
                ?: doc.selectFirst("meta[property=og:video:url]")?.attr("content")
                ?: doc.selectFirst("video source")?.attr("src")
                ?: doc.selectFirst("video")?.attr("src")
            
            // Script içinde video URL ara
            if (videoUrl == null) {
                val patterns = listOf(
                    Regex("\"playable_url\"\\s*:\\s*\"([^\"]+)\""),
                    Regex("\"playable_url_quality_hd\"\\s*:\\s*\"([^\"]+)\""),
                    Regex("\"contentUrl\"\\s*:\\s*\"([^\"]+)\""),
                    Regex("(https://[^\"\\s]+\\.mp4[^\"\\s]*)")
                )
                
                for (pattern in patterns) {
                    val match = pattern.find(html)
                    if (match != null) {
                        videoUrl = match.groupValues.getOrNull(1) ?: match.value
                        videoUrl = videoUrl?.replace("\\u0026", "&")?.replace("\\/", "/")
                        break
                    }
                }
            }
            
            return VideoInfo(
                id = url.hashCode().toString(),
                url = url,
                platform = platform,
                title = title.take(100),
                description = description?.take(200),
                thumbnailUrl = thumbnail,
                duration = null,
                author = null,
                authorAvatarUrl = null,
                availableQualities = if (videoUrl != null) {
                    listOf(AvailableQuality(VideoQuality.QUALITY_720P, null, videoUrl))
                } else emptyList()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
