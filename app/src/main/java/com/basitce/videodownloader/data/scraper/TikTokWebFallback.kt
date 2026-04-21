package com.basitce.videodownloader.data.scraper

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object TikTokWebFallback {

    const val DIRECT_SELECTOR = "__tiktok_web_direct__"

    suspend fun fetchPreview(pageUrl: String): PreviewResult = withContext(Dispatchers.IO) {
        val client = buildClient()
        resolveMedia(client, pageUrl, probeSize = true).toPreviewResult()
    }

    suspend fun downloadToFile(
        pageUrl: String,
        destination: File,
        onProgress: (Int) -> Unit = {}
    ): Long = withContext(Dispatchers.IO) {
        val client = buildClient()
        val media = resolveMedia(client, pageUrl, probeSize = false)
        val request = Request.Builder()
            .url(media.directUrl)
            .headers(mediaHeaders())
            .get()
            .build()

        val call = client.newCall(request)
        val cancelHandler = currentCoroutineContext().job.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                call.cancel()
                destination.delete()
            }
        }

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException(
                        "TikTok video akisi indirilemedi (HTTP ${response.code})"
                    )
                }

                val body = response.body
                    ?: throw IOException("TikTok video icerigi bos geldi")
                val totalBytes = body.contentLength().takeIf { it > 0 } ?: media.fileSize

                body.byteStream().use { input ->
                    destination.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloadedBytes = 0L

                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) {
                                break
                            }

                            output.write(buffer, 0, read)
                            downloadedBytes += read

                            if (totalBytes != null && totalBytes > 0L) {
                                val progress = ((downloadedBytes * 99L) / totalBytes)
                                    .toInt()
                                    .coerceIn(0, 99)
                                onProgress(progress)
                            }
                        }
                    }
                }

                onProgress(100)
                destination.length()
            }
        } finally {
            cancelHandler.dispose()
        }
    }

    private fun resolveMedia(
        client: OkHttpClient,
        pageUrl: String,
        probeSize: Boolean
    ): ResolvedMedia {
        val pageRequest = Request.Builder()
            .url(pageUrl)
            .headers(pageHeaders())
            .get()
            .build()

        val html = client.newCall(pageRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("TikTok sayfasi yuklenemedi (HTTP ${response.code})")
            }
            response.body?.string()?.takeIf { it.isNotBlank() }
                ?: throw IOException("TikTok sayfasi bos dondu")
        }

        val item = parseItemStruct(html)
        val video = item.optJSONObject("video")
            ?: throw IOException("TikTok video verisi bulunamadi")
        val directUrl = video.optString("playAddr").nullIfBlank()
            ?: video.optString("downloadAddr").nullIfBlank()
            ?: throw IOException("TikTok dogrudan video adresi bulunamadi")

        return ResolvedMedia(
            id = item.optString("id").nullIfBlank()
                ?: throw IOException("TikTok video kimligi bulunamadi"),
            title = item.optString("desc").nullIfBlank(),
            description = item.optString("desc").nullIfBlank(),
            author = item.optJSONObject("author")?.run {
                optString("nickname").nullIfBlank()
                    ?: optString("uniqueId").nullIfBlank()
            },
            thumbnailUrl = video.optString("originCover").nullIfBlank()
                ?: video.optString("cover").nullIfBlank()
                ?: video.optString("dynamicCover").nullIfBlank(),
            durationSeconds = video.optLong("duration").takeIf { it > 0L },
            width = video.optInt("width"),
            height = video.optInt("height"),
            directUrl = directUrl,
            fileSize = if (probeSize) probeFileSize(client, directUrl) else null
        )
    }

    private fun parseItemStruct(html: String): JSONObject {
        val document = Jsoup.parse(html)

        document.getElementById("api-data")
            ?.scriptJson()
            ?.takeIf { it.isNotBlank() }
            ?.let(::JSONObject)
            ?.optJSONObject("videoDetail")
            ?.optJSONObject("itemInfo")
            ?.optJSONObject("itemStruct")
            ?.let { return it }

        document.getElementById("__UNIVERSAL_DATA_FOR_REHYDRATION__")
            ?.scriptJson()
            ?.takeIf { it.isNotBlank() }
            ?.let(::JSONObject)
            ?.optJSONObject("__DEFAULT_SCOPE__")
            ?.let { scope ->
                scope.optJSONObject("webapp.video-detail")
                    ?: scope.optJSONObject("webapp.reflow.video.detail")
            }
            ?.optJSONObject("itemInfo")
            ?.optJSONObject("itemStruct")
            ?.let { return it }

        throw IOException("TikTok fallback JSON verisi bulunamadi")
    }

    private fun probeFileSize(client: OkHttpClient, directUrl: String): Long? {
        val headRequest = Request.Builder()
            .url(directUrl)
            .headers(mediaHeaders())
            .head()
            .build()

        return runCatching {
            client.newCall(headRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use null
                }

                response.header("Content-Length")
                    ?.toLongOrNull()
                    ?.takeIf { it > 0L }
            }
        }.getOrNull()
    }

    private fun buildClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .cookieJar(InMemoryCookieJar())
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private fun pageHeaders(): Headers {
        return Headers.Builder()
            .add("User-Agent", MOBILE_USER_AGENT)
            .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .add("Accept-Language", "en-US,en;q=0.9")
            .add("Referer", "https://www.tiktok.com/")
            .build()
    }

    private fun mediaHeaders(): Headers {
        return Headers.Builder()
            .add("User-Agent", MOBILE_USER_AGENT)
            .add("Accept", "*/*")
            .add("Accept-Language", "en-US,en;q=0.9")
            .add("Referer", "https://www.tiktok.com/")
            .build()
    }

    private fun org.jsoup.nodes.Element.scriptJson(): String {
        return data().ifBlank { html() }.trim()
    }

    private fun String.nullIfBlank(): String? = takeIf { it.isNotBlank() }

    data class PreviewResult(
        val id: String,
        val title: String,
        val description: String?,
        val author: String?,
        val thumbnailUrl: String?,
        val durationSeconds: Long?,
        val width: Int,
        val height: Int,
        val fileSize: Long?
    )

    private data class ResolvedMedia(
        val id: String,
        val title: String?,
        val description: String?,
        val author: String?,
        val thumbnailUrl: String?,
        val durationSeconds: Long?,
        val width: Int,
        val height: Int,
        val directUrl: String,
        val fileSize: Long?
    ) {
        fun toPreviewResult(): PreviewResult {
            return PreviewResult(
                id = id,
                title = title ?: "TikTok Video #$id",
                description = description,
                author = author,
                thumbnailUrl = thumbnailUrl,
                durationSeconds = durationSeconds,
                width = width,
                height = height,
                fileSize = fileSize
            )
        }
    }

    private class InMemoryCookieJar : CookieJar {
        private val cookies = mutableListOf<Cookie>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(this.cookies) {
                cookies.forEach { incoming ->
                    this.cookies.removeAll { existing ->
                        existing.name == incoming.name &&
                            existing.domain == incoming.domain &&
                            existing.path == incoming.path
                    }
                    this.cookies += incoming
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return synchronized(cookies) {
                cookies.filter { it.matches(url) }
            }
        }
    }

    private const val MOBILE_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
}
