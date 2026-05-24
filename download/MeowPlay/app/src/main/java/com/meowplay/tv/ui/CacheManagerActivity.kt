package com.meowplay.tv.ui

import android.app.Dialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.meowplay.tv.R
import com.meowplay.tv.player.CacheManager

/**
 * Cache Manager Activity — THE MAIN FEATURE of MeowPlay.
 *
 * Every setting ACTUALLY configures Media3:
 * - Disk Cache → SimpleCache maxCacheSize
 * - RAM Buffer → DefaultLoadControl setTargetBufferBytes
 * - Buffer Length → DefaultLoadControl setBufferDurationsMs
 * - Clear Cache → SimpleCache.release() + delete dir
 *
 * CRITICAL: NEVER use setMessage() + setSingleChoiceItems() together!
 */
class CacheManagerActivity : FragmentActivity() {

    private lateinit var cacheList: LinearLayout
    private lateinit var backBtn: Button
    private lateinit var txtCacheSize: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cache_manager)

        cacheList = findViewById(R.id.cache_list)
        backBtn = findViewById(R.id.back_btn)
        txtCacheSize = findViewById(R.id.txt_cache_size)

        backBtn.setOnClickListener { finish() }

        val cm = CacheManager.getInstance(this)

        // Show current cache size
        updateCacheSize(cm)

        // ─── Video Cache on Disk ──────────────────────────────────────
        addCacheRow(
            "Video Cache on Disk",
            "Sets maxCacheSize on SimpleCache.\nCurrent: ${CacheManager.DiskCacheOptions.labelFor(getCurrentDiskCacheValue(cm))}"
        ) {
            showOptionDialog("Video Cache on Disk", CacheManager.DiskCacheOptions.LABELS,
                CacheManager.DiskCacheOptions.VALUES.indexOf(getCurrentDiskCacheValue(cm)).coerceAtLeast(0)) { idx ->
                cm.setDiskCacheSize(CacheManager.DiskCacheOptions.VALUES[idx])
                Toast.makeText(this, "Disk cache set to ${CacheManager.DiskCacheOptions.LABELS[idx]}", Toast.LENGTH_SHORT).show()
                recreate()
            }
        }

        // ─── Video Buffer Size ────────────────────────────────────────
        addCacheRow(
            "Video Buffer Size (RAM)",
            "Sets targetBufferBytes on DefaultLoadControl.\nCurrent: ${CacheManager.RamBufferOptions.labelFor(getCurrentRamBufferValue(cm))}"
        ) {
            showOptionDialog("Video Buffer Size (RAM)", CacheManager.RamBufferOptions.LABELS,
                CacheManager.RamBufferOptions.VALUES.indexOf(getCurrentRamBufferValue(cm)).coerceAtLeast(0)) { idx ->
                cm.setRamBufferSize(CacheManager.RamBufferOptions.VALUES[idx])
                Toast.makeText(this, "RAM buffer set to ${CacheManager.RamBufferOptions.LABELS[idx]}", Toast.LENGTH_SHORT).show()
                recreate()
            }
        }

        // ─── Video Buffer Length ──────────────────────────────────────
        addCacheRow(
            "Video Buffer Length",
            "Sets bufferDurationsMs on DefaultLoadControl.\nCurrent: ${CacheManager.BufferLengthOptions.labelFor(getCurrentBufferLengthValue(cm))}"
        ) {
            showOptionDialog("Video Buffer Length", CacheManager.BufferLengthOptions.LABELS,
                CacheManager.BufferLengthOptions.VALUES.indexOf(getCurrentBufferLengthValue(cm)).coerceAtLeast(0)) { idx ->
                cm.setBufferLength(CacheManager.BufferLengthOptions.VALUES[idx])
                Toast.makeText(this, "Buffer length set to ${CacheManager.BufferLengthOptions.LABELS[idx]}", Toast.LENGTH_SHORT).show()
                recreate()
            }
        }

        // ─── Clear Cache Buttons ──────────────────────────────────────
        addCacheRow("Clear Video Cache", "Deletes ExoPlayer disk cache directory") {
            val size = cm.formatCacheSize(cm.getCacheSize())
            val dialog = Dialog(this, R.style.GlassDialog)
            dialog.setContentView(R.layout.dialog_exit_confirm)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            dialog.findViewById<TextView>(R.id.txt_buffer_info).text = "Video cache: $size\nThis will delete cached video data."
            dialog.findViewById<Button>(R.id.btn_yes).setOnClickListener {
                cm.clearVideoCache()
                Toast.makeText(this, "Video cache cleared!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                recreate()
            }
            dialog.findViewById<Button>(R.id.btn_no).setOnClickListener { dialog.dismiss() }
            dialog.show()
        }

        addCacheRow("Clear All Cache", "Deletes entire cache directory (video + images)") {
            val size = cm.formatCacheSize(cm.getTotalCacheSize())
            val dialog = Dialog(this, R.style.GlassDialog)
            dialog.setContentView(R.layout.dialog_exit_confirm)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            dialog.findViewById<TextView>(R.id.txt_buffer_info).text = "Total cache: $size\nThis will delete ALL cached data."
            dialog.findViewById<Button>(R.id.btn_yes).setOnClickListener {
                cm.clearAllCache()
                Toast.makeText(this, "All cache cleared!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                recreate()
            }
            dialog.findViewById<Button>(R.id.btn_no).setOnClickListener { dialog.dismiss() }
            dialog.show()
        }
    }

    private fun getCurrentDiskCacheValue(cm: CacheManager): Int {
        val prefs = getSharedPreferences("meowplay_cache", MODE_PRIVATE)
        return prefs.getInt(CacheManager.KEY_DISK_CACHE, 0)
    }

    private fun getCurrentRamBufferValue(cm: CacheManager): Int {
        val prefs = getSharedPreferences("meowplay_cache", MODE_PRIVATE)
        return prefs.getInt(CacheManager.KEY_RAM_BUFFER, 0)
    }

    private fun getCurrentBufferLengthValue(cm: CacheManager): Int {
        val prefs = getSharedPreferences("meowplay_cache", MODE_PRIVATE)
        return prefs.getInt(CacheManager.KEY_BUFFER_LENGTH, 0)
    }

    private fun updateCacheSize(cm: CacheManager) {
        val videoCache = cm.formatCacheSize(cm.getCacheSize())
        val totalCache = cm.formatCacheSize(cm.getTotalCacheSize())
        txtCacheSize.text = "Video Cache: $videoCache  |  Total: $totalCache"
    }

    private fun addCacheRow(title: String, subtitle: String, onClick: () -> Unit) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
            background = createRowBackground(false)
            isFocusable = true
            isClickable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { onClick() }
            onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                v.background = createRowBackground(hasFocus)
                v.animate().scaleX(if (hasFocus) 1.01f else 1.0f)
                    .scaleY(if (hasFocus) 1.01f else 1.0f)
                    .setDuration(100).start()
            }
        }

        val titleView = TextView(this).apply {
            text = title
            setTextColor(android.graphics.Color.WHITE)
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val subtitleView = TextView(this).apply {
            text = subtitle
            setTextColor(0xFFB0B0B0.toInt())
            textSize = 13f
            setLineSpacing(2f, 1f)
        }

        container.addView(titleView)
        container.addView(subtitleView)
        cacheList.addView(container)
    }

    private fun createRowBackground(focused: Boolean) = android.graphics.drawable.GradientDrawable().apply {
        setColor(if (focused) 0x2D2D5E else 0xFF1A1A2E.toInt())
        cornerRadius = 12f
        if (focused) setStroke(2, 0x40FFFFFF.toInt())
    }

    private fun showOptionDialog(title: String, items: Array<String>, currentIndex: Int, onSelect: (Int) -> Unit) {
        val dialog = Dialog(this, R.style.GlassDialog)
        dialog.setContentView(R.layout.dialog_track_selection)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val txtDialogTitle = dialog.findViewById<TextView>(R.id.txt_dialog_title)
        val trackListLayout = dialog.findViewById<LinearLayout>(R.id.track_list_layout)

        txtDialogTitle.text = title

        items.forEachIndexed { index, label ->
            val btn = Button(this).apply {
                text = if (index == currentIndex) "● $label" else "  $label"
                setTextColor(if (index == currentIndex) 0xFFBB86FC.toInt() else 0xFFFFFFFF.toInt())
                setBackgroundColor(0x1A1A2E.toInt())
                setPadding(24, 16, 24, 16)
                textSize = 16f
                isFocusable = true
                setOnFocusChangeListener { v, hasFocus ->
                    v.setBackgroundColor(if (hasFocus) 0x2D2D5E.toInt() else 0x1A1A2E.toInt())
                }
                setOnClickListener {
                    onSelect(index)
                    dialog.dismiss()
                }
            }
            trackListLayout.addView(btn)
        }

        dialog.show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true }
        return super.onKeyDown(keyCode, event)
    }
}
