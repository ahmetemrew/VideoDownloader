package com.basitce.videodownloader

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.basitce.videodownloader.databinding.ActivityMainBinding
import com.basitce.videodownloader.data.LinkResolver

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var pendingSharedUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)
    }

    /**
     * Diğer uygulamalardan paylaşılan linkleri işler
     */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (sharedText != null) {
                // URL'yi çıkar
                val url = LinkResolver.extractUrlFromText(sharedText)
                if (url != null && LinkResolver.isSupported(url)) {
                    pendingSharedUrl = url
                    Toast.makeText(this, getString(R.string.toast_link_received), Toast.LENGTH_SHORT).show()
                    
                    // Universal fragment'a yönlendir
                    binding.bottomNavigation.selectedItemId = R.id.universalFragment
                } else {
                    Toast.makeText(this, getString(R.string.unsupported_url), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Bekleyen paylaşılan URL'yi alır ve temizler
     */
    fun consumePendingSharedUrl(): String? {
        val url = pendingSharedUrl
        pendingSharedUrl = null
        return url
    }
}