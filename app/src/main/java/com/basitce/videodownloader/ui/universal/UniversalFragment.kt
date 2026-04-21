package com.basitce.videodownloader.ui.universal

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.basitce.videodownloader.MainActivity
import com.basitce.videodownloader.R
import com.basitce.videodownloader.data.AppPreferences
import com.basitce.videodownloader.data.LinkResolver
import com.basitce.videodownloader.data.model.AvailableQuality
import com.basitce.videodownloader.data.model.DownloadProfile
import com.basitce.videodownloader.data.model.VideoInfo
import com.basitce.videodownloader.databinding.FragmentUniversalBinding
import com.basitce.videodownloader.service.DownloadManager
import com.basitce.videodownloader.service.NotificationHelper
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class UniversalFragment : Fragment() {

    private var _binding: FragmentUniversalBinding? = null
    private val binding get() = _binding!!

    private val previewViewModel: UniversalPreviewViewModel by activityViewModels()

    private lateinit var downloadManager: DownloadManager
    private lateinit var appPreferences: AppPreferences

    private var isRenderingState = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUniversalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        downloadManager = DownloadManager.getInstance(requireContext())
        appPreferences = AppPreferences(requireContext())

        NotificationHelper.createNotificationChannels(requireContext())

        setupUI()
        observePreviewState()
        checkPendingUrl()

        if (appPreferences.autoPasteEnabled && appPreferences.isFirstLaunch) {
            appPreferences.isFirstLaunch = false
            autoPasteFromClipboard()
        }
    }

    override fun onResume() {
        super.onResume()
        checkPendingUrl()

        if (appPreferences.autoPasteEnabled && previewViewModel.state.value.inputUrl.isBlank()) {
            autoPasteFromClipboard()
        }
    }

    private fun setupUI() {
        binding.urlInput.doAfterTextChanged { text ->
            if (isRenderingState) {
                return@doAfterTextChanged
            }

            previewViewModel.setInputUrl(text?.toString().orEmpty())
            updatePlatformIndicator(text?.toString().orEmpty())
        }

        binding.urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                fetchVideoInfo()
                true
            } else {
                false
            }
        }

        binding.filenameInput.doAfterTextChanged { text ->
            if (isRenderingState) {
                return@doAfterTextChanged
            }
            previewViewModel.updateFileName(text?.toString().orEmpty())
        }

        binding.btnPaste.setOnClickListener { pasteFromClipboard() }
        binding.btnFetch.setOnClickListener { fetchVideoInfo() }
        binding.btnDownload.setOnClickListener { startDownload() }
    }

    private fun observePreviewState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                previewViewModel.state.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(state: UniversalPreviewState) {
        isRenderingState = true
        try {
            if (binding.urlInput.text?.toString().orEmpty() != state.inputUrl) {
                binding.urlInput.setText(state.inputUrl)
                binding.urlInput.setSelection(binding.urlInput.text?.length ?: 0)
            }

            if (binding.filenameInput.text?.toString().orEmpty() != state.fileName) {
                binding.filenameInput.setText(state.fileName)
                binding.filenameInput.setSelection(binding.filenameInput.text?.length ?: 0)
            }

            updatePlatformIndicator(state.inputUrl)
            showLoading(state.isLoading)

            if (state.videoInfo != null) {
                displayVideoInfo(state.videoInfo, state.selectedQualitySelector)
            } else if (!state.isLoading) {
                binding.videoPreviewCard.isVisible = false
            }

            state.error?.let { error ->
                showError(formatFetchErrorMessage(error))
                previewViewModel.clearError()
            }
        } finally {
            isRenderingState = false
        }
    }

    private fun checkPendingUrl() {
        val activity = activity as? MainActivity ?: return
        val pendingUrl = activity.consumePendingSharedUrl() ?: return
        previewViewModel.setInputUrl(pendingUrl)
        previewViewModel.fetchVideoInfo(pendingUrl)
    }

    private fun autoPasteFromClipboard() {
        try {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val pastedText = clip.getItemAt(0).text?.toString() ?: ""
                val url = LinkResolver.extractUrlFromText(pastedText)

                if (url != null && LinkResolver.isSupported(url)) {
                    previewViewModel.setInputUrl(url)
                }
            }
        } catch (_: Exception) {
            // Ignore clipboard read errors for silent auto-paste.
        }
    }

    private fun pasteFromClipboard() {
        try {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip == null || clip.itemCount == 0) {
                showError("Panoda kopyalanmis link yok")
                return
            }

            val pastedText = clip.getItemAt(0).text?.toString() ?: ""
            val url = LinkResolver.extractUrlFromText(pastedText) ?: pastedText
            previewViewModel.setInputUrl(url)

            if (LinkResolver.isSupported(url)) {
                previewViewModel.fetchVideoInfo(url)
            } else if (url.isNotBlank()) {
                showError("Bu link desteklenmiyor. Desteklenen platformlar: Instagram, TikTok, Twitter/X, YouTube, Facebook, Pinterest")
            }
        } catch (e: Exception) {
            showError("Pano okunamadi: ${e.message}")
        }
    }

    private fun updatePlatformIndicator(url: String) {
        if (url.isBlank()) {
            binding.platformIndicator.isVisible = false
            return
        }

        val result = LinkResolver.resolve(url)
        binding.platformIndicator.isVisible = true
        if (result.isValid) {
            binding.platformIcon.setImageResource(result.platform.iconRes)
            binding.platformName.text = getString(R.string.platform_detected, result.platform.displayName)
            binding.platformName.setTextColor(resources.getColor(R.color.success, null))
        } else {
            binding.platformIcon.setImageResource(R.drawable.ic_link)
            binding.platformName.text = "Platform taninmadi"
            binding.platformName.setTextColor(resources.getColor(R.color.warning, null))
        }
    }

    private fun fetchVideoInfo() {
        val url = binding.urlInput.text?.toString()?.trim() ?: ""

        if (url.isBlank()) {
            showError("Lutfen bir video linki girin")
            return
        }

        if (!LinkResolver.isSupported(url)) {
            showError("Bu platform desteklenmiyor.\n\nDesteklenen: Instagram, TikTok, Twitter/X, YouTube, Facebook, Pinterest")
            return
        }

        binding.videoPreviewCard.isVisible = false
        previewViewModel.fetchVideoInfo(url)
    }

    private fun showLoading(show: Boolean) {
        binding.loadingContainer.isVisible = show
        binding.btnFetch.isEnabled = !show
        binding.btnPaste.isEnabled = !show
    }

    private fun displayVideoInfo(videoInfo: VideoInfo, selectedSelector: String?) {
        binding.videoPreviewCard.isVisible = true

        videoInfo.thumbnailUrl?.let { url ->
            binding.videoThumbnail.load(url) {
                crossfade(true)
                placeholder(R.color.surface_dark)
                error(R.color.surface_elevated_dark)
            }
        } ?: run {
            binding.videoThumbnail.setBackgroundColor(resources.getColor(R.color.surface_dark, null))
        }

        binding.videoTitle.text = videoInfo.title
        binding.videoAuthor.text = videoInfo.author ?: videoInfo.platform.displayName
        binding.videoDuration.text = videoInfo.getFormattedDuration()
        binding.videoDuration.isVisible = videoInfo.duration != null

        if (videoInfo.availableQualities.isNotEmpty()) {
            setupQualityChips(videoInfo.availableQualities, selectedSelector)
            binding.btnDownload.isEnabled = true
        } else {
            binding.qualityChips.removeAllViews()
            binding.fileSizeInfo.text = "Video URL bulunamadi. Bu platform videoyu koruma altinda tutuyor olabilir."
            binding.fileSizeInfo.setTextColor(resources.getColor(R.color.warning, null))
            binding.btnDownload.isEnabled = false
        }
    }

    private fun setupQualityChips(qualities: List<AvailableQuality>, selectedSelector: String?) {
        binding.qualityChips.removeAllViews()

        qualities.forEachIndexed { index, quality ->
            val chip = Chip(requireContext()).apply {
                text = "${quality.quality.label} (${quality.quality.resolution})"
                isCheckable = true
                val shouldCheck = quality.url == selectedSelector || (selectedSelector == null && index == 0)
                isChecked = shouldCheck

                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        previewViewModel.selectQuality(quality)
                        updateFileSizeInfo(quality)
                    }
                }
            }
            binding.qualityChips.addView(chip)

            if (chip.isChecked) {
                updateFileSizeInfo(quality)
            }
        }
    }

    private fun updateFileSizeInfo(quality: AvailableQuality) {
        val sizeText = if (quality.fileSize != null) {
            "Tahmini boyut: ${quality.getFormattedSize()}"
        } else {
            "Indirmeye hazir"
        }
        binding.fileSizeInfo.text = sizeText
        binding.fileSizeInfo.setTextColor(resources.getColor(R.color.success, null))
    }

    private fun startDownload() {
        val state = previewViewModel.state.value
        val videoInfo = state.videoInfo ?: return
        val quality = previewViewModel.selectedQuality() ?: return
        val customFileName = binding.filenameInput.text?.toString()?.trim()?.ifBlank { videoInfo.title } ?: videoInfo.title

        if (quality.url.isBlank()) {
            showError("Video URL bulunamadi. Lutfen farkli bir link deneyin.")
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            downloadManager.enqueue(
                url = videoInfo.url,
                downloadSelector = quality.url,
                downloadExtractorArgs = quality.extractorArgs,
                strictSelection = quality.strictSelection,
                platform = videoInfo.platform,
                title = videoInfo.title,
                thumbnailUrl = videoInfo.thumbnailUrl,
                quality = quality.quality,
                customFileName = customFileName,
                downloadProfile = DownloadProfile.fromVideoQuality(quality.quality)
            )

            val queuedCount = downloadManager.getQueuedCount()
            val activeCount = downloadManager.getActiveCount()
            val message = if (activeCount > 0 || queuedCount > 0) {
                "Kuyruga eklendi (${activeCount + queuedCount} indirme)"
            } else {
                "Indirme basladi: $customFileName"
            }
            showSuccess(message)
            previewViewModel.reset()
        }
    }

    private fun formatFetchErrorMessage(error: Throwable): String {
        return when (error) {
            is UnknownHostException -> "Internet baglantisi yok. Lutfen baglantinizi kontrol edin."
            is SocketTimeoutException -> "Baglanti zaman asimina ugradi. Tekrar deneyin."
            is IOException -> "Ag hatasi olustu: ${error.message}"
            else -> {
                val message = error.message ?: "Bilinmeyen hata"
                when {
                    message.contains("Video not available, status code 0", ignoreCase = true) ->
                        "TikTok bu video icin hem standart web akisinda hem de uygulama extractor tarafinda veri vermedi."
                    message.contains("Unable to extract webpage video data", ignoreCase = true) ->
                        "TikTok web sayfasindan video verisi okunamadi."
                    message.contains("status code 10204", ignoreCase = true) ->
                        "TikTok bu videoyu mevcut IP ile engelliyor. Farkli bir paylasim linki veya farkli ag deneyin."
                    message.contains("fallback", ignoreCase = true) && message.contains("TikTok", ignoreCase = true) ->
                        "TikTok fallback akisi da basarisiz oldu. Video gecici olarak TikTok tarafinda kisitli olabilir."
                    message.contains("video", ignoreCase = true) && message.contains("bulunamadi", ignoreCase = true) ->
                        "Video bulunamadi. Link dogru mu kontrol edin."
                    message.contains("permission", ignoreCase = true) ->
                        "Erisim izni reddedildi. Bu video gizli olabilir."
                    message.contains("404") ->
                        "Sayfa bulunamadi. Video silinmis olabilir."
                    else -> "Hata: $message"
                }
            }
        }
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(resources.getColor(R.color.error, null))
            .setTextColor(resources.getColor(R.color.white, null))
            .show()
    }

    private fun showSuccess(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(resources.getColor(R.color.success, null))
            .setTextColor(resources.getColor(R.color.white, null))
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
