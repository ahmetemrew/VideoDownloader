package com.basitce.videodownloader.ui.platform

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.basitce.videodownloader.MainActivity
import com.basitce.videodownloader.R
import com.basitce.videodownloader.data.model.DownloadItem
import com.basitce.videodownloader.data.model.DownloadProfile
import com.basitce.videodownloader.data.model.DownloadStatus
import com.basitce.videodownloader.databinding.FragmentPlatformBinding
import com.basitce.videodownloader.databinding.ItemBulkDownloadBinding
import com.basitce.videodownloader.databinding.ItemPendingBatchLinkBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class PlatformFragment : Fragment() {

    private var _binding: FragmentPlatformBinding? = null
    private val binding get() = _binding!!

    private val batchViewModel: PlatformBatchViewModel by activityViewModels()

    private lateinit var pendingLinksAdapter: PendingLinksAdapter
    private lateinit var batchAdapter: BatchDownloadAdapter
    private var isRenderingState = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlatformBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViews()
        setupProfileDropdown()
        setupInput()
        observeViewModel()
        consumeSharedUrls()
    }

    override fun onResume() {
        super.onResume()
        consumeSharedUrls()
    }

    private fun setupRecyclerViews() {
        pendingLinksAdapter = PendingLinksAdapter(
            onRemove = { link -> batchViewModel.removePendingLink(link.url) }
        )
        batchAdapter = BatchDownloadAdapter()

        binding.pendingLinksList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = pendingLinksAdapter
            itemAnimator = null
            isNestedScrollingEnabled = false
        }

        binding.batchStatusList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = batchAdapter
            itemAnimator = null
        }
    }

    private fun setupProfileDropdown() {
        val profiles = DownloadProfile.entries.toList()
        val profileLabels = profiles.map(::profileLabel)
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            profileLabels
        )

        binding.profileDropdown.setAdapter(adapter)
        binding.profileDropdown.setOnItemClickListener { _, _, position, _ ->
            batchViewModel.onProfileSelected(profiles[position])
        }
    }

    private fun setupInput() {
        binding.bulkLinkInput.doAfterTextChanged { editable ->
            if (isRenderingState) {
                return@doAfterTextChanged
            }

            val text = editable?.toString().orEmpty()
            batchViewModel.onInputTextChanged(text)
            if (text.endsWith(" ") || text.endsWith("\n")) {
                batchViewModel.addLinksFromText(text, showEmptyFeedback = false)
            }
        }

        binding.bulkLinkInput.setOnEditorActionListener { _, _, _ ->
            batchViewModel.addLinksFromCurrentInput()
            true
        }

        binding.bulkLinkInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                batchViewModel.addLinksFromCurrentInput()
                true
            } else {
                false
            }
        }

        binding.btnPasteLinks.setOnClickListener { pasteFromClipboard() }
        binding.btnAddLinks.setOnClickListener { batchViewModel.addLinksFromCurrentInput() }
        binding.btnStartBatch.setOnClickListener { batchViewModel.startBatch() }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    batchViewModel.uiState.collect { state ->
                        renderState(state)
                    }
                }

                launch {
                    batchViewModel.events.collect { message ->
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun renderState(state: PlatformBatchUiState) {
        isRenderingState = true
        try {
            val currentInput = binding.bulkLinkInput.text?.toString().orEmpty()
            if (currentInput != state.inputText) {
                binding.bulkLinkInput.setText(state.inputText)
                binding.bulkLinkInput.setSelection(binding.bulkLinkInput.text?.length ?: 0)
            }

            val dropdownText = binding.profileDropdown.text?.toString().orEmpty()
            val expectedProfileText = profileLabel(state.selectedProfile)
            if (dropdownText != expectedProfileText) {
                binding.profileDropdown.setText(expectedProfileText, false)
            }

            pendingLinksAdapter.submitList(state.pendingLinks)
            batchAdapter.submitList(state.batchDownloads)

            binding.linkCountText.text = state.linkCountText
            binding.batchStateSummary.text = state.batchSummaryText
            binding.btnStartBatch.isEnabled = state.pendingLinks.isNotEmpty() && !state.isStartingBatch
            binding.btnStartBatch.text = if (state.isStartingBatch) {
                getString(R.string.bulk_starting_download)
            } else {
                getString(R.string.bulk_start_download)
            }

            val hasPendingLinks = state.pendingLinks.isNotEmpty()
            binding.pendingLinksList.isVisible = hasPendingLinks
            binding.emptyPendingText.isVisible = !hasPendingLinks

            val hasBatchRows = state.batchDownloads.isNotEmpty()
            val hasBatchSession = state.activeBatchIds.isNotEmpty()
            binding.batchStatusList.isVisible = hasBatchRows
            binding.emptyStatusText.isVisible = !hasBatchRows
            binding.emptyStatusText.text = if (hasBatchSession) {
                getString(R.string.bulk_status_waiting)
            } else {
                getString(R.string.bulk_empty_state)
            }
        } finally {
            isRenderingState = false
        }
    }

    private fun consumeSharedUrls() {
        val activity = activity as? MainActivity ?: return
        batchViewModel.consumeSharedUrls(activity.consumePendingSharedUrls())
    }

    private fun pasteFromClipboard() {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        val pastedText = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).coerceToText(requireContext())?.toString().orEmpty()
        } else {
            ""
        }

        if (pastedText.isBlank()) {
            Snackbar.make(binding.root, R.string.bulk_no_links_found, Snackbar.LENGTH_LONG).show()
            return
        }

        batchViewModel.addLinksFromText(pastedText)
    }

    private fun profileLabel(profile: DownloadProfile): String {
        return "${profile.label} - ${profile.summary}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class PendingLinksAdapter(
    private val onRemove: (PendingBatchLink) -> Unit
) : ListAdapter<PendingBatchLink, PendingLinksAdapter.ViewHolder>(PendingLinkDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPendingBatchLinkBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onRemove)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemPendingBatchLinkBinding,
        private val onRemove: (PendingBatchLink) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PendingBatchLink) {
            binding.platformIcon.setImageResource(item.platform.iconRes)
            binding.platformName.text = item.platform.displayName
            binding.platformName.setTextColor(
                ContextCompat.getColor(binding.root.context, R.color.text_primary)
            )
            binding.sourceLabel.text = item.sourceLabel()
            binding.sourceUrl.text = item.shortUrl()
            binding.btnRemove.setOnClickListener { onRemove(item) }
        }
    }
}

