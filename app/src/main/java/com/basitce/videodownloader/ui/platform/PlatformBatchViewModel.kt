package com.basitce.videodownloader.ui.platform

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.basitce.videodownloader.data.AppPreferences
import com.basitce.videodownloader.data.LinkResolver
import com.basitce.videodownloader.data.model.DownloadItem
import com.basitce.videodownloader.data.model.DownloadProfile
import com.basitce.videodownloader.data.model.DownloadSelectorFactory
import com.basitce.videodownloader.data.model.DownloadStatus
import com.basitce.videodownloader.data.model.Platform
import com.basitce.videodownloader.data.repository.DownloadRepository
import com.basitce.videodownloader.service.DownloadManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Serializable

class PlatformBatchViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val appPreferences = AppPreferences(application)
    private val downloadManager = DownloadManager.getInstance(application)
    private val downloadRepository = DownloadRepository(application)

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private var batchObservationJob: Job? = null

    private val restoredState = PlatformBatchPlanner.restoreSnapshot(
        pendingLinks = savedStateHandle.get<ArrayList<PendingBatchLink>>(KEY_PENDING_LINKS)
            ?.toList()
            .orEmpty(),
        activeBatchIds = savedStateHandle.get<LongArray>(KEY_ACTIVE_BATCH_IDS)
            ?.toList()
            .orEmpty(),
        selectedProfileName = savedStateHandle.get<String>(KEY_SELECTED_PROFILE),
        inputText = savedStateHandle.get<String>(KEY_INPUT_TEXT).orEmpty(),
        fallbackProfile = appPreferences.defaultDownloadProfile
    )

    private val _uiState = MutableStateFlow(
        PlatformBatchUiState(
            inputText = restoredState.inputText,
            pendingLinks = restoredState.pendingLinks,
            linkCountText = PlatformBatchPlanner.readyCountText(restoredState.pendingLinks.size),
            selectedProfile = restoredState.selectedProfile,
            activeBatchIds = restoredState.activeBatchIds,
            batchSummaryText = PlatformBatchPlanner.batchSummaryText(
                downloads = emptyList(),
                activeBatchIds = restoredState.activeBatchIds
            )
        )
    )
    val uiState: StateFlow<PlatformBatchUiState> = _uiState.asStateFlow()

    init {
        persistState(_uiState.value)
        if (restoredState.activeBatchIds.isNotEmpty()) {
            observeBatch(restoredState.activeBatchIds)
        }
    }

    fun onInputTextChanged(text: String) {
        updateState { current ->
            current.copy(inputText = text)
        }
    }

    fun onProfileSelected(profile: DownloadProfile) {
        updateState { current ->
            current.copy(selectedProfile = profile)
        }
    }

    fun addLinksFromCurrentInput(showEmptyFeedback: Boolean = true) {
        addLinksFromText(_uiState.value.inputText, showEmptyFeedback)
    }

    fun addLinksFromText(rawText: String, showEmptyFeedback: Boolean = true) {
        val extractedLinks = PlatformBatchPlanner.extractPendingLinks(rawText)
        if (extractedLinks.isEmpty()) {
            if (showEmptyFeedback) {
                _events.tryEmit("Ayıklanabilir yeni bağlantı bulunamadı.")
            }
            return
        }

        val mergedLinks = PlatformBatchPlanner.mergePendingLinks(
            existing = _uiState.value.pendingLinks,
            incoming = extractedLinks
        )

        if (mergedLinks.size == _uiState.value.pendingLinks.size) {
            if (showEmptyFeedback) {
                _events.tryEmit("Eklenebilecek yeni bağlantı bulunamadı.")
            }
            return
        }

        updateState { current ->
            current.copy(
                inputText = "",
                pendingLinks = mergedLinks,
                linkCountText = PlatformBatchPlanner.readyCountText(mergedLinks.size)
            )
        }
    }

    fun consumeSharedUrls(urls: List<String>) {
        if (urls.isEmpty()) {
            return
        }

        val incomingLinks = urls.mapNotNull { url ->
            PlatformBatchPlanner.pendingLinkFromUrl(url)
        }
        if (incomingLinks.isEmpty()) {
            return
        }

        val mergedLinks = PlatformBatchPlanner.mergePendingLinks(
            existing = _uiState.value.pendingLinks,
            incoming = incomingLinks
        )
        updateState { current ->
            current.copy(
                pendingLinks = mergedLinks,
                linkCountText = PlatformBatchPlanner.readyCountText(mergedLinks.size)
            )
        }
    }

    fun removePendingLink(url: String) {
        val updatedLinks = _uiState.value.pendingLinks.filterNot { it.url == url }
        updateState { current ->
            current.copy(
                pendingLinks = updatedLinks,
                linkCountText = PlatformBatchPlanner.readyCountText(updatedLinks.size)
            )
        }
    }

    fun startBatch() {
        val currentState = _uiState.value
        if (currentState.pendingLinks.isEmpty()) {
            _events.tryEmit("Önce en az bir bağlantı ekle.")
            return
        }

        viewModelScope.launch {
            updateState { current ->
                current.copy(isStartingBatch = true)
            }

            try {
                appPreferences.defaultDownloadProfile = currentState.selectedProfile

                val requests = PlatformBatchPlanner.buildQueuedRequests(
                    links = currentState.pendingLinks,
                    profile = currentState.selectedProfile
                )

                val ids = downloadManager.enqueueBatch(requests)
                updateState { current ->
                    current.copy(
                        inputText = "",
                        pendingLinks = emptyList(),
                        linkCountText = PlatformBatchPlanner.readyCountText(0),
                        activeBatchIds = ids,
                        batchDownloads = emptyList(),
                        batchSummaryText = PlatformBatchPlanner.batchSummaryText(
                            downloads = emptyList(),
                            activeBatchIds = ids
                        ),
                        isStartingBatch = false
                    )
                }

                observeBatch(ids)
                _events.tryEmit("${ids.size} bağlantı kuyruğa eklendi.")
            } catch (error: Exception) {
                updateState { current ->
                    current.copy(isStartingBatch = false)
                }
                val suffix = error.message?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "."
                _events.tryEmit("Toplu indirme başlatılamadı$suffix")
            }
        }
    }

    private fun observeBatch(ids: List<Long>) {
        batchObservationJob?.cancel()

        if (ids.isEmpty()) {
            updateState { current ->
                current.copy(
                    activeBatchIds = emptyList(),
                    batchDownloads = emptyList(),
                    batchSummaryText = PlatformBatchPlanner.batchSummaryText(
                        downloads = emptyList(),
                        activeBatchIds = emptyList()
                    )
                )
            }
            return
        }

        batchObservationJob = viewModelScope.launch {
            downloadRepository.observeDownloadsByIds(ids).collectLatest { downloads ->
                updateState { current ->
                    current.copy(
                        batchDownloads = downloads,
                        batchSummaryText = PlatformBatchPlanner.batchSummaryText(
                            downloads = downloads,
                            activeBatchIds = ids
                        )
                    )
                }
            }
        }
    }

    private fun updateState(transform: (PlatformBatchUiState) -> PlatformBatchUiState) {
        _uiState.update { current ->
            val updated = transform(current)
            persistState(updated)
            updated
        }
    }

    private fun persistState(state: PlatformBatchUiState) {
        savedStateHandle[KEY_PENDING_LINKS] = ArrayList(state.pendingLinks)
        savedStateHandle[KEY_ACTIVE_BATCH_IDS] = state.activeBatchIds.toLongArray()
        savedStateHandle[KEY_SELECTED_PROFILE] = state.selectedProfile.name
        savedStateHandle[KEY_INPUT_TEXT] = state.inputText
    }

    private companion object {
        const val KEY_PENDING_LINKS = "platform_batch_pending_links"
        const val KEY_ACTIVE_BATCH_IDS = "platform_batch_active_ids"
        const val KEY_SELECTED_PROFILE = "platform_batch_selected_profile"
        const val KEY_INPUT_TEXT = "platform_batch_input_text"
    }
}

