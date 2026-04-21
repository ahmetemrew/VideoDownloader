package com.basitce.videodownloader.data.scraper

import android.content.Context
import kotlin.random.Random

object TikTokSessionConfig {

    private const val PREFS_NAME = "tiktok_session_config"
    private const val KEY_DEVICE_ID = "device_id"

    fun extractorArgs(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val deviceId = prefs.getString(KEY_DEVICE_ID, null) ?: generate19DigitId().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }

        return "tiktok:device_id=$deviceId"
    }

    private fun generate19DigitId(): String {
        val firstDigit = Random.nextInt(7, 8)
        val remaining = buildString(18) {
            repeat(18) {
                append(Random.nextInt(0, 10))
            }
        }
        return "$firstDigit$remaining"
    }
}
