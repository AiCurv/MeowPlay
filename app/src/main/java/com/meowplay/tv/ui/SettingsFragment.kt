package com.meowplay.tv.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.meowplay.tv.R
import com.meowplay.tv.player.CacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings tab — Advanced cache configuration.
 *
 * This is the MAIN feature of MeowPlay. Provides granular control over:
 * 1. Video Disk Cache (Auto to 5GB)
 * 2. Video RAM Buffer Size (Auto to 1GB)
 * 3. Video Buffer Length (Auto to 30 min)
 * 4. Back Buffer (Auto to 2 min)
 * 5. Clear Video & Image Cache
 * 6. Remote control toggle (for future mobile app)
 */
class SettingsFragment : Fragment() {

    private lateinit var cacheManager: CacheManager

    // UI elements
    private lateinit var diskCacheLabel: TextView
    private lateinit var diskCacheValue: TextView
    private lateinit var ramBufferLabel: TextView
    private lateinit var ramBufferValue: TextView
    private lateinit var bufferLengthLabel: TextView
    private lateinit var bufferLengthValue: TextView
    private lateinit var backBufferLabel: TextView
    private lateinit var backBufferValue: TextView
    private lateinit var clearCacheButton: TextView
    private lateinit var currentCacheSizeLabel: TextView
    private lateinit var remoteToggleLabel: TextView
    private lateinit var remoteToggleValue: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cacheManager = CacheManager.getInstance(requireContext())

        // Bind views
        diskCacheLabel = view.findViewById(R.id.disk_cache_label)
        diskCacheValue = view.findViewById(R.id.disk_cache_value)
        ramBufferLabel = view.findViewById(R.id.ram_buffer_label)
        ramBufferValue = view.findViewById(R.id.ram_buffer_value)
        bufferLengthLabel = view.findViewById(R.id.buffer_length_label)
        bufferLengthValue = view.findViewById(R.id.buffer_length_value)
        backBufferLabel = view.findViewById(R.id.back_buffer_label)
        backBufferValue = view.findViewById(R.id.back_buffer_value)
        clearCacheButton = view.findViewById(R.id.clear_cache_button)
        currentCacheSizeLabel = view.findViewById(R.id.current_cache_size)
        remoteToggleLabel = view.findViewById(R.id.remote_label)
        remoteToggleValue = view.findViewById(R.id.remote_value)

        // Setup click listeners
        setupDiskCacheSetting()
        setupRamBufferSetting()
        setupBufferLengthSetting()
        setupBackBufferSetting()
        setupClearCache()
        setupRemoteToggle()

        // Update all displayed values
        refreshValues()

