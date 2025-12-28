package com.basitce.videodownloader.data

import android.content.Context
import android.content.SharedPreferences
import com.basitce.videodownloader.data.model.VideoQuality

/**
 * Uygulama ayarlarını yöneten SharedPreferences wrapper
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "video_downloader_prefs"
        
        // Ayar anahtarları
        private const val KEY_DEFAULT_QUALITY = "default_quality"
        private const val KEY_AUTO_PASTE = "auto_paste"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_THEME = "theme"
        private const val KEY_DOWNLOAD_LOCATION = "download_location"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_TOTAL_DOWNLOADS = "total_downloads"
        
        // Tema değerleri
        const val THEME_LIGHT = 0
        const val THEME_DARK = 1
        const val THEME_SYSTEM = 2
    }

    // === Varsayılan Kalite ===
    var defaultQuality: VideoQuality
        get() {
            val ordinal = prefs.getInt(KEY_DEFAULT_QUALITY, VideoQuality.QUALITY_720P.ordinal)
            return VideoQuality.entries.getOrElse(ordinal) { VideoQuality.QUALITY_720P }
        }
        set(value) {
            prefs.edit().putInt(KEY_DEFAULT_QUALITY, value.ordinal).apply()
        }

    // === Otomatik Yapıştır ===
    var autoPasteEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_PASTE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_PASTE, value).apply()
        }

    // === Bildirimler ===
    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, value).apply()
        }

    // === Tema ===
    var theme: Int
        get() = prefs.getInt(KEY_THEME, THEME_DARK) // Varsayılan koyu tema
        set(value) {
            prefs.edit().putInt(KEY_THEME, value).apply()
        }

    // === İndirme Konumu ===
    var downloadLocation: String
        get() = prefs.getString(KEY_DOWNLOAD_LOCATION, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_DOWNLOAD_LOCATION, value).apply()
        }

    // === İlk Açılış ===
    var isFirstLaunch: Boolean
        get() = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        set(value) {
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH, value).apply()
        }

    // === Toplam İndirme Sayısı ===
    var totalDownloads: Int
        get() = prefs.getInt(KEY_TOTAL_DOWNLOADS, 0)
        set(value) {
            prefs.edit().putInt(KEY_TOTAL_DOWNLOADS, value).apply()
        }

    /**
     * İndirme sayısını artır
     */
    fun incrementDownloadCount() {
        totalDownloads++
    }

    /**
     * Tüm ayarları varsayılana sıfırla
     */
    fun resetToDefaults() {
        prefs.edit().clear().apply()
    }
}
