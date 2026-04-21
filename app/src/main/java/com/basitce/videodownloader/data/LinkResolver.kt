package com.basitce.videodownloader.data

import android.net.Uri
import com.basitce.videodownloader.data.model.Platform
import java.util.Locale

object LinkResolver {

    data class ResolveResult(
        val platform: Platform,
        val videoId: String?,
        val cleanUrl: String,
        val isValid: Boolean
    )

    data class ClassifiedLink(
        val platform: Platform,
        val videoId: String?,
        val cleanUrl: String,
        val isValidUrl: Boolean,
        val isSupportedPlatform: Boolean
    )

    private val instagramIdPatterns = listOf(
        Regex("/(?:p|reel|reels|tv|share)/([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE),
        Regex("/stories/[^/]+/([0-9]+)", RegexOption.IGNORE_CASE)
    )

    private val tikTokIdPatterns = listOf(
        Regex("/@[^/]+/video/([0-9]+)", RegexOption.IGNORE_CASE),
        Regex("/share/video/([0-9]+)", RegexOption.IGNORE_CASE),
        Regex("/v/([0-9]+)", RegexOption.IGNORE_CASE),
        Regex("/t/([A-Za-z0-9]+)", RegexOption.IGNORE_CASE)
    )

    private val twitterIdPatterns = listOf(
        Regex("/status/([0-9]+)", RegexOption.IGNORE_CASE)
    )

    private val youtubeIdPatterns = listOf(
        Regex("[?&]v=([A-Za-z0-9_-]{11})", RegexOption.IGNORE_CASE),
        Regex("/shorts/([A-Za-z0-9_-]{11})", RegexOption.IGNORE_CASE),
        Regex("/live/([A-Za-z0-9_-]{11})", RegexOption.IGNORE_CASE),
        Regex("/embed/([A-Za-z0-9_-]{11})", RegexOption.IGNORE_CASE),
        Regex("youtu\\.be/([A-Za-z0-9_-]{11})", RegexOption.IGNORE_CASE)
    )

    private val facebookIdPatterns = listOf(
        Regex("[?&]v=([0-9]+)", RegexOption.IGNORE_CASE),
        Regex("/videos/([0-9]+)", RegexOption.IGNORE_CASE),
        Regex("/reel/([0-9]+)", RegexOption.IGNORE_CASE),
        Regex("/share/v/([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE),
        Regex("fb\\.watch/([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE)
    )

    private val pinterestIdPatterns = listOf(
        Regex("/pin/([0-9]+)", RegexOption.IGNORE_CASE),
        Regex("pin\\.it/([A-Za-z0-9]+)", RegexOption.IGNORE_CASE)
    )

    private val urlPattern = Regex(
        pattern = """((?:https?://|//|www\.)[^\s<>()]+|[A-Za-z0-9.-]+\.(?:com|be|it|watch|gg|tv|me|net|org)/[^\s<>()]+)""",
        option = RegexOption.IGNORE_CASE
    )

    fun resolve(url: String): ResolveResult {
        val classified = classify(url)
        return ResolveResult(
            platform = classified.platform,
            videoId = classified.videoId,
            cleanUrl = classified.cleanUrl,
            isValid = classified.isSupportedPlatform
        )
    }

    fun classify(url: String): ClassifiedLink {
        val cleanUrl = normalizeUrl(url)
        val host = parseHost(cleanUrl)
        if (host == null) {
            return ClassifiedLink(
                platform = Platform.UNKNOWN,
                videoId = null,
                cleanUrl = cleanUrl,
                isValidUrl = false,
                isSupportedPlatform = false
            )
        }

        val platform = detectPlatformByHost(host) ?: Platform.UNKNOWN
        val parsed = Uri.parse(cleanUrl)
        val isValidUrl = !parsed.scheme.isNullOrBlank() && !parsed.host.isNullOrBlank()

        return ClassifiedLink(
            platform = platform,
            videoId = extractVideoId(platform, cleanUrl),
            cleanUrl = cleanUrl,
            isValidUrl = isValidUrl,
            isSupportedPlatform = platform != Platform.UNKNOWN
        )
    }

    fun isSupported(url: String): Boolean = classify(url).isSupportedPlatform

    fun isValidUrl(url: String): Boolean = classify(url).isValidUrl

    fun detectPlatform(url: String): Platform = classify(url).platform

    fun extractUrlFromText(text: String): String? {
        val urls = extractUrlsFromText(text)
        return urls.firstOrNull(::isSupported) ?: urls.firstOrNull()
    }

    fun extractUrlsFromText(text: String): List<String> {
        return urlPattern.findAll(text)
            .map { it.value.trim().trimEnd('.', ',', ';', ')', ']') }
            .map(::normalizeUrl)
            .filter(::isValidUrl)
            .distinct()
            .toList()
    }

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

    fun getSupportedFormats(): Map<Platform, List<String>> {
        return mapOf(
            Platform.INSTAGRAM to listOf(
                "instagram.com/reel/xxx",
                "instagram.com/p/xxx",
                "instagram.com/share/xxx",
                "instagr.am/reel/xxx"
            ),
            Platform.TIKTOK to listOf(
                "tiktok.com/@user/video/xxx",
                "vm.tiktok.com/xxx",
                "vt.tiktok.com/xxx",
                "tiktok.com/t/xxx"
            ),
            Platform.TWITTER to listOf(
                "x.com/user/status/xxx",
                "twitter.com/user/status/xxx",
                "vxtwitter.com/user/status/xxx",
                "mobile.twitter.com/user/status/xxx"
            ),
            Platform.YOUTUBE to listOf(
                "youtube.com/watch?v=xxx",
                "youtu.be/xxx",
                "youtube.com/shorts/xxx",
                "music.youtube.com/watch?v=xxx"
            ),
            Platform.FACEBOOK to listOf(
                "facebook.com/watch/?v=xxx",
                "facebook.com/user/videos/xxx",
                "facebook.com/reel/xxx",
                "fb.watch/xxx"
            ),
            Platform.PINTEREST to listOf(
                "pinterest.com/pin/xxx",
                "tr.pinterest.com/pin/xxx",
                "pin.it/xxx"
            )
        )
    }

    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            return trimmed
        }

        val withScheme = when {
            trimmed.startsWith("http://", true) ||
                trimmed.startsWith("https://", true) -> trimmed

            trimmed.startsWith("//") -> "https:$trimmed"
            else -> "https://$trimmed"
        }

        return withScheme.replaceFirst("http://", "https://")
    }

    private fun parseHost(url: String): String? {
        val host = runCatching { Uri.parse(url).host }.getOrNull()
        return host?.lowercase(Locale.US)?.removePrefix("www.")
    }

    private fun detectPlatformByHost(host: String): Platform? {
        return when {
            host == "instagram.com" || host.endsWith(".instagram.com") || host == "instagr.am" ->
                Platform.INSTAGRAM

            host == "tiktok.com" || host.endsWith(".tiktok.com") ->
                Platform.TIKTOK

            host == "x.com" ||
                host.endsWith(".x.com") ||
                host == "twitter.com" ||
                host.endsWith(".twitter.com") ||
                host == "vxtwitter.com" ||
                host.endsWith(".vxtwitter.com") ||
                host == "fxtwitter.com" ||
                host.endsWith(".fxtwitter.com") ||
                host.startsWith("nitter.") ->
                Platform.TWITTER

            host == "youtube.com" ||
                host.endsWith(".youtube.com") ||
                host == "youtu.be" ||
                host == "youtube-nocookie.com" ||
                host.endsWith(".youtube-nocookie.com") ->
                Platform.YOUTUBE

            host == "facebook.com" ||
                host.endsWith(".facebook.com") ||
                host == "fb.watch" ||
                host == "fb.gg" ->
                Platform.FACEBOOK

            host == "pin.it" ||
                host == "pinterest.com" ||
                host.endsWith(".pinterest.com") ||
                host.contains("pinterest.") ->
                Platform.PINTEREST

            else -> null
        }
    }

    private fun extractVideoId(platform: Platform, url: String): String? {
        val patterns = when (platform) {
            Platform.INSTAGRAM -> instagramIdPatterns
            Platform.TIKTOK -> tikTokIdPatterns
            Platform.TWITTER -> twitterIdPatterns
            Platform.YOUTUBE -> youtubeIdPatterns
            Platform.FACEBOOK -> facebookIdPatterns
            Platform.PINTEREST -> pinterestIdPatterns
            Platform.UNKNOWN -> emptyList()
        }

        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(url)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
        }
    }
}
