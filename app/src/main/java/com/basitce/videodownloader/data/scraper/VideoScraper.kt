package com.basitce.videodownloader.data.scraper

import android.content.Context
import com.basitce.videodownloader.data.LinkResolver
import com.basitce.videodownloader.data.model.AvailableQuality
import com.basitce.videodownloader.data.model.Platform
import com.basitce.videodownloader.data.model.VideoInfo
import com.basitce.videodownloader.data.model.VideoQuality
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoScraper(private val context: Context) {

    suspend fun scrapeVideoInfo(url: String): Result<VideoInfo> = withContext(Dispatchers.IO) {
        try {
            val canonicalUrl = CanonicalUrlResolver.resolve(url)
            val resolved = LinkResolver.resolve(canonicalUrl)
            if (!resolved.isValid) {
                return@withContext Result.failure(Exception("Desteklenmeyen URL"))
            }

            val videoInfo = if (resolved.platform == Platform.TIKTOK) {
                scrapeTikTokVideoInfo(resolved.cleanUrl, resolved)
            } else {
                YtDlpEngine.ensureInitialized(context)
                buildVideoInfoFromProbe(
                    resolved = resolved,
                    probe = fetchInfo(resolved.cleanUrl, resolved.platform)
                )
            }

            Result.success(videoInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchInfo(url: String, platform: Platform): ProbeResult {
        return when (platform) {
            Platform.YOUTUBE -> fetchYoutubeInfo(url)
            Platform.TIKTOK -> fetchTikTokInfo(url)
            else -> {
                val info = executeInfoRequest(url, null)
                ProbeResult(
                    resolvedUrl = info.webpageUrl ?: url,
                    info = info,
                    formatProbes = info.formats.orEmpty().map { FormatProbe(it, null) }
                )
            }
        }
    }

    private fun fetchTikTokInfo(url: String): ProbeResult {
        val attempts = listOf(
            TikTokSessionConfig.extractorArgs(context),
            null
        )

        var lastError: Throwable? = null
        for (extractorArgs in attempts) {
            try {
                val info = executeInfoRequest(url, extractorArgs)
                return ProbeResult(
                    resolvedUrl = info.webpageUrl ?: url,
                    info = info,
                    formatProbes = info.formats.orEmpty().map { FormatProbe(it, extractorArgs) }
                )
            } catch (error: Throwable) {
                lastError = error
            }
        }

        throw (lastError ?: IllegalStateException("TikTok video bilgisi alinamadi"))
    }

    private suspend fun scrapeTikTokVideoInfo(
        url: String,
        resolved: LinkResolver.ResolveResult
    ): VideoInfo {
        return try {
            YtDlpEngine.ensureInitialized(context)
            val probe = fetchTikTokInfo(url)
            if (probe.formatProbes.isEmpty()) {
                throw IllegalStateException("TikTok format listesi bos geldi")
            }

            buildVideoInfoFromProbe(
                resolved = resolved,
                probe = probe
            )
        } catch (_: Exception) {
            buildTikTokFallbackVideoInfo(url, resolved)
        }
    }

    private fun buildVideoInfoFromProbe(
        resolved: LinkResolver.ResolveResult,
        probe: ProbeResult
    ): VideoInfo {
        val availableQualities = buildAvailableQualities(
            platform = resolved.platform,
            formatProbes = probe.formatProbes
        )

        return VideoInfo(
            id = probe.info.id?.ifBlank { null }
                ?: resolved.videoId
                ?: resolved.cleanUrl.hashCode().toString(),
            url = probe.resolvedUrl,
            platform = resolved.platform,
            title = probe.info.title?.takeIf { it.isNotBlank() }
                ?: "${resolved.platform.displayName} Video",
            description = probe.info.description?.takeIf { it.isNotBlank() },
            thumbnailUrl = probe.info.thumbnail?.takeIf { it.isNotBlank() },
            duration = probe.info.duration.takeIf { it > 0 }?.toLong(),
            author = probe.info.uploader?.takeIf { it.isNotBlank() },
            authorAvatarUrl = null,
            availableQualities = availableQualities.ifEmpty {
                listOf(
                    AvailableQuality(
                        quality = defaultPreviewQuality(resolved.platform),
                        fileSize = null,
                        url = qualitySelectorFor(
                            resolved.platform,
                            defaultPreviewQuality(resolved.platform)
                        )
                    )
                )
            }
        )
    }

    private suspend fun buildTikTokFallbackVideoInfo(
        url: String,
        resolved: LinkResolver.ResolveResult
    ): VideoInfo {
        val fallback = TikTokWebFallback.fetchPreview(url)
        val quality = qualityFromDimensions(
            width = fallback.width,
            height = fallback.height
        )

        return VideoInfo(
            id = fallback.id.ifBlank {
                resolved.videoId ?: resolved.cleanUrl.hashCode().toString()
            },
            url = resolved.cleanUrl,
            platform = Platform.TIKTOK,
            title = fallback.title.ifBlank { "TikTok Video" },
            description = fallback.description,
            thumbnailUrl = fallback.thumbnailUrl,
            duration = fallback.durationSeconds,
            author = fallback.author,
            authorAvatarUrl = null,
            availableQualities = listOf(
                AvailableQuality(
                    quality = quality,
                    fileSize = fallback.fileSize,
                    url = TikTokWebFallback.DIRECT_SELECTOR,
                    strictSelection = true
                )
            )
        )
    }

    private fun fetchYoutubeInfo(url: String): ProbeResult {
        var baseInfo: com.yausername.youtubedl_android.mapper.VideoInfo? = null
        var lastError: Throwable? = null
        val mergedFormats = linkedMapOf<String, FormatProbe>()

        for (extractorArgs in youtubeExtractorArgs()) {
            try {
                val info = executeInfoRequest(url, extractorArgs)
                if (baseInfo == null) {
                    baseInfo = info
                }

                info.formats.orEmpty().forEach { format ->
                    mergedFormats.putIfAbsent(
                        format.uniqueKey(extractorArgs),
                        FormatProbe(format, extractorArgs)
                    )
                }
            } catch (error: Throwable) {
                lastError = error
            }
        }

        val finalInfo = baseInfo ?: throw (lastError ?: IllegalStateException("Video bilgisi alinamadi"))
        return ProbeResult(
            resolvedUrl = finalInfo.webpageUrl ?: url,
            info = finalInfo,
            formatProbes = if (mergedFormats.isEmpty()) {
                finalInfo.formats.orEmpty().map { FormatProbe(it, null) }
            } else {
                mergedFormats.values.toList()
            }
        )
    }

    private fun executeInfoRequest(
        url: String,
        extractorArgs: String?
    ): com.yausername.youtubedl_android.mapper.VideoInfo {
        val request = YoutubeDLRequest(url)
            .addOption("--no-playlist")
            .addOption("--no-warnings")
            .addOption("--geo-bypass")
            .addOption("--retries", "3")
            .addOption("--fragment-retries", "3")
            .addOption("--extractor-retries", "3")
            .addOption("--user-agent", MOBILE_USER_AGENT)
            .apply {
                if (!extractorArgs.isNullOrBlank()) {
                    addOption("--extractor-args", extractorArgs)
                }
            }

        return YoutubeDL.getInstance().getInfo(request)
    }

    private fun buildAvailableQualities(
        platform: Platform,
        formatProbes: List<FormatProbe>
    ): List<AvailableQuality> {
        if (formatProbes.isEmpty()) {
            return emptyList()
        }

        return when (platform) {
            Platform.YOUTUBE -> buildYoutubeQualities(formatProbes)
            else -> buildGenericQualities(platform, formatProbes)
        }
    }

    private fun buildGenericQualities(
        platform: Platform,
        formatProbes: List<FormatProbe>
    ): List<AvailableQuality> {
        val videoFormats = formatProbes.filter { isVideoFormat(it.format) }
        val bestSizesByQuality = linkedMapOf<VideoQuality, Long?>()

        videoFormats.asSequence()
            .map { toQuality(it.format) }
            .distinct()
            .sortedBy { it.priority }
            .forEach { quality ->
                val size = videoFormats
                    .filter { toQuality(it.format) == quality }
                    .map { extractFileSize(it.format) }
                    .filterNotNull()
                    .maxOrNull()

                bestSizesByQuality[quality] = size
            }

        val mappedQualities = bestSizesByQuality.entries.map { (quality, size) ->
            AvailableQuality(
                quality = quality,
                fileSize = size,
                url = qualitySelectorFor(platform, quality)
            )
        }.toMutableList()

        val bestAudio = formatProbes
            .filter { isAudioOnlyFormat(it.format) }
            .maxByOrNull { it.format.abr + it.format.tbr }

        if (bestAudio != null) {
            mappedQualities += AvailableQuality(
                quality = VideoQuality.QUALITY_AUDIO_ONLY,
                fileSize = extractFileSize(bestAudio.format),
                url = bestAudioSelector(bestAudio),
                extractorArgs = bestAudio.extractorArgs,
                strictSelection = bestAudio.format.formatId != null
            )
        }

        return mappedQualities
    }

    private fun buildYoutubeQualities(formatProbes: List<FormatProbe>): List<AvailableQuality> {
        val audioByExtractorArgs = formatProbes
            .filter { isAudioOnlyFormat(it.format) && !it.format.formatId.isNullOrBlank() }
            .groupBy { it.extractorArgs }
            .mapValues { (_, probes) ->
                probes.maxByOrNull { scoreAudioFormat(it.format) }
            }

        val mappedQualities = formatProbes
            .filter { isVideoFormat(it.format) && !it.format.formatId.isNullOrBlank() }
            .groupBy { toQuality(it.format) }
            .toSortedMap(compareBy { it.priority })
            .mapNotNull { (quality, probes) ->
                val candidate = probes
                    .mapNotNull { probe ->
                        buildYoutubeCandidate(
                            videoProbe = probe,
                            audioProbe = audioByExtractorArgs[probe.extractorArgs]
                        )
                    }
                    .maxByOrNull { it.score }
                    ?: return@mapNotNull null

                AvailableQuality(
                    quality = quality,
                    fileSize = candidate.fileSize,
                    url = candidate.selector,
                    extractorArgs = candidate.extractorArgs,
                    strictSelection = true
                )
            }
            .toMutableList()

        val bestAudio = audioByExtractorArgs.values
            .filterNotNull()
            .maxByOrNull { scoreAudioFormat(it.format) }

        if (bestAudio != null) {
            mappedQualities += AvailableQuality(
                quality = VideoQuality.QUALITY_AUDIO_ONLY,
                fileSize = extractFileSize(bestAudio.format),
                url = bestAudioSelector(bestAudio),
                extractorArgs = bestAudio.extractorArgs,
                strictSelection = true
            )
        }

        return mappedQualities
    }

    private fun buildYoutubeCandidate(
        videoProbe: FormatProbe,
        audioProbe: FormatProbe?
    ): ExactYoutubeCandidate? {
        val videoId = videoProbe.format.formatId ?: return null
        val videoSize = extractFileSize(videoProbe.format)

        return if (isMuxedFormat(videoProbe.format)) {
            ExactYoutubeCandidate(
                selector = videoId,
                extractorArgs = videoProbe.extractorArgs,
                fileSize = videoSize,
                score = scoreVideoFormat(videoProbe.format) + 5_000
            )
        } else {
            val audioId = audioProbe?.format?.formatId ?: return null
            ExactYoutubeCandidate(
                selector = "$videoId+$audioId",
                extractorArgs = videoProbe.extractorArgs,
                fileSize = sumSizes(videoSize, extractFileSize(audioProbe.format)),
                score = scoreVideoFormat(videoProbe.format) + scoreAudioFormat(audioProbe.format)
            )
        }
    }

    private fun bestAudioSelector(audioProbe: FormatProbe): String {
        val formatId = audioProbe.format.formatId
        return if (!formatId.isNullOrBlank()) {
            formatId
        } else {
            "bestaudio[ext=m4a]/bestaudio/best"
        }
    }

    private fun isVideoFormat(format: VideoFormat): Boolean {
        if (format.vcodec.equals("none", ignoreCase = true)) {
            return false
        }

        if (format.height <= 0 && format.width <= 0) {
            return false
        }

        return true
    }

    private fun isAudioOnlyFormat(format: VideoFormat): Boolean {
        return format.vcodec.equals("none", ignoreCase = true)
    }

    private fun isMuxedFormat(format: VideoFormat): Boolean {
        return !format.acodec.equals("none", ignoreCase = true)
    }

    private fun toQuality(format: VideoFormat): VideoQuality {
        return when {
            format.height >= 2160 -> VideoQuality.QUALITY_4K
            format.height >= 1440 -> VideoQuality.QUALITY_1440P
            format.height >= 1080 -> VideoQuality.QUALITY_1080P
            format.height >= 720 -> VideoQuality.QUALITY_720P
            format.height >= 480 -> VideoQuality.QUALITY_480P
            else -> VideoQuality.QUALITY_360P
        }
    }

    private fun qualityFromDimensions(width: Int, height: Int): VideoQuality {
        return when (maxOf(width, height)) {
            in 2160..Int.MAX_VALUE -> VideoQuality.QUALITY_4K
            in 1440..2159 -> VideoQuality.QUALITY_1440P
            in 1080..1439 -> VideoQuality.QUALITY_1080P
            in 720..1079 -> VideoQuality.QUALITY_720P
            in 480..719 -> VideoQuality.QUALITY_480P
            else -> VideoQuality.QUALITY_360P
        }
    }

    private fun extractFileSize(format: VideoFormat): Long? {
        return when {
            format.fileSize > 0L -> format.fileSize
            format.fileSizeApproximate > 0L -> format.fileSizeApproximate
            else -> null
        }
    }

    private fun qualitySelectorFor(platform: Platform, quality: VideoQuality): String {
        val maxHeight = when (quality) {
            VideoQuality.QUALITY_4K -> "2160"
            VideoQuality.QUALITY_1440P -> "1440"
            VideoQuality.QUALITY_1080P -> "1080"
            VideoQuality.QUALITY_720P -> "720"
            VideoQuality.QUALITY_480P -> "480"
            VideoQuality.QUALITY_360P -> "360"
            VideoQuality.QUALITY_AUDIO_ONLY -> return "bestaudio[ext=m4a]/bestaudio/best"
        }

        return when (platform) {
            Platform.YOUTUBE -> (
                "bestvideo*[height<=$maxHeight]+bestaudio/" +
                    "best*[height<=$maxHeight][ext=mp4]/" +
                    "best*[height<=$maxHeight]/best"
                )

            else -> "best[height<=$maxHeight]/best"
        }
    }

    private fun defaultPreviewQuality(platform: Platform): VideoQuality {
        return when (platform) {
            Platform.YOUTUBE -> VideoQuality.QUALITY_1080P
            else -> VideoQuality.QUALITY_720P
        }
    }

    private fun youtubeExtractorArgs(): List<String?> {
        return listOf(
            "youtube:player_client=default,-web_creator,-web_music",
            "youtube:player_client=android_vr,web_safari,tv_downgraded",
            "youtube:player_client=android_vr,tv",
            null
        )
    }

    private fun scoreVideoFormat(format: VideoFormat): Int {
        var score = format.tbr
        if (format.ext.equals("mp4", ignoreCase = true)) {
            score += 10_000
        }
        if (format.vcodec?.contains("avc", ignoreCase = true) == true) {
            score += 2_000
        }
        return score
    }

    private fun scoreAudioFormat(format: VideoFormat): Int {
        var score = format.abr + format.tbr
        if (format.ext.equals("m4a", ignoreCase = true)) {
            score += 5_000
        }
        return score
    }

    private fun sumSizes(first: Long?, second: Long?): Long? {
        return when {
            first != null && second != null -> first + second
            first != null -> first
            else -> second
        }
    }

    private data class ProbeResult(
        val resolvedUrl: String,
        val info: com.yausername.youtubedl_android.mapper.VideoInfo,
        val formatProbes: List<FormatProbe>
    )

    private data class FormatProbe(
        val format: VideoFormat,
        val extractorArgs: String?
    )

    private data class ExactYoutubeCandidate(
        val selector: String,
        val extractorArgs: String?,
        val fileSize: Long?,
        val score: Int
    )

    private fun VideoFormat.uniqueKey(extractorArgs: String?): String {
        return listOf(
            formatId,
            height.toString(),
            width.toString(),
            vcodec,
            acodec,
            extractorArgs
        ).joinToString("|")
    }

    private companion object {
        const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}
