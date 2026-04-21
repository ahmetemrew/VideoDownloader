package com.basitce.videodownloader

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.basitce.videodownloader.data.AppPreferences
import com.basitce.videodownloader.data.LinkResolver
import com.basitce.videodownloader.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appPreferences: AppPreferences

    private var pendingSharedUrls: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        appPreferences = AppPreferences(this)
        applyThemePreference()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNavigation.setupWithNavController(navController)
    }

    private fun handleIntent(intent: Intent?) {
        val navigationTarget = intent?.getStringExtra("navigate_to")
        if (navigationTarget == "downloads") {
            binding.bottomNavigation.selectedItemId = R.id.downloadsFragment
            return
        }

        val sharedText = extractSharedText(intent)
        if (sharedText.isNullOrBlank()) {
            return
        }

        val urls = LinkResolver.extractUrlsFromText(sharedText)
        if (urls.isEmpty()) {
            Toast.makeText(this, getString(R.string.unsupported_url), Toast.LENGTH_SHORT).show()
            return
        }

        pendingSharedUrls = urls
        Toast.makeText(this, getString(R.string.toast_link_received), Toast.LENGTH_SHORT).show()

        binding.bottomNavigation.selectedItemId = when {
            urls.size == 1 && LinkResolver.isSupported(urls.first()) -> R.id.universalFragment
            else -> R.id.platformFragment
        }
    }

    fun consumePendingSharedUrl(): String? {
        if (pendingSharedUrls.size != 1) {
            return null
        }

        val url = pendingSharedUrls.firstOrNull()
        pendingSharedUrls = emptyList()
        return url
    }

    fun consumePendingSharedUrls(): List<String> {
        val urls = pendingSharedUrls
        pendingSharedUrls = emptyList()
        return urls
    }

    private fun extractSharedText(intent: Intent?): String? {
        if (intent == null) {
            return null
        }

        val textParts = mutableListOf<String>()

        when (intent.action) {
            Intent.ACTION_SEND -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let(textParts::add)
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getStringArrayListExtra(Intent.EXTRA_TEXT)?.let(textParts::addAll)
            }
        }

        intent.clipData?.let { clipData ->
            for (index in 0 until clipData.itemCount) {
                clipData.getItemAt(index).coerceToText(this)?.toString()?.let(textParts::add)
            }
        }

        return textParts
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n")
            .ifBlank { null }
    }

    private fun applyThemePreference() {
        val mode = when (appPreferences.theme) {
            AppPreferences.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            AppPreferences.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
