package com.basitce.videodownloader.data.scraper

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object YtDlpEngine {

    private const val PREFS_NAME = "yt_dlp_engine"
    private const val KEY_LAST_UPDATE_AT = "last_update_at"
    private const val UPDATE_INTERVAL_MS = 24L * 60L * 60L * 1000L

    private val initMutex = Mutex()

    @Volatile
    private var initialized = false

    suspend fun ensureInitialized(context: Context) = withContext(Dispatchers.IO) {
        if (initialized) {
            return@withContext
        }

        initMutex.withLock {
            if (initialized) {
                return@withLock
            }

            val appContext = context.applicationContext
            YoutubeDL.getInstance().init(appContext)
            FFmpeg.getInstance().init(appContext)
            tryUpdateRuntime(appContext)
            initialized = true
        }
    }

    private fun tryUpdateRuntime(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastUpdateAt = prefs.getLong(KEY_LAST_UPDATE_AT, 0L)

        if (now - lastUpdateAt < UPDATE_INTERVAL_MS) {
            return
        }

        try {
            YoutubeDL.getInstance().updateYoutubeDL(context)
            prefs.edit().putLong(KEY_LAST_UPDATE_AT, now).apply()
        } catch (_: YoutubeDLException) {
            // GÃ¼ncelleme baÅŸarÄ±sÄ±z olsa bile bundled runtime ile devam et
        }
    }
}
