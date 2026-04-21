package com.basitce.videodownloader.data.scraper

import com.basitce.videodownloader.data.LinkResolver
import com.basitce.videodownloader.data.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object CanonicalUrlResolver {

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun resolve(url: String): String = withContext(Dispatchers.IO) {
        val classified = LinkResolver.classify(url)
        if (!classified.isValidUrl) {
            return@withContext url
        }

        if (classified.platform != Platform.TIKTOK || !needsCanonicalResolution(classified.cleanUrl)) {
            return@withContext classified.cleanUrl
        }

        val request = Request.Builder()
            .url(classified.cleanUrl)
            .get()
            .header("User-Agent", MOBILE_USER_AGENT)
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                normalizeTikTokCanonicalUrl(response.request.url.toString())
            }
        }.getOrDefault(normalizeTikTokCanonicalUrl(classified.cleanUrl))
    }

    private fun needsCanonicalResolution(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("vm.tiktok.com/") ||
            lower.contains("vt.tiktok.com/") ||
            lower.contains("/t/")
    }

    private fun normalizeTikTokCanonicalUrl(url: String): String {
        return url
            .replace("https://m.tiktok.com/", "https://www.tiktok.com/")
            .replace("/@/video/", "/@_/video/")
    }

    private const val MOBILE_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
}
