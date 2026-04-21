package com.basitce.videodownloader.data.scraper

import android.content.Context
import android.os.Environment
import com.basitce.videodownloader.data.LinkResolver
import com.basitce.videodownloader.data.model.Platform
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.io.File

class VideoDownloader(private val context: Context) {

    data class DownloadResult(
        val filePath: String,
        val fileSize: Long
    )

    suspend fun downloadVideo(
        sourceUrl: String,
        formatSelector: String,
        extractorArgs: String? = null,
        strictSelection: Boolean = false,
        fileName: String,
        onProgress: (Int) -> Unit = {}
    ): Result<DownloadResult> = withContext(Dispatchers.IO) {
        val normalizedSourceUrl = CanonicalUrlResolver.resolve(sourceUrl)
        val safeFileStem = buildSafeFileStem(fileName)
        val outputDir = getOutputDirectory()
        val platform = LinkResolver.detectPlatform(normalizedSourceUrl)

        try {
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            if (platform == Platform.TIKTOK && formatSelector == TikTokWebFallback.DIRECT_SELECTOR) {
                val directFile = File(outputDir, "$safeFileStem.mp4")
                val directSize = TikTokWebFallback.downloadToFile(
                    pageUrl = normalizedSourceUrl,
                    destination = directFile,
                    onProgress = onProgress
                )

                return@withContext Result.success(
                    DownloadResult(
                        filePath = directFile.absolutePath,
                        fileSize = directSize
                    )
                )
            }

            YtDlpEngine.ensureInitialized(context)

            val outputTemplate = File(outputDir, "$safeFileStem.%(ext)s").absolutePath
            val processId = "download-$safeFileStem"
            val job = currentCoroutineContext().job

            val cancelHandler = job.invokeOnCompletion { cause ->
                if (cause is CancellationException) {
                    YoutubeDL.getInstance().destroyProcessById(processId)
                }
            }

            try {
                var lastError: Exception? = null

                for (attempt in buildAttempts(normalizedSourceUrl, formatSelector, extractorArgs, strictSelection)) {
                    try {
                        val request = buildDownloadRequest(
                            sourceUrl = normalizedSourceUrl,
                            selector = attempt.selector,
                            outputTemplate = outputTemplate,
                            extractorArgs = attempt.extractorArgs,
                            audioOnly = attempt.audioOnly
                        )

                        YoutubeDL.getInstance().execute(request, processId) { progress, _, _ ->
                            if (progress >= 0f) {
                                onProgress(progress.toInt().coerceIn(0, 99))
                            }
                        }

                        val downloadedFile = outputDir.listFiles()
                            ?.filter { it.isFile && it.name.startsWith(safeFileStem) }
                            ?.maxByOrNull { it.lastModified() }
                            ?: throw IllegalStateException("Indirilen dosya bulunamadi")

                        onProgress(100)
                        return@withContext Result.success(
                            DownloadResult(
                                filePath = downloadedFile.absolutePath,
                                fileSize = downloadedFile.length()
                            )
                        )
                    } catch (e: YoutubeDL.CanceledException) {
                        throw CancellationException("Indirme iptal edildi", e)
                    } catch (e: Exception) {
                        lastError = e
                    }
                }

                if (platform == Platform.TIKTOK) {
                    val directFile = File(outputDir, "$safeFileStem.mp4")
                    val directSize = TikTokWebFallback.downloadToFile(
                        pageUrl = normalizedSourceUrl,
                        destination = directFile,
                        onProgress = onProgress
                    )

                    return@withContext Result.success(
                        DownloadResult(
                            filePath = directFile.absolutePath,
                            fileSize = directSize
                        )
                    )
                }

                Result.failure(lastError ?: IllegalStateException("Indirme basarisiz oldu"))
            } finally {
                cancelHandler.dispose()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildAttempts(
        sourceUrl: String,
        preferredSelector: String,
        extractorArgs: String?,
        strictSelection: Boolean
    ): List<DownloadAttempt> {
        val platform = LinkResolver.detectPlatform(sourceUrl)
        val maxHeight = Regex("height<=([0-9]+)")
            .find(preferredSelector)
            ?.groupValues
            ?.getOrNull(1)

        val audioOnly = preferredSelector.contains("bestaudio", ignoreCase = true) &&
            !preferredSelector.contains("bestvideo", ignoreCase = true)

        val extractorArgsCandidates = extractorArgsCandidates(platform, extractorArgs)

        if (strictSelection) {
            return listOf(
                DownloadAttempt(
                    selector = preferredSelector,
                    extractorArgs = extractorArgsCandidates.firstOrNull(),
                    audioOnly = audioOnly
                )
            )
        }

        val selectors = linkedSetOf<String>()
        if (preferredSelector.isNotBlank()) {
            selectors += preferredSelector
        }

        when (platform) {
            Platform.YOUTUBE -> {
                if (audioOnly) {
                    selectors += "bestaudio[ext=m4a]/bestaudio/best"
                } else {
                    if (maxHeight != null) {
                        selectors += "bestvideo*[height<=$maxHeight]+bestaudio/best*[height<=$maxHeight][ext=mp4]/best*[height<=$maxHeight]/best"
                        selectors += "bestvideo*[height<=$maxHeight]+bestaudio/best*[height<=$maxHeight]/best"
                        selectors += "best[height<=$maxHeight][ext=mp4]/best[height<=$maxHeight]/best"
                    }
                    selectors += "bestvideo*+bestaudio/best[ext=mp4]/best"
                    selectors += "best"
                }
            }

            else -> {
                if (audioOnly) {
                    selectors += "bestaudio/best"
                } else {
                    if (maxHeight != null) {
                        selectors += "best[height<=$maxHeight]/best"
                    }
                    selectors += "best"
                }
            }
        }

        return buildList {
            extractorArgsCandidates.forEach { args ->
                selectors.forEach { selector ->
                    add(
                        DownloadAttempt(
                            selector = selector,
                            extractorArgs = args,
                            audioOnly = audioOnly
                        )
                    )
                }
            }
        }
    }

    private fun buildSafeFileStem(fileName: String): String {
        val sanitized = fileName
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "video" }
            .take(60)

        return "$sanitized-${System.currentTimeMillis()}"
    }

    @Suppress("DEPRECATION")
    private fun getOutputDirectory(): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(downloadsDir, "VideoDownloader")
    }

    private fun buildDownloadRequest(
        sourceUrl: String,
        selector: String,
        outputTemplate: String,
        extractorArgs: String?,
        audioOnly: Boolean
    ): YoutubeDLRequest {
        val platform = LinkResolver.detectPlatform(sourceUrl)
        val resolvedExtractorArgs = extractorArgs ?: defaultExtractorArgs(platform)

        return YoutubeDLRequest(sourceUrl)
            .addOption("--no-playlist")
            .addOption("--newline")
            .addOption("--progress")
            .addOption("--no-part")
            .addOption("--no-warnings")
            .addOption("--geo-bypass")
            .addOption("--retries", "3")
            .addOption("--fragment-retries", "3")
            .addOption("--extractor-retries", "3")
            .addOption("--output", outputTemplate)
            .addOption("--user-agent", MOBILE_USER_AGENT)
            .apply {
                if (!resolvedExtractorArgs.isNullOrBlank()) {
                    addOption("--extractor-args", resolvedExtractorArgs)
                }

                if (platform == Platform.TIKTOK) {
                    addOption("--referer", "https://www.tiktok.com/")
                }

                if (audioOnly) {
                    addOption("--extract-audio")
                    addOption("--audio-format", "mp3")
                } else {
                    addOption("--merge-output-format", "mp4")
                }
            }
            .addOption("-f", selector)
    }

    private fun defaultExtractorArgs(platform: Platform): String? {
        return when (platform) {
            Platform.YOUTUBE -> "youtube:player_client=default,-web_creator,-web_music"
            Platform.TIKTOK -> TikTokSessionConfig.extractorArgs(context)
            else -> null
        }
    }

    private fun extractorArgsCandidates(platform: Platform, explicit: String?): List<String?> {
        if (!explicit.isNullOrBlank()) {
            return listOf(explicit)
        }

        return when (platform) {
            Platform.YOUTUBE -> listOf(
                "youtube:player_client=default,-web_creator,-web_music",
                "youtube:player_client=android_vr,web_safari,tv_downgraded",
                "youtube:player_client=android_vr,tv",
                null
            )

            Platform.TIKTOK -> listOf(
                TikTokSessionConfig.extractorArgs(context),
                null
            )

            else -> listOf(null)
        }
    }

    private data class DownloadAttempt(
        val selector: String,
        val extractorArgs: String?,
        val audioOnly: Boolean
    )

    private companion object {
        const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}
