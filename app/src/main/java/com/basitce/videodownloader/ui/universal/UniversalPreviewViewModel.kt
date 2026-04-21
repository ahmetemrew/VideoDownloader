package com.basitce.videodownloader.ui.universal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.basitce.videodownloader.data.model.AvailableQuality
import com.basitce.videodownloader.data.model.VideoInfo
import com.basitce.videodownloader.data.scraper.VideoScraper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UniversalPreviewViewModel(application: Application) : AndroidViewModel(application) {

    private val videoScraper = VideoScraper(application.applicationContext)

    private val _state = MutableStateFlow(UniversalPreviewState())
    val state: StateFlow<UniversalPreviewState> = _state.asStateFlow()

    fun setInputUrl(url: String) {
        _state.update { current ->
            if (current.inputUrl == url) current else current.copy(inputUrl = url)
        }
    }

    fun fetchVideoInfo(url: String = _state.value.inputUrl) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) {
            return
        }

        _state.update { current ->
            current.copy(
                inputUrl = trimmedUrl,
                isLoading = true,
                videoInfo = null,
                selectedQualitySelector = null,
                fileName = "",
                error = null
            )
        }

        viewModelScope.launch {
            val result = videoScraper.scrapeVideoInfo(trimmedUrl)
            result.onSuccess { info ->
                val defaultQuality = info.getDefaultQuality() ?: info.getBestQuality()
                _state.update { current ->
                    current.copy(
                        inputUrl = trimmedUrl,
                        isLoading = false,
                        videoInfo = info,
                        selectedQualitySelector = defaultQuality?.url,
                        fileName = sanitizeFileName(info.title),
                        error = null
                    )
                }
            }.onFailure { error ->
                _state.update { current ->
                    current.copy(
                        inputUrl = trimmedUrl,
                        isLoading = false,
                        videoInfo = null,
                        selectedQualitySelector = null,
                        error = error
                    )
                }
            }
        }
    }

    fun selectQuality(quality: AvailableQuality) {
        _state.update { current ->
            current.copy(selectedQualitySelector = quality.url)
        }
    }

    fun updateFileName(fileName: String) {
        _state.update { current ->
            current.copy(fileName = fileName)
        }
    }

    fun clearError() {
        _state.update { current ->
            if (current.error == null) current else current.copy(error = null)
        }
    }

    fun reset() {
        _state.value = UniversalPreviewState()
    }

    fun selectedQuality(): AvailableQuality? {
        val current = _state.value
        return current.videoInfo?.availableQualities?.firstOrNull { it.url == current.selectedQualitySelector }
    }

    private fun sanitizeFileName(title: String): String {
        return title
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(50)
    }
}

data class UniversalPreviewState(
    val inputUrl: String = "",
    val isLoading: Boolean = false,
    val videoInfo: VideoInfo? = null,
    val selectedQualitySelector: String? = null,
    val fileName: String = "",
    val error: Throwable? = null
)