data class PlatformBatchUiState(
    val inputText: String = "",
    val pendingLinks: List<PendingBatchLink> = emptyList(),
    val linkCountText: String = PlatformBatchPlanner.readyCountText(0),
    val selectedProfile: DownloadProfile = DownloadProfile.MAX,
    val activeBatchIds: List<Long> = emptyList(),
    val batchDownloads: List<DownloadItem> = emptyList(),
    val batchSummaryText: String = PlatformBatchPlanner.batchSummaryText(emptyList(), emptyList()),
    val isStartingBatch: Boolean = false
)

data class PendingBatchLink(
    val url: String,
    val platform: Platform,
    val videoId: String? = null
) : Serializable {

    fun sourceLabel(): String {
        return videoId?.let { "Video kimliği: $it" } ?: "Kaynak bağlantı hazır"
    }

    fun shortUrl(maxLength: Int = 54): String {
        val compact = url
            .removePrefix("https://")
            .removePrefix("http://")

        return if (compact.length <= maxLength) {
            compact
        } else {
            compact.take(maxLength - 1) + "…"
        }
    }
}

data class RestoredPlatformBatchState(
    val pendingLinks: List<PendingBatchLink>,
    val activeBatchIds: List<Long>,
    val selectedProfile: DownloadProfile,
    val inputText: String
)

