package com.basitce.videodownloader.ui.universal

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import com.basitce.videodownloader.MainActivity
import com.basitce.videodownloader.R
import com.basitce.videodownloader.data.AppPreferences
import com.basitce.videodownloader.data.LinkResolver
import com.basitce.videodownloader.data.model.AvailableQuality
import com.basitce.videodownloader.data.model.DownloadItem
import com.basitce.videodownloader.data.model.DownloadStatus
import com.basitce.videodownloader.data.model.VideoInfo
import com.basitce.videodownloader.data.repository.DownloadRepository
import com.basitce.videodownloader.data.scraper.VideoDownloader
import com.basitce.videodownloader.data.scraper.VideoScraper
import com.basitce.videodownloader.databinding.FragmentUniversalBinding
import com.basitce.videodownloader.service.DownloadManager
import com.basitce.videodownloader.service.NotificationHelper
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.net.UnknownHostException

class UniversalFragment : Fragment() {

    private var _binding: FragmentUniversalBinding? = null
    private val binding get() = _binding!!

    private var currentVideoInfo: VideoInfo? = null
    private var selectedQuality: AvailableQuality? = null
    private lateinit var videoScraper: VideoScraper
    private lateinit var downloadManager: DownloadManager
    private lateinit var appPreferences: AppPreferences

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
        
        // Bağımlılıkları başlat
        videoScraper = VideoScraper(requireContext())
        downloadManager = DownloadManager.getInstance(requireContext())
        appPreferences = AppPreferences(requireContext())
        
        // Bildirim kanallarını oluştur
        NotificationHelper.createNotificationChannels(requireContext())
        
        setupUI()
        checkPendingUrl()
        
