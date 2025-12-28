package com.basitce.videodownloader.data.scraper

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * JavaScript rendering gerektiren sayfalar için WebView tabanlı scraper
 * Instagram, TikTok gibi SPA uygulamalarında video URL'sini çıkarır
 */
class WebViewScraper(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Sayfayı WebView'da yükleyip JavaScript ile video URL'sini çıkarır
     */
    suspend fun scrapeWithWebView(url: String, timeoutMs: Long = 15000): ScrapedData? {
        return try {
            withTimeout(timeoutMs) {
                loadPageAndExtract(url)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private suspend fun loadPageAndExtract(url: String): ScrapedData? = suspendCancellableCoroutine { cont ->
        mainHandler.post {
            val webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            }

            val jsInterface = VideoExtractorInterface { data ->
                if (cont.isActive) {
                    cont.resume(data)
                }
                webView.destroy()
            }

            webView.addJavascriptInterface(jsInterface, "Android")

            webView.webViewClient = object : WebViewClient() {
                private var pageLoaded = false

                override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                    super.onPageFinished(view, loadedUrl)
                    if (!pageLoaded) {
                        pageLoaded = true
                        // Sayfa yüklendi, biraz bekleyip JavaScript çalıştır
                        mainHandler.postDelayed({
                            extractVideoData(view)
                        }, 2000) // 2 saniye bekle (dinamik içerik için)
                    }
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): android.webkit.WebResourceResponse? {
                    // Video URL'lerini yakalamaya çalış
                    val requestUrl = request?.url?.toString() ?: ""
                    if (isVideoUrl(requestUrl)) {
                        jsInterface.capturedVideoUrl = requestUrl
                    }
                    return super.shouldInterceptRequest(view, request)
                }
            }

            webView.loadUrl(url)

            cont.invokeOnCancellation {
                mainHandler.post {
                    webView.stopLoading()
                    webView.destroy()
                }
            }
        }
    }

    private fun extractVideoData(webView: WebView?) {
        webView?.evaluateJavascript("""
            (function() {
                var result = {
                    title: '',
                    thumbnail: '',
                    videoUrl: '',
                    author: ''
                };
                
                // Başlık al
                var ogTitle = document.querySelector('meta[property="og:title"]');
                result.title = ogTitle ? ogTitle.content : document.title;
                
                // Thumbnail al
                var ogImage = document.querySelector('meta[property="og:image"]');
                result.thumbnail = ogImage ? ogImage.content : '';
                
                // Video URL'si ara
                
                // 1. og:video meta tag
                var ogVideo = document.querySelector('meta[property="og:video"]') || 
                              document.querySelector('meta[property="og:video:url"]') ||
                              document.querySelector('meta[property="og:video:secure_url"]');
                if (ogVideo) {
                    result.videoUrl = ogVideo.content;
                }
                
                // 2. Video elementi
                if (!result.videoUrl) {
                    var videos = document.querySelectorAll('video');
                    for (var i = 0; i < videos.length; i++) {
                        var src = videos[i].src || videos[i].querySelector('source')?.src;
                        if (src && src.includes('.mp4')) {
                            result.videoUrl = src;
                            break;
                        }
                    }
                }
                
                // 3. TikTok için özel: script içinde video URL ara
                if (!result.videoUrl) {
                    var scripts = document.querySelectorAll('script');
                    for (var i = 0; i < scripts.length; i++) {
                        var text = scripts[i].innerHTML;
                        
                        // downloadAddr veya playAddr ara
                        var downloadMatch = text.match(/"downloadAddr"\s*:\s*"([^"]+)"/);
                        var playMatch = text.match(/"playAddr"\s*:\s*"([^"]+)"/);
                        
                        var match = downloadMatch || playMatch;
                        if (match) {
                            result.videoUrl = match[1].replace(/\\u002F/g, '/').replace(/\\u0026/g, '&');
                            break;
                        }
                    }
                }
                
                // 4. Instagram için: video_url ara
                if (!result.videoUrl) {
                    var scripts = document.querySelectorAll('script[type="application/ld+json"]');
                    for (var i = 0; i < scripts.length; i++) {
                        try {
                            var json = JSON.parse(scripts[i].innerHTML);
                            if (json.video && json.video.contentUrl) {
                                result.videoUrl = json.video.contentUrl;
                                break;
                            }
                            if (json.contentUrl) {
                                result.videoUrl = json.contentUrl;
                                break;
                            }
                        } catch(e) {}
                    }
                }
                
                // 5. Sayfadaki tüm mp4 linklerini ara
                if (!result.videoUrl) {
                    var html = document.documentElement.innerHTML;
                    var mp4Match = html.match(/https?:\/\/[^"'\s]+\.mp4[^"'\s]*/i);
                    if (mp4Match) {
                        result.videoUrl = mp4Match[0].replace(/\\u0026/g, '&').replace(/\\/g, '');
                    }
                }
                
                // Yazar bilgisi
                var authorMeta = document.querySelector('meta[property="og:site_name"]') ||
                                 document.querySelector('meta[name="author"]');
                result.author = authorMeta ? authorMeta.content : '';
                
                Android.onDataExtracted(JSON.stringify(result));
                return result;
            })();
        """.trimIndent()) { }
    }

    private fun isVideoUrl(url: String): Boolean {
        return url.contains(".mp4") ||
               url.contains("video") && (url.contains("cdn") || url.contains("media")) ||
               url.contains("playback") ||
               url.contains("stream")
    }

    /**
     * Çıkarılan video verisi
     */
    data class ScrapedData(
        val title: String,
        val thumbnail: String,
        val videoUrl: String,
        val author: String
    )

    /**
     * JavaScript - Kotlin köprüsü
     */
    private class VideoExtractorInterface(
        private val onDataExtracted: (ScrapedData?) -> Unit
    ) {
        var capturedVideoUrl: String? = null

        @JavascriptInterface
        fun onDataExtracted(jsonData: String) {
            try {
                val json = org.json.JSONObject(jsonData)
                var videoUrl = json.optString("videoUrl", "")
                
                // Eğer JavaScript'ten URL bulunamadıysa, yakalanan URL'yi kullan
                if (videoUrl.isBlank() && !capturedVideoUrl.isNullOrBlank()) {
                    videoUrl = capturedVideoUrl!!
                }
                
                val data = ScrapedData(
                    title = json.optString("title", "Video"),
                    thumbnail = json.optString("thumbnail", ""),
                    videoUrl = videoUrl,
                    author = json.optString("author", "")
                )
                onDataExtracted(data)
            } catch (e: Exception) {
                e.printStackTrace()
                onDataExtracted(null)
            }
        }
    }
}