internal object PlatformBatchPlanner {

    fun pendingLinkFromUrl(url: String): PendingBatchLink? {
        val classified = LinkResolver.classify(url)
        if (!classified.isValidUrl) {
            return null
        }

        return PendingBatchLink(
            url = classified.cleanUrl,
            platform = classified.platform,
            videoId = classified.videoId
        )
    }

    fun extractPendingLinks(rawText: String): List<PendingBatchLink> {
        return LinkResolver.extractUrlsFromText(rawText)
            .mapNotNull(::pendingLinkFromUrl)
    }

    fun mergePendingLinks(
        existing: List<PendingBatchLink>,
        incoming: List<PendingBatchLink>
    ): List<PendingBatchLink> {
        val ordered = LinkedHashMap<String, PendingBatchLink>()
        existing.forEach { ordered[it.url] = it }
        incoming.forEach { ordered.putIfAbsent(it.url, it) }
        return ordered.values.toList()
    }

    fun readyCountText(count: Int): String {
        return when (count) {
            0 -> "Hazır bağlantı yok"
            1 -> "1 bağlantı hazır"
            else -> "$count bağlantı hazır"
        }
    }

    fun buildQueuedRequests(
        links: List<PendingBatchLink>,
        profile: DownloadProfile
    ): List<DownloadManager.QueuedDownloadRequest> {
        return links.map { link ->
            val fileSeed = link.videoId
                ?: link.url.hashCode().toString().replace("-", "")
            val fileName = "${link.platform.shortName.lowercase()}-$fileSeed"

            DownloadManager.QueuedDownloadRequest(
                sourceUrl = link.url,
                downloadSelector = DownloadSelectorFactory.selectorForProfile(link.platform, profile),
                platform = link.platform,
                title = "${link.platform.displayName} videosu",
                thumbnailUrl = null,
                quality = DownloadSelectorFactory.fallbackQuality(profile),
                downloadProfile = profile,
                fileName = fileName
            )
        }
    }

    fun batchSummaryText(
        downloads: List<DownloadItem>,
        activeBatchIds: List<Long>
    ): String {
        if (activeBatchIds.isEmpty()) {
            return "Henüz toplu indirme oturumu yok."
        }

        if (downloads.isEmpty()) {
            return "Kuyruk hazırlanıyor. İlk durum satırları birazdan görünecek."
        }

        val pending = downloads.count { it.status == DownloadStatus.PENDING }
        val downloading = downloads.count { it.status == DownloadStatus.DOWNLOADING }
        val completed = downloads.count { it.status == DownloadStatus.COMPLETED }
        val failed = downloads.count { it.status == DownloadStatus.FAILED }
        val paused = downloads.count { it.status == DownloadStatus.PAUSED }

        return buildString {
            append("${downloads.size} öğe")
            append(" | $pending bekliyor")
            append(" | $downloading indiriliyor")
            append(" | $completed tamamlandı")
            append(" | $failed hata")
            if (paused > 0) {
                append(" | $paused duraklatıldı")
            }
        }
    }

    fun restoreSnapshot(
        pendingLinks: List<PendingBatchLink>,
        activeBatchIds: List<Long>,
        selectedProfileName: String?,
        inputText: String,
        fallbackProfile: DownloadProfile
    ): RestoredPlatformBatchState {
        val selectedProfile = selectedProfileName
            ?.let { name -> DownloadProfile.entries.firstOrNull { it.name == name } }
            ?: fallbackProfile

        return RestoredPlatformBatchState(
            pendingLinks = pendingLinks,
            activeBatchIds = activeBatchIds,
            selectedProfile = selectedProfile,
            inputText = inputText
        )
    }
}