        // Otomatik yapıştır ayarı açıksa kontrol et
        if (appPreferences.autoPasteEnabled && appPreferences.isFirstLaunch) {
            appPreferences.isFirstLaunch = false
            autoPasteFromClipboard()
        }
    }

    override fun onResume() {
        super.onResume()
        checkPendingUrl()
        
        // Her resume'da otomatik yapıştır
        if (appPreferences.autoPasteEnabled && binding.urlInput.text.isNullOrBlank()) {
            autoPasteFromClipboard()
        }
    }

    private fun setupUI() {
        // URL input değişikliklerini dinle
        binding.urlInput.doAfterTextChanged { text ->
            val url = text?.toString() ?: ""
            updatePlatformIndicator(url)
        }

        // Klavyede Done'a basıldığında
        binding.urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                fetchVideoInfo()
                true
            } else false
        }

        // Yapıştır butonu
        binding.btnPaste.setOnClickListener {
            pasteFromClipboard()
        }

        // Getir butonu
        binding.btnFetch.setOnClickListener {
            fetchVideoInfo()
        }

        // İndir butonu
        binding.btnDownload.setOnClickListener {
            startDownload()
        }
    }

    private fun checkPendingUrl() {
        val activity = activity as? MainActivity ?: return
        val pendingUrl = activity.consumePendingSharedUrl()
        if (pendingUrl != null) {
            binding.urlInput.setText(pendingUrl)
            fetchVideoInfo()
        }
    }

    /**
     * Panodan otomatik yapıştır (sessiz)
     */
    private fun autoPasteFromClipboard() {
        try {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val pastedText = clip.getItemAt(0).text?.toString() ?: ""
                val url = LinkResolver.extractUrlFromText(pastedText)
                
                if (url != null && LinkResolver.isSupported(url)) {
                    binding.urlInput.setText(url)
                    updatePlatformIndicator(url)
                }
            }
        } catch (e: Exception) {
            // Sessiz hata - izin yoksa veya başka sorun varsa görmezden gel
        }
    }

    private fun pasteFromClipboard() {
        try {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val pastedText = clip.getItemAt(0).text?.toString() ?: ""
                val url = LinkResolver.extractUrlFromText(pastedText) ?: pastedText
                binding.urlInput.setText(url)
                updatePlatformIndicator(url)
                
                // Otomatik olarak video bilgisini getir
                if (LinkResolver.isSupported(url)) {
                    fetchVideoInfo()
                } else if (url.isNotBlank()) {
                    showError("Bu link desteklenmiyor. Desteklenen platformlar: Instagram, TikTok, Twitter/X, YouTube, Facebook, Pinterest")
                }
            } else {
                showError("Panoda kopyalanmış link yok")
            }
        } catch (e: Exception) {
            showError("Pano okunamadı: ${e.message}")
        }
    }

    private fun updatePlatformIndicator(url: String) {
        if (url.isBlank()) {
            binding.platformIndicator.isVisible = false
            return
        }

        val result = LinkResolver.resolve(url)
        if (result.isValid) {
            binding.platformIndicator.isVisible = true
            binding.platformIcon.setImageResource(result.platform.iconRes)
            binding.platformName.text = getString(R.string.platform_detected, result.platform.displayName)
            binding.platformName.setTextColor(resources.getColor(R.color.success, null))
        } else {
            binding.platformIndicator.isVisible = true
            binding.platformIcon.setImageResource(R.drawable.ic_link)
            binding.platformName.text = "Platform tanınmadı"
            binding.platformName.setTextColor(resources.getColor(R.color.warning, null))
        }
    }

    private fun fetchVideoInfo() {
        val url = binding.urlInput.text?.toString()?.trim() ?: ""
        
        if (url.isBlank()) {
            showError("Lütfen bir video linki girin")
            return
        }

        if (!LinkResolver.isSupported(url)) {
            showError("Bu platform desteklenmiyor.\n\nDesteklenen: Instagram, TikTok, Twitter/X, YouTube, Facebook, Pinterest")
            return
        }

        // Loading durumunu göster
        showLoading(true)
        binding.videoPreviewCard.isVisible = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Gerçek web scraping kullan (Jsoup + WebView fallback)
                val result = videoScraper.scrapeVideoInfo(url)
                
                showLoading(false)
                
                result.onSuccess { videoInfo ->
                    currentVideoInfo = videoInfo
                    displayVideoInfo(videoInfo)
                }.onFailure { error ->
                    handleFetchError(error)
                }
            } catch (e: Exception) {
                showLoading(false)
                handleFetchError(e)
            }
        }
    }

    /**
     * Hata mesajlarını kullanıcı dostu hale getir
     */
    private fun handleFetchError(error: Throwable) {
        val message = when (error) {
            is UnknownHostException -> "İnternet bağlantısı yok. Lütfen bağlantınızı kontrol edin."
            is java.net.SocketTimeoutException -> "Bağlantı zaman aşımına uğradı. Tekrar deneyin."
            is java.io.IOException -> "Ağ hatası oluştu: ${error.message}"
            else -> {
                val msg = error.message ?: "Bilinmeyen hata"
                when {
                    msg.contains("video", ignoreCase = true) && msg.contains("bulunamadı", ignoreCase = true) ->
                        "Video bulunamadı. Link doğru mu kontrol edin."
                    msg.contains("permission", ignoreCase = true) ->
                        "Erişim izni reddedildi. Bu video gizli olabilir."
                    msg.contains("404") ->
                        "Sayfa bulunamadı. Video silinmiş olabilir."
                    else -> "Hata: $msg"
                }
            }
        }
        showError(message)
    }

    private fun showLoading(show: Boolean) {
        binding.loadingContainer.isVisible = show
        binding.btnFetch.isEnabled = !show
        binding.btnPaste.isEnabled = !show
    }

    private fun displayVideoInfo(videoInfo: VideoInfo) {
        binding.videoPreviewCard.isVisible = true

        // Thumbnail yükle
        videoInfo.thumbnailUrl?.let { url ->
            binding.videoThumbnail.load(url) {
                crossfade(true)
                placeholder(R.color.surface_dark)
                error(R.color.surface_elevated_dark)
            }
        } ?: run {
            binding.videoThumbnail.setBackgroundColor(resources.getColor(R.color.surface_dark, null))
        }

        // Video bilgilerini göster
        binding.videoTitle.text = videoInfo.title
        binding.videoAuthor.text = videoInfo.author ?: videoInfo.platform.displayName
        binding.videoDuration.text = videoInfo.getFormattedDuration()
        binding.videoDuration.isVisible = videoInfo.duration != null

        // Dosya adını ayarla (varsayılan olarak video başlığı)
        val safeFileName = videoInfo.title
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(50)
        binding.filenameInput.setText(safeFileName)

        // Kalite seçeneklerini oluştur
        if (videoInfo.availableQualities.isNotEmpty()) {
            setupQualityChips(videoInfo.availableQualities)
            binding.btnDownload.isEnabled = true
            binding.fileSizeInfo.text = "İndirmeye hazır"
            binding.fileSizeInfo.setTextColor(resources.getColor(R.color.success, null))
        } else {
            binding.qualityChips.removeAllViews()
            binding.fileSizeInfo.text = "⚠️ Video URL bulunamadı. Bu platform videoyu koruma altında tutuyor olabilir."
            binding.fileSizeInfo.setTextColor(resources.getColor(R.color.warning, null))
            binding.btnDownload.isEnabled = false
        }
    }

    private fun setupQualityChips(qualities: List<AvailableQuality>) {
        binding.qualityChips.removeAllViews()
        
        qualities.forEachIndexed { index, quality ->
            val chip = Chip(requireContext()).apply {
                text = "${quality.quality.label} (${quality.quality.resolution})"
                isCheckable = true
                isChecked = index == 0
                
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedQuality = quality
                        updateFileSizeInfo(quality)
                    }
                }
            }
            binding.qualityChips.addView(chip)
            
            if (index == 0) {
                selectedQuality = quality
                updateFileSizeInfo(quality)
            }
        }
    }

    private fun updateFileSizeInfo(quality: AvailableQuality) {
        val sizeText = if (quality.fileSize != null) {
            "Tahmini boyut: ${quality.getFormattedSize()}"
        } else {
            "İndirmeye hazır ✓"
        }
        binding.fileSizeInfo.text = sizeText
        binding.fileSizeInfo.setTextColor(resources.getColor(R.color.success, null))
    }

    private fun startDownload() {
        val videoInfo = currentVideoInfo ?: return
        val quality = selectedQuality ?: return
        val customFileName = binding.filenameInput.text?.toString()?.trim() ?: videoInfo.title

        if (quality.url.isBlank()) {
            showError("Video URL bulunamadı. Lütfen farklı bir link deneyin.")
            return
        }

        // Kuyruğa ekle
        viewLifecycleOwner.lifecycleScope.launch {
            downloadManager.enqueue(
                url = videoInfo.url,
                videoUrl = quality.url,
                platform = videoInfo.platform,
                title = videoInfo.title,
                thumbnailUrl = videoInfo.thumbnailUrl,
                quality = quality.quality,
                customFileName = customFileName
            )
            
            val queuedCount = downloadManager.getQueuedCount()
            val activeCount = downloadManager.getActiveCount()
            
            val message = if (activeCount > 0 || queuedCount > 0) {
                "✅ Kuyruğa eklendi (${activeCount + queuedCount} indirme)"
            } else {
                "✅ İndirme başladı: $customFileName"
            }
            showSuccess(message)
            resetUI()
        }
    }

    private fun resetUI() {
        binding.urlInput.text?.clear()
        binding.videoPreviewCard.isVisible = false
        binding.platformIndicator.isVisible = false
        binding.btnDownload.text = getString(R.string.btn_download)
        binding.btnDownload.isEnabled = true
        currentVideoInfo = null
        selectedQuality = null
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
