package com.basitce.videodownloader.ui.downloads

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.basitce.videodownloader.R
import com.basitce.videodownloader.data.model.DownloadItem
import com.basitce.videodownloader.data.model.DownloadStatus
import com.basitce.videodownloader.data.repository.DownloadRepository
import com.basitce.videodownloader.databinding.FragmentDownloadsBinding
import com.basitce.videodownloader.databinding.ItemDownloadBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DownloadsFragment : Fragment() {

    private var _binding: FragmentDownloadsBinding? = null
    private val binding get() = _binding!!

    private lateinit var downloadAdapter: DownloadAdapter
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var recyclerView: RecyclerView
    private var currentTab = 0 // 0: Completed, 1: Active, 2: Failed

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
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun setupRecyclerView() {
        downloadAdapter = DownloadAdapter(
            onItemClick = { download ->
                if (download.status == DownloadStatus.COMPLETED) {
                    Toast.makeText(context, "Video: ${download.customFileName}", Toast.LENGTH_SHORT).show()
                    // TODO: Video oynatıcı aç
                }
            },
            onMoreClick = { download, view ->
                showPopupMenu(download, view)
            }
        )

        // ViewPager yerine RecyclerView kullan
        binding.viewPager.isVisible = false
        
        recyclerView = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = downloadAdapter
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
        viewLifecycleOwner.lifecycleScope.launch {
            val flow = when (currentTab) {
                0 -> downloadRepository.getCompletedDownloads()
                1 -> downloadRepository.getActiveDownloads()
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
        
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_share -> {
                    shareDownload(download)
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

    private fun shareDownload(download: DownloadItem) {
        // TODO: Video paylaş
        Toast.makeText(context, "Paylaşılıyor: ${download.customFileName}", Toast.LENGTH_SHORT).show()
    }

    private fun confirmDelete(download: DownloadItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_delete_title)
            .setMessage("\"${download.customFileName}\" silinecek.")
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/**
 * İndirme listesi için RecyclerView adapter
 */
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
                // Thumbnail
                item.thumbnailUrl?.let { url ->
                    thumbnail.load(url) {
                        crossfade(true)
                    }
                }

                // Platform badge
                platformBadge.setImageResource(item.platform.iconRes)

                // Title & info
                title.text = item.customFileName
                quality.text = item.quality.resolution
                size.text = item.getFormattedSize()

                // Status göstergeleri
                when (item.status) {
                    DownloadStatus.COMPLETED -> {
                        statusCompleted.isVisible = true
                        progressText.isVisible = false
                        statusFailed.isVisible = false
                        progressBar.isVisible = false
                    }
                    DownloadStatus.DOWNLOADING, DownloadStatus.PENDING -> {
                        statusCompleted.isVisible = false
                        progressText.isVisible = true
                        progressText.text = "${item.progress}%"
                        statusFailed.isVisible = false
                        progressBar.isVisible = true
                        progressBar.progress = item.progress
                    }
                    DownloadStatus.FAILED -> {
                        statusCompleted.isVisible = false
                        progressText.isVisible = false
                        statusFailed.isVisible = true
                        progressBar.isVisible = false
                    }
                    DownloadStatus.PAUSED -> {
                        statusCompleted.isVisible = false
                        progressText.isVisible = true
                        progressText.text = "⏸"
                        statusFailed.isVisible = false
                        progressBar.isVisible = true
                        progressBar.progress = item.progress
                    }
                }

                // Click listeners
                downloadCard.setOnClickListener { onItemClick(item) }
                btnMore.setOnClickListener { onMoreClick(item, it) }
            }
        }
    }
}
