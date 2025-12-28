package com.basitce.videodownloader.data.scraper

import android.content.Context
import com.basitce.videodownloader.data.LinkResolver
import com.basitce.videodownloader.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

/**
 * Web scraping ile video bilgisi çıkaran ana sınıf
 * Jsoup ile başlar, başarısız olursa WebView fallback kullanır
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
                .header("Accept-Language", "en-US,en;q=0.5")
                .build()
            chain.proceed(request)
        }
        .build()

    /**
     * URL'den video bilgisi çıkarır
     * İlk önce Jsoup ile dener, başarısız olursa WebView kullanır
     */
    suspend fun scrapeVideoInfo(url: String): Result<VideoInfo> = withContext(Dispatchers.IO) {
        try {
            val resolved = LinkResolver.resolve(url)
            if (!resolved.isValid) {
                return@withContext Result.failure(Exception("Desteklenmeyen URL"))
            }

            // 1. Önce hızlı Jsoup ile dene
            var videoInfo = scrapeWithJsoup(resolved.cleanUrl, resolved.platform)
            
            // 2. Eğer video URL bulunamadıysa WebView ile dene
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
                // Bilgi var ama video URL yok
                Result.success(videoInfo.copy(
                    availableQualities = emptyList()
                ))
            } else {
                Result.failure(Exception("Video bilgisi alınamadı"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Jsoup ile hızlı scraping
     */
    private suspend fun scrapeWithJsoup(url: String, platform: Platform): VideoInfo? = withContext(Dispatchers.IO) {
        try {
            val doc = fetchPage(url) ?: return@withContext null
            
            when (platform) {
                Platform.INSTAGRAM -> scrapeInstagram(url, doc)
                Platform.TIKTOK -> scrapeTikTok(url, doc)
                Platform.TWITTER -> scrapeTwitter(url, doc)
                Platform.YOUTUBE -> scrapeYouTube(url, doc)
                Platform.FACEBOOK -> scrapeFacebook(url, doc)
                Platform.PINTEREST -> scrapePinterest(url, doc)
                Platform.UNKNOWN -> null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Sayfayı indirir ve parse eder
     */
    private fun fetchPage(url: String): Document? {
        return try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return null
            Jsoup.parse(html)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Instagram Reels/Post scraper
     */
    private fun scrapeInstagram(url: String, doc: Document): VideoInfo {
        val title = doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?: doc.selectFirst("title")?.text()
            ?: "Instagram Video"
        
        val thumbnail = doc.selectFirst("meta[property=og:image]")?.attr("content")
        val description = doc.selectFirst("meta[property=og:description]")?.attr("content")
        
        // Video URL'sini bul
        var videoUrl = doc.selectFirst("meta[property=og:video]")?.attr("content")
            ?: doc.selectFirst("meta[property=og:video:url]")?.attr("content")
            ?: doc.selectFirst("meta[property=og:video:secure_url]")?.attr("content")
        
        // Script içinden video URL'si ara
        if (videoUrl == null) {
            val scripts = doc.select("script[type=application/ld+json]")
            for (script in scripts) {
                val json = script.html()
                if (json.contains("video") || json.contains("contentUrl")) {
                    val videoMatch = Regex("\"contentUrl\"\\s*:\\s*\"([^\"]+)\"").find(json)
                    videoUrl = videoMatch?.groupValues?.get(1)
                        ?.replace("\\u0026", "&")
                        ?.replace("\\/", "/")
                    if (videoUrl != null) break
                }
            }
        }
        
        // HTML içinde doğrudan video URL ara
        if (videoUrl == null) {
            val html = doc.html()
            val mp4Match = Regex("\"video_url\"\\s*:\\s*\"([^\"]+)\"").find(html)
                ?: Regex("https://[^\"]+\\.mp4[^\"]*").find(html)
            videoUrl = mp4Match?.groupValues?.getOrNull(1) ?: mp4Match?.value
            videoUrl = videoUrl?.replace("\\u0026", "&")?.replace("\\/", "/")
        }

        return createVideoInfo(url, Platform.INSTAGRAM, title, thumbnail, description, videoUrl)
    }

    /**
     * TikTok scraper
     */
    private fun scrapeTikTok(url: String, doc: Document): VideoInfo {
        val title = doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?: doc.selectFirst("title")?.text()
            ?: "TikTok Video"
        
        val thumbnail = doc.selectFirst("meta[property=og:image]")?.attr("content")
        val description = doc.selectFirst("meta[property=og:description]")?.attr("content")
        
        var videoUrl = doc.selectFirst("meta[property=og:video]")?.attr("content")
            ?: doc.selectFirst("meta[property=og:video:url]")?.attr("content")
        
        // Script içinde video URL ara
        if (videoUrl == null) {
            val html = doc.html()
            
            // downloadAddr tercih et (watermark'sız)
            val downloadMatch = Regex("\"downloadAddr\"\\s*:\\s*\"([^\"]+)\"").find(html)
            val playMatch = Regex("\"playAddr\"\\s*:\\s*\"([^\"]+)\"").find(html)
            
            videoUrl = (downloadMatch ?: playMatch)?.groupValues?.get(1)
                ?.replace("\\u002F", "/")
                ?.replace("\\u0026", "&")
                ?.replace("\\/", "/")
        }

        return createVideoInfo(url, Platform.TIKTOK, title, thumbnail, description, videoUrl)
    }

    /**
     * Twitter/X scraper
     */
    private fun scrapeTwitter(url: String, doc: Document): VideoInfo {
        val title = doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?: doc.selectFirst("title")?.text()
            ?: "Twitter Video"
        
        val thumbnail = doc.selectFirst("meta[property=og:image]")?.attr("content")
        val description = doc.selectFirst("meta[property=og:description]")?.attr("content")
        
        var videoUrl = doc.selectFirst("meta[property=og:video]")?.attr("content")
            ?: doc.selectFirst("meta[property=og:video:url]")?.attr("content")
        
        // Video elementi kontrol et
        if (videoUrl == null) {
            videoUrl = doc.selectFirst("video source")?.attr("src")
                ?: doc.selectFirst("video")?.attr("src")
        }
        
        // Script içinde m3u8 veya mp4 ara
        if (videoUrl == null) {
            val html = doc.html()
            val mp4Match = Regex("\"video_url\"\\s*:\\s*\"([^\"]+\\.mp4[^\"]*)\"").find(html)
            videoUrl = mp4Match?.groupValues?.get(1)?.replace("\\/", "/")
        }

        return createVideoInfo(url, Platform.TWITTER, title, thumbnail, description, videoUrl)
    }

    /**
     * YouTube scraper
     */
    private fun scrapeYouTube(url: String, doc: Document): VideoInfo {
        val title = doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?: doc.selectFirst("meta[name=title]")?.attr("content")
            ?: doc.selectFirst("title")?.text()
            ?: "YouTube Video"
        
        val thumbnail = doc.selectFirst("meta[property=og:image]")?.attr("content")
        val description = doc.selectFirst("meta[property=og:description]")?.attr("content")
        
        val videoId = LinkResolver.resolve(url).videoId
        
        // YouTube doğrudan video URL vermez, bilgi döndür
        return VideoInfo(
            id = videoId ?: url.hashCode().toString(),
            url = url,
            platform = Platform.YOUTUBE,
            title = title.take(100),
            description = description?.take(200),
            thumbnailUrl = thumbnail ?: if (videoId != null) "https://img.youtube.com/vi/$videoId/maxresdefault.jpg" else null,
            duration = null,
            author = null,
            authorAvatarUrl = null,
            availableQualities = emptyList() // YouTube için WebView gerekli
        )
    }

    /**
     * Facebook scraper
     */
    private fun scrapeFacebook(url: String, doc: Document): VideoInfo {
        val title = doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?: "Facebook Video"
        
        val thumbnail = doc.selectFirst("meta[property=og:image]")?.attr("content")
        val description = doc.selectFirst("meta[property=og:description]")?.attr("content")
        
        var videoUrl = doc.selectFirst("meta[property=og:video]")?.attr("content")
            ?: doc.selectFirst("meta[property=og:video:url]")?.attr("content")
        
        // Script içinde video URL ara
        if (videoUrl == null) {
            val html = doc.html()
            val mp4Match = Regex("\"playable_url\"\\s*:\\s*\"([^\"]+)\"").find(html)
                ?: Regex("\"playable_url_quality_hd\"\\s*:\\s*\"([^\"]+)\"").find(html)
            videoUrl = mp4Match?.groupValues?.get(1)
                ?.replace("\\u0026", "&")
                ?.replace("\\/", "/")
        }

        return createVideoInfo(url, Platform.FACEBOOK, title, thumbnail, description, videoUrl)
    }

    /**
     * Pinterest scraper
     */
    private fun scrapePinterest(url: String, doc: Document): VideoInfo {
        val title = doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?: "Pinterest Video"
        
        val thumbnail = doc.selectFirst("meta[property=og:image]")?.attr("content")
        val description = doc.selectFirst("meta[property=og:description]")?.attr("content")
        
        var videoUrl = doc.selectFirst("meta[property=og:video]")?.attr("content")
            ?: doc.selectFirst("meta[property=og:video:url]")?.attr("content")
        
        if (videoUrl == null) {
            videoUrl = doc.selectFirst("video source")?.attr("src")
        }
        
        // Script içinde video URL ara
        if (videoUrl == null) {
            val html = doc.html()
            val mp4Match = Regex("\"url\"\\s*:\\s*\"([^\"]+\\.mp4[^\"]*)\"").find(html)
            videoUrl = mp4Match?.groupValues?.get(1)?.replace("\\/", "/")
        }

        return createVideoInfo(url, Platform.PINTEREST, title, thumbnail, description, videoUrl)
    }

    /**
     * VideoInfo objesi oluşturur
     */
    private fun createVideoInfo(
        url: String,
        platform: Platform,
        title: String,
        thumbnail: String?,
        description: String?,
        videoUrl: String?
    ): VideoInfo {
        val qualities = if (!videoUrl.isNullOrBlank()) {
            listOf(AvailableQuality(VideoQuality.QUALITY_720P, null, videoUrl))
        } else {
            emptyList()
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
            availableQualities = qualities
        )
    }
}
