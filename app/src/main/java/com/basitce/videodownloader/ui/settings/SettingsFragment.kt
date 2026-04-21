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
import com.basitce.videodownloader.data.model.DownloadProfile
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
        binding.versionText.text = getString(R.string.setting_version, BuildConfig.VERSION_NAME)

        binding.settingQuality.setOnClickListener { showQualityDialog() }
        binding.settingBatchProfile.setOnClickListener { showBatchProfileDialog() }
        binding.settingTheme.setOnClickListener { showThemeDialog() }

        binding.switchGalleryAutoSave.setOnCheckedChangeListener { _, isChecked ->
            appPreferences.galleryAutoSaveEnabled = isChecked
        }

        binding.switchAutoPaste.setOnCheckedChangeListener { _, isChecked ->
            appPreferences.autoPasteEnabled = isChecked
        }

        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            appPreferences.notificationsEnabled = isChecked
        }
    }

    private fun loadSettings() {
        binding.switchGalleryAutoSave.isChecked = appPreferences.galleryAutoSaveEnabled
        binding.switchAutoPaste.isChecked = appPreferences.autoPasteEnabled
        binding.switchNotifications.isChecked = appPreferences.notificationsEnabled
        updateQualityDisplay()
        updateBatchProfileDisplay()
        updateThemeDisplay()
        updateStats()
    }

    private fun updateStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            val completedCount = downloadRepository.getCompletedCount()
            binding.versionText.text = buildString {
                append(getString(R.string.setting_version, BuildConfig.VERSION_NAME))
                append("\n")
                append(getString(R.string.settings_total_downloads, completedCount))
            }
        }
    }

    private fun showQualityDialog() {
        val qualities = VideoQuality.getVideoQualities()
        val labels = qualities.map { "${it.label} (${it.resolution})" }.toTypedArray()
        val currentIndex = qualities.indexOf(appPreferences.defaultQuality).coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.setting_quality)
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                appPreferences.defaultQuality = qualities[which]
                updateQualityDisplay()
                dialog.dismiss()
                Toast.makeText(context, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showBatchProfileDialog() {
        val profiles = DownloadProfile.entries.toList()
        val labels = profiles.map { "${it.label} - ${it.summary}" }.toTypedArray()
        val currentIndex = profiles.indexOf(appPreferences.defaultDownloadProfile).coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.setting_batch_profile)
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                appPreferences.defaultDownloadProfile = profiles[which]
                updateBatchProfileDisplay()
                dialog.dismiss()
                Toast.makeText(context, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showThemeDialog() {
        val themes = listOf(
            AppPreferences.THEME_LIGHT to getString(R.string.theme_light),
            AppPreferences.THEME_DARK to getString(R.string.theme_dark),
            AppPreferences.THEME_SYSTEM to getString(R.string.theme_system)
        )
        val labels = themes.map { it.second }.toTypedArray()
        val currentIndex = themes.indexOfFirst { it.first == appPreferences.theme }.coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.setting_theme)
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                appPreferences.theme = themes[which].first
                updateThemeDisplay()
                dialog.dismiss()
                requireActivity().recreate()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun updateQualityDisplay() {
        val quality = appPreferences.defaultQuality
        binding.qualityValue.text = "${quality.label} (${quality.resolution})"
    }

    private fun updateBatchProfileDisplay() {
        val profile = appPreferences.defaultDownloadProfile
        binding.batchProfileValue.text = "${profile.label} - ${profile.summary}"
    }

    private fun updateThemeDisplay() {
        binding.themeValue.text = when (appPreferences.theme) {
            AppPreferences.THEME_LIGHT -> getString(R.string.theme_light)
            AppPreferences.THEME_DARK -> getString(R.string.theme_dark)
            else -> getString(R.string.theme_system)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
