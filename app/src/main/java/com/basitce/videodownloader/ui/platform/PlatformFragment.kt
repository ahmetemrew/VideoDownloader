package com.basitce.videodownloader.ui.platform

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.basitce.videodownloader.R
import com.basitce.videodownloader.data.model.Platform
import com.basitce.videodownloader.databinding.FragmentPlatformBinding
import com.basitce.videodownloader.databinding.ItemPlatformBinding

class PlatformFragment : Fragment() {

    private var _binding: FragmentPlatformBinding? = null
    private val binding get() = _binding!!

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
        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        val platforms = Platform.getSupportedPlatforms()
        
        binding.platformsRecycler.apply {
            layoutManager = GridLayoutManager(context, 2)
            adapter = PlatformAdapter(platforms) { platform ->
                onPlatformClicked(platform)
            }
        }
    }

    private fun onPlatformClicked(platform: Platform) {
        // Universal fragment'a git ve platform bilgisiyle
        Toast.makeText(
            context,
            "${platform.displayName} seçildi. Evrensel sekmeye git ve linki yapıştır.",
            Toast.LENGTH_SHORT
        ).show()
        
        // Bottom navigation'da Universal tab'a geç
        findNavController().navigate(R.id.universalFragment)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/**
 * Platform kartları için RecyclerView adapter
 */
class PlatformAdapter(
    private val platforms: List<Platform>,
    private val onPlatformClick: (Platform) -> Unit
) : RecyclerView.Adapter<PlatformAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPlatformBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(platforms[position])
    }

    override fun getItemCount() = platforms.size

    inner class ViewHolder(
        private val binding: ItemPlatformBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(platform: Platform) {
            binding.apply {
                platformIcon.setImageResource(platform.iconRes)
                platformName.text = platform.displayName
                platformDescription.text = platform.description
                
                platformCard.setOnClickListener {
                    onPlatformClick(platform)
                }
            }
        }
    }
}