private class PendingLinkDiffCallback : DiffUtil.ItemCallback<PendingBatchLink>() {
    override fun areItemsTheSame(oldItem: PendingBatchLink, newItem: PendingBatchLink): Boolean {
        return oldItem.url == newItem.url
    }

    override fun areContentsTheSame(oldItem: PendingBatchLink, newItem: PendingBatchLink): Boolean {
        return oldItem == newItem
    }
}

private class BatchDownloadAdapter :
    ListAdapter<DownloadItem, BatchDownloadAdapter.ViewHolder>(BatchDownloadDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBulkDownloadBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemBulkDownloadBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DownloadItem) {
            val context = binding.root.context
            binding.platformIcon.setImageResource(item.platform.iconRes)
            binding.platformText.text = item.platform.displayName
            binding.itemTitle.text = item.customFileName
            binding.itemSubtitle.text = item.originalUrl

            val statusText = when (item.status) {
                DownloadStatus.PENDING -> context.getString(R.string.batch_status_pending)
                DownloadStatus.DOWNLOADING -> context.getString(
                    R.string.download_progress,
                    item.progress.coerceIn(0, 100)
                )
                DownloadStatus.COMPLETED -> context.getString(R.string.batch_status_completed)
                DownloadStatus.FAILED -> context.getString(R.string.batch_status_failed)
                DownloadStatus.PAUSED -> context.getString(R.string.status_paused)
            }

            val statusColor = when (item.status) {
                DownloadStatus.PENDING -> R.color.text_secondary
                DownloadStatus.DOWNLOADING -> R.color.primary
                DownloadStatus.COMPLETED -> R.color.success
                DownloadStatus.FAILED -> R.color.error
                DownloadStatus.PAUSED -> R.color.warning
            }

            binding.statusText.text = statusText
            binding.statusText.setTextColor(ContextCompat.getColor(context, statusColor))
            binding.itemProgress.isVisible =
                item.status == DownloadStatus.PENDING || item.status == DownloadStatus.DOWNLOADING
            binding.itemProgress.isIndeterminate = item.status == DownloadStatus.PENDING
            if (item.status == DownloadStatus.DOWNLOADING) {
                binding.itemProgress.progress = item.progress.coerceIn(0, 100)
            }

            val detailText = when (item.status) {
                DownloadStatus.PENDING -> context.getString(R.string.bulk_detail_pending)
                DownloadStatus.DOWNLOADING -> context.getString(R.string.bulk_detail_downloading)
                DownloadStatus.COMPLETED -> item.getFormattedSize().ifBlank {
                    context.getString(R.string.bulk_detail_ready)
                }
                DownloadStatus.FAILED -> item.errorMessage?.ifBlank {
                    context.getString(R.string.bulk_detail_failed)
                } ?: context.getString(R.string.bulk_detail_failed)
                DownloadStatus.PAUSED -> context.getString(R.string.bulk_detail_paused)
            }

            binding.detailText.text = detailText
            binding.detailText.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (item.status == DownloadStatus.FAILED) R.color.error else R.color.text_secondary
                )
            )
            binding.errorText.isVisible =
                item.status == DownloadStatus.FAILED && !item.errorMessage.isNullOrBlank()
            binding.errorText.text = item.errorMessage
        }
    }
}

private class BatchDownloadDiffCallback : DiffUtil.ItemCallback<DownloadItem>() {
    override fun areItemsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean {
        return oldItem == newItem
    }
}