        // Refresh cache size
        refreshCacheSize()
    }

    override fun onResume() {
        super.onResume()
        refreshValues()
        refreshCacheSize()
    }

    private fun refreshValues() {
        val prefs = requireContext().getSharedPreferences("meowplay_cache", android.content.Context.MODE_PRIVATE)

        diskCacheValue.text = CacheManager.DiskCacheOptions.getLabelForValue(
            prefs.getInt(CacheManager.Keys.DISK_CACHE_SIZE, 0)
        )
        ramBufferValue.text = CacheManager.RamBufferOptions.getLabelForValue(
            prefs.getInt(CacheManager.Keys.RAM_BUFFER_SIZE, 0)
        )
        bufferLengthValue.text = CacheManager.BufferLengthOptions.getLabelForValue(
            prefs.getInt(CacheManager.Keys.BUFFER_LENGTH, 0)
        )
        backBufferValue.text = CacheManager.BackBufferOptions.getLabelForValue(
            prefs.getInt(CacheManager.Keys.BACK_BUFFER, 0)
        )

        // Remote toggle
        val remotePrefs = requireContext().getSharedPreferences("meowplay_prefs", android.content.Context.MODE_PRIVATE)
        val remoteEnabled = remotePrefs.getBoolean("remote_enabled", false)
        remoteToggleValue.text = if (remoteEnabled) "Enabled" else "Disabled"
    }

    private fun refreshCacheSize() {
        lifecycleScope.launch {
            val size = withContext(Dispatchers.IO) {
                cacheManager.getTotalCacheSize()
            }
            currentCacheSizeLabel.text = "Current cache: ${cacheManager.formatCacheSize(size)}"
        }
    }

    // ─── Disk Cache Setting ──────────────────────────────────────────────────

    private fun setupDiskCacheSetting() {
        val row = view?.findViewById<View>(R.id.disk_cache_row)
        row?.setOnClickListener { showDiskCacheDialog() }
        row?.isFocusable = true
        row?.nextFocusDownId = R.id.ram_buffer_row
    }

    private fun showDiskCacheDialog() {
        val options = CacheManager.DiskCacheOptions
        val currentValue = requireContext().getSharedPreferences("meowplay_cache", android.content.Context.MODE_PRIVATE)
            .getInt(CacheManager.Keys.DISK_CACHE_SIZE, 0)
        val selectedIndex = options.VALUES.indexOf(currentValue).coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle("Video Disk Cache")
            .setMessage("Amount of video data saved to disk for faster replay.\nHigher values use more storage but allow rewinding without re-downloading.\n\n⚠ Set too high on devices with low storage (like Android TV) may cause issues.")
            .setSingleChoiceItems(options.LABELS, selectedIndex) { dialog, which ->
                val selectedValue = options.VALUES[which]
                cacheManager.setDiskCacheSize(selectedValue)
                diskCacheValue.text = options.getLabelForValue(selectedValue)
                dialog.dismiss()
                refreshCacheSize()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─── RAM Buffer Setting ──────────────────────────────────────────────────

    private fun setupRamBufferSetting() {
        val row = view?.findViewById<View>(R.id.ram_buffer_row)
        row?.setOnClickListener { showRamBufferDialog() }
        row?.isFocusable = true
    }

    private fun showRamBufferDialog() {
        val options = CacheManager.RamBufferOptions
        val currentValue = requireContext().getSharedPreferences("meowplay_cache", android.content.Context.MODE_PRIVATE)
            .getInt(CacheManager.Keys.RAM_BUFFER_SIZE, 0)
        val selectedIndex = options.VALUES.indexOf(currentValue).coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle("Video Buffer Size (RAM)")
            .setMessage("Amount of video data held in memory for smooth playback.\nHigher values reduce buffering but use more RAM.\n\n⚠ Values above 300MB may cause crashes on low-memory devices like Android TV boxes.")
            .setSingleChoiceItems(options.LABELS, selectedIndex) { dialog, which ->
                val selectedValue = options.VALUES[which]
                val warning = options.getWarningLevel(selectedValue)

                if (warning == CacheManager.WarningLevel.MAY_CRASH) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("⚠ Warning")
                        .setMessage("Setting RAM buffer to ${options.getLabelForValue(selectedValue)} may cause the app to crash on your device due to memory limitations. Are you sure?")
                        .setPositiveButton("I understand") { _, _ ->
                            cacheManager.setRamBufferSize(selectedValue)
                            ramBufferValue.text = options.getLabelForValue(selectedValue)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    cacheManager.setRamBufferSize(selectedValue)
                    ramBufferValue.text = options.getLabelForValue(selectedValue)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─── Buffer Length Setting ────────────────────────────────────────────────

    private fun setupBufferLengthSetting() {
        val row = view?.findViewById<View>(R.id.buffer_length_row)
        row?.setOnClickListener { showBufferLengthDialog() }
        row?.isFocusable = true
    }

    private fun showBufferLengthDialog() {
        val options = CacheManager.BufferLengthOptions
        val currentValue = requireContext().getSharedPreferences("meowplay_cache", android.content.Context.MODE_PRIVATE)
            .getInt(CacheManager.Keys.BUFFER_LENGTH, 0)
        val selectedIndex = options.VALUES.indexOf(currentValue).coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle("Video Buffer Length")
            .setMessage("How far ahead MeowPlay should buffer video data.\nHigher values provide smoother playback on unstable connections but use more data and memory.\n\n💡 Tip: 2-5 minutes is usually sufficient for most connections.")
            .setSingleChoiceItems(options.LABELS, selectedIndex) { dialog, which ->
                val selectedValue = options.VALUES[which]
                cacheManager.setBufferLength(selectedValue)
                bufferLengthValue.text = options.getLabelForValue(selectedValue)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─── Back Buffer Setting ──────────────────────────────────────────────────

    private fun setupBackBufferSetting() {
        val row = view?.findViewById<View>(R.id.back_buffer_row)
        row?.setOnClickListener { showBackBufferDialog() }
        row?.isFocusable = true
    }

    private fun showBackBufferDialog() {
        val options = CacheManager.BackBufferOptions
        val currentValue = requireContext().getSharedPreferences("meowplay_cache", android.content.Context.MODE_PRIVATE)
            .getInt(CacheManager.Keys.BACK_BUFFER, 0)
        val selectedIndex = options.VALUES.indexOf(currentValue).coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle("Back Buffer (Rewind Cache)")
            .setMessage("How much video data behind the current position to keep in memory.\nHigher values allow smoother rewinding but use more RAM.\n\n💡 Tip: 30 seconds is usually sufficient.")
            .setSingleChoiceItems(options.LABELS, selectedIndex) { dialog, which ->
                val selectedValue = options.VALUES[which]
                cacheManager.setBackBuffer(selectedValue)
                backBufferValue.text = options.getLabelForValue(selectedValue)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─── Clear Cache ──────────────────────────────────────────────────────────

    private fun setupClearCache() {
        clearCacheButton.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Clear Video & Image Cache")
                .setMessage("This will delete all cached video and image data. Your history and settings will be preserved.")
                .setPositiveButton("Clear Cache") { _, _ ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            cacheManager.clearAllCache()
                        }
                        refreshCacheSize()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    // ─── Remote Toggle (for future mobile app) ────────────────────────────────

    private fun setupRemoteToggle() {
        val row = view?.findViewById<View>(R.id.remote_row)
        row?.setOnClickListener { toggleRemote() }
        row?.isFocusable = true

        val prefs = requireContext().getSharedPreferences("meowplay_prefs", android.content.Context.MODE_PRIVATE)
        val remoteEnabled = prefs.getBoolean("remote_enabled", false)
        remoteToggleValue.text = if (remoteEnabled) "Enabled" else "Disabled"
    }

    private fun toggleRemote() {
        val prefs = requireContext().getSharedPreferences("meowplay_prefs", android.content.Context.MODE_PRIVATE)
        val currentlyEnabled = prefs.getBoolean("remote_enabled", false)
        val newValue = !currentlyEnabled

        prefs.edit().putBoolean("remote_enabled", newValue).apply()
        remoteToggleValue.text = if (newValue) "Enabled" else "Disabled"

        val app = requireContext().applicationContext as com.meowplay.tv.MeowPlayApp
        if (newValue) {
            app.startRemoteServer()
        } else {
            app.stopRemoteServer()
        }
    }
}
