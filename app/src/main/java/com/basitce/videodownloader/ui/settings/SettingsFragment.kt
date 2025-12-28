package com.basitce.videodownloader.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.basitce.videodownloader.BuildConfig
import com.basitce.videodownloader.R
import com.basitce.videodownloader.data.AppPreferences
import com.basitce.videodownloader.data.model.VideoQuality
import com.basitce.videodownloader.data.repository.DownloadRepository
import com.basitce.videodownloader.databinding.FragmentSettingsBinding
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var appPreferences: AppPreferences
    private lateinit var downloadRepository: DownloadRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        appPreferences = AppPreferences(requireContext())
        downloadRepository = DownloadRepository(requireContext())
        
        setupUI()
        loadSettings()
    }

    private fun setupUI() {
        // Versiyon bilgisi
        binding.versionText.text = getString(R.string.setting_version, BuildConfig.VERSION_NAME)

        // Kalite seçimi
        binding.settingQuality.setOnClickListener {
            showQualityDialog()
        }

        // Auto-paste switch
        binding.switchAutoPaste.setOnCheckedChangeListener { _, isChecked ->
            appPreferences.autoPasteEnabled = isChecked
        }

        // Notifications switch
        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            appPreferences.notificationsEnabled = isChecked
        }
    }

    private fun loadSettings() {
        // Mevcut ayarları yükle
        binding.switchAutoPaste.isChecked = appPreferences.autoPasteEnabled
        binding.switchNotifications.isChecked = appPreferences.notificationsEnabled
        updateQualityDisplay()
        updateStats()
    }

    private fun updateStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            val completedCount = downloadRepository.getCompletedCount()
            binding.versionText.text = buildString {
                append(getString(R.string.setting_version, BuildConfig.VERSION_NAME))
                append("\n")
                append("Toplam indirme: $completedCount")
            }
        }
    }

    private fun showQualityDialog() {
        val qualities = VideoQuality.getVideoQualities()
        val qualityNames = qualities.map { "${it.label} (${it.resolution})" }.toTypedArray()
        val currentIndex = qualities.indexOf(appPreferences.defaultQuality).coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.setting_quality)
            .setSingleChoiceItems(qualityNames, currentIndex) { dialog, which ->
                appPreferences.defaultQuality = qualities[which]
                updateQualityDisplay()
                dialog.dismiss()
                Toast.makeText(context, "Varsayılan kalite güncellendi", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun updateQualityDisplay() {
        val quality = appPreferences.defaultQuality
        binding.qualityValue.text = "${quality.label} (${quality.resolution})"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
