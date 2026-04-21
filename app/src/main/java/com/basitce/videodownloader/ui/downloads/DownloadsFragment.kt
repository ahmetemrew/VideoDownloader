package com.basitce.videodownloader.ui.downloads

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.basitce.videodownloader.R
import com.basitce.videodownloader.data.GallerySaver
import com.basitce.videodownloader.data.model.DownloadItem
import com.basitce.videodownloader.data.model.DownloadStatus
import com.basitce.videodownloader.data.repository.DownloadRepository
import com.basitce.videodownloader.databinding.FragmentDownloadsBinding
import com.basitce.videodownloader.databinding.ItemDownloadBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class DownloadsFragment : Fragment() {

    private var _binding: FragmentDownloadsBinding? = null
    private val binding get() = _binding!!

    private lateinit var downloadAdapter: DownloadAdapter
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var recyclerView: RecyclerView
    private var currentTab = 0
    private var loadJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDownloadsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        downloadRepository = DownloadRepository(requireContext())

        binding.emptyActionButton.setOnClickListener {
            findNavController().navigate(R.id.universalFragment)
        }

        setupTabs()
        setupRecyclerView()
        loadDownloads()
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = tab.position
                loadDownloads()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun setupRecyclerView() {
        downloadAdapter = DownloadAdapter(
            onItemClick = { download ->
                if (download.status == DownloadStatus.COMPLETED) {
                    openDownload(download)
                }
            },
            onMoreClick = { download, view ->
                showPopupMenu(download, view)
            }
        )

        binding.viewPager.isVisible = false

        recyclerView = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = downloadAdapter
            clipToPadding = false
            setPadding(0, 14, 0, 124)
        }

        (binding.viewPager.parent as? ViewGroup)?.let { parent ->
            val index = parent.indexOfChild(binding.viewPager)
            parent.removeView(binding.viewPager)
            val params = binding.viewPager.layoutParams
            recyclerView.layoutParams = params
            parent.addView(recyclerView, index)
        }
    }

    private fun loadDownloads() {
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            val flow = when (currentTab) {
                0 -> downloadRepository.getActiveDownloads()
                1 -> downloadRepository.getCompletedDownloads()
                2 -> downloadRepository.getFailedDownloads()
                else -> downloadRepository.getAllDownloads()
            }

            flow.collectLatest { downloads ->
                downloadAdapter.submitList(downloads)
                binding.emptyState.isVisible = downloads.isEmpty()
                recyclerView.isVisible = downloads.isNotEmpty()
            }
        }
    }

    private fun showPopupMenu(download: DownloadItem, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.menu_download_item, popup.menu)
        popup.menu.findItem(R.id.action_gallery)?.title = if (download.isSavedToGallery()) {
            getString(R.string.action_open_in_gallery)
        } else {
            getString(R.string.action_add_to_gallery)
        }

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_share -> {
                    shareDownload(download)
                    true
                }

                R.id.action_gallery -> {
                    handleGalleryAction(download)
                    true
                }

                R.id.action_delete -> {
                    confirmDelete(download)
                    true
                }

                else -> false
            }
        }
        popup.show()
    }

    private fun handleGalleryAction(download: DownloadItem) {
        if (download.isSavedToGallery()) {
            openInGallery(download)
            return
        }

        val filePath = download.filePath
        if (filePath.isNullOrBlank()) {
            Toast.makeText(context, R.string.error_download_failed, Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val galleryUri = GallerySaver.scanIntoGallery(requireContext(), filePath)
            if (galleryUri == null) {
                Toast.makeText(context, R.string.error_download_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }

            downloadRepository.markGallerySaved(download.id, galleryUri.toString())
            Toast.makeText(context, R.string.action_add_to_gallery, Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareDownload(download: DownloadItem) {
        val contentUri = resolveDownloadUri(download)
        if (contentUri == null) {
            Toast.makeText(context, R.string.error_download_failed, Toast.LENGTH_SHORT).show()
            return
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = resolveMimeType(download)
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, download.customFileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share)))
    }

    private fun confirmDelete(download: DownloadItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_delete_title)
            .setMessage(
                getString(R.string.dialog_delete_download_message, download.customFileName)
            )
            .setPositiveButton(R.string.action_delete) { _, _ ->
                deleteDownload(download)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun deleteDownload(download: DownloadItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            downloadRepository.delete(download)
            Toast.makeText(context, R.string.toast_deleted, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openDownload(download: DownloadItem) {
        val contentUri = resolveDownloadUri(download)
        if (contentUri == null) {
            Toast.makeText(context, R.string.error_download_failed, Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, resolveMimeType(download))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.error_download_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openInGallery(download: DownloadItem) {
        val uri = download.galleryUri?.let(Uri::parse) ?: resolveDownloadUri(download)
        if (uri == null) {
            Toast.makeText(context, R.string.error_download_failed, Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, resolveMimeType(download))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.error_download_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun resolveDownloadUri(download: DownloadItem): Uri? {
        val path = download.filePath ?: return null
        if (path.startsWith("content://")) {
            return Uri.parse(path)
        }

        val file = File(path)
        if (!file.exists()) {
            return null
        }

        return FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )
    }

    private fun resolveMimeType(download: DownloadItem): String {
        val path = download.filePath.orEmpty().lowercase()
        return when {
            path.endsWith(".mp3") || path.endsWith(".m4a") || download.quality.resolution.equals("MP3", ignoreCase = true) ->
                "audio/*"

            else -> "video/*"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadJob?.cancel()
        _binding = null
    }
}

class DownloadAdapter(
    private val onItemClick: (DownloadItem) -> Unit,
    private val onMoreClick: (DownloadItem, View) -> Unit
) : RecyclerView.Adapter<DownloadAdapter.ViewHolder>() {

    private var items: List<DownloadItem> = emptyList()

    fun submitList(newItems: List<DownloadItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDownloadBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(
        private val binding: ItemDownloadBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DownloadItem) {
            binding.apply {
                item.thumbnailUrl?.let { url ->
                    thumbnail.load(url) {
                        crossfade(true)
                    }
                }

                platformBadge.setImageResource(item.platform.iconRes)
                title.text = item.customFileName
                quality.text = item.getDisplayQualityLabel()
                size.text = item.getFormattedSize().ifBlank { item.getStatusText() }

                when (item.status) {
                    DownloadStatus.COMPLETED -> {
                        statusCompleted.isVisible = true
                        progressText.isVisible = false
                        statusFailed.isVisible = false
                        progressBar.isVisible = false
                        progressBar.isIndeterminate = false
                    }

                    DownloadStatus.DOWNLOADING -> {
                        statusCompleted.isVisible = false
                        progressText.isVisible = true
                        progressText.text = "${item.progress}%"
                        statusFailed.isVisible = false
                        progressBar.isVisible = true
                        progressBar.isIndeterminate = false
                        progressBar.progress = item.progress
                    }

                    DownloadStatus.PENDING -> {
                        statusCompleted.isVisible = false
                        progressText.isVisible = true
                        progressText.text = root.context.getString(R.string.status_pending)
                        statusFailed.isVisible = false
                        progressBar.isVisible = true
                        progressBar.isIndeterminate = true
                    }

                    DownloadStatus.FAILED -> {
                        statusCompleted.isVisible = false
                        progressText.isVisible = false
                        statusFailed.isVisible = true
                        progressBar.isVisible = false
                        progressBar.isIndeterminate = false
                    }

                    DownloadStatus.PAUSED -> {
                        statusCompleted.isVisible = false
                        progressText.isVisible = true
                        progressText.text = root.context.getString(R.string.status_paused)
                        statusFailed.isVisible = false
                        progressBar.isVisible = true
                        progressBar.isIndeterminate = false
                        progressBar.progress = item.progress
                    }
                }

                downloadCard.setOnClickListener { onItemClick(item) }
                btnMore.setOnClickListener { onMoreClick(item, it) }
            }
        }
    }
}
