package com.meowplay.tv

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.fragment.app.FragmentActivity

/**
 * Main Activity using Leanback BrowseSupportFragment.
 * This is the PROPER way to build Android TV UIs — not ViewPager2.
 *
 * Structure:
 * - Left sidebar: Categories (Home, History, Settings)
 * - Right content: Rows of actionable cards
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_container, MainBrowseFragment())
                .commit()
        }

        // If launched from external app, go directly to player
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIncomingIntent(it) }
    }

    private fun handleIncomingIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_SEND) {
            val playerIntent = Intent(this, com.meowplay.tv.player.PlayerActivity::class.java)
            playerIntent.action = intent.action
            playerIntent.data = intent.data
            playerIntent.putExtras(intent.extras ?: Bundle())
            playerIntent.type = intent.type
            if (intent.clipData != null) playerIntent.setClipData(intent.clipData)
            startActivity(playerIntent)
        }
    }

    /**
     * BrowseSupportFragment — the standard Android TV "Netflix-style" layout.
     * Left sidebar with categories, right side with rows of cards.
     */
    class MainBrowseFragment : BrowseSupportFragment() {

        override fun onActivityCreated(savedInstanceState: Bundle?) {
            super.onActivityCreated(savedInstanceState)

            title = "MeowPlay"
            headersState = BrowseSupportFragment.HEADERS_ENABLED
            isHeadersTransitionOnBackEnabled = true
            brandColor = resources.getColor(R.color.accent_purple, null)

            val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

            // ─── Row 1: Play URL ──────────────────────────────
            val playHeader = HeaderItem(0, "Play")
            val playAdapter = ArrayObjectAdapter(CardPresenter())
            playAdapter.add(MenuCard("Paste Link", "Enter a video URL to play", "paste"))
            playAdapter.add(MenuCard("Play from History", "Resume a previously played video", "history"))
            rowsAdapter.add(ListRow(playHeader, playAdapter))

            // ─── Row 2: Cache Settings ────────────────────────
            val cacheHeader = HeaderItem(1, "Cache Settings")
            val cacheAdapter = ArrayObjectAdapter(CardPresenter())
            cacheAdapter.add(MenuCard("Disk Cache", "Video data saved to disk", "disk_cache"))
            cacheAdapter.add(MenuCard("RAM Buffer", "In-memory playback buffer", "ram_buffer"))
            cacheAdapter.add(MenuCard("Buffer Length", "How far ahead to buffer", "buffer_length"))
            cacheAdapter.add(MenuCard("Back Buffer", "Rewind cache behind position", "back_buffer"))
            cacheAdapter.add(MenuCard("Clear Cache", "Delete all cached data", "clear_cache"))
            rowsAdapter.add(ListRow(cacheHeader, cacheAdapter))

            // ─── Row 3: General ───────────────────────────────
            val generalHeader = HeaderItem(2, "General")
            val generalAdapter = ArrayObjectAdapter(CardPresenter())
            generalAdapter.add(MenuCard("Remote Control", "Enable API for mobile app", "remote"))
            generalAdapter.add(MenuCard("About MeowPlay", "v1.0.0", "about"))
            rowsAdapter.add(ListRow(generalHeader, generalAdapter))

            adapter = rowsAdapter

            onItemViewClickedListener = ItemViewClickedListener()
        }

        private inner class ItemViewClickedListener : OnItemViewClickedListener {
            override fun onItemClicked(
                itemViewHolder: Presenter.ViewHolder?,
                item: Any?,
                rowViewHolder: RowPresenter.ViewHolder?,
                row: Row?
            ) {
                if (item is MenuCard) {
                    when (item.action) {
                        "paste" -> {
                            val intent = Intent(activity, com.meowplay.tv.ui.PasteLinkActivity::class.java)
                            startActivity(intent)
                        }
                        "history" -> {
                            val intent = Intent(activity, com.meowplay.tv.ui.HistoryActivity::class.java)
                            startActivity(intent)
                        }
                        "disk_cache" -> showDiskCacheDialog()
                        "ram_buffer" -> showRamBufferDialog()
                        "buffer_length" -> showBufferLengthDialog()
                        "back_buffer" -> showBackBufferDialog()
                        "clear_cache" -> showClearCacheDialog()
                        "remote" -> toggleRemote()
                        "about" -> showAbout()
                    }
                }
            }

            private fun showDiskCacheDialog() {
                val cm = com.meowplay.tv.player.CacheManager.getInstance(requireContext())
                val current = requireContext().getSharedPreferences("meowplay_cache", android.content.Context.MODE_PRIVATE)
                    .getInt(com.meowplay.tv.player.CacheManager.KEY_DISK_CACHE, 0)
                val opts = com.meowplay.tv.player.CacheManager.DiskCacheOptions
                val idx = opts.VALUES.indexOf(current).coerceAtLeast(0)

                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Video Cache on Disk")
                    .setMessage("Amount of video data saved to disk for faster replay.\n\nCauses problems if set too high on devices with low storage space, such as Android TV.")
                    .setSingleChoiceItems(opts.LABELS, idx) { dialog, which ->
                        cm.setDiskCacheSize(opts.VALUES[which])
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            private fun showRamBufferDialog() {
                val cm = com.meowplay.tv.player.CacheManager.getInstance(requireContext())
                val current = requireContext().getSharedPreferences("meowplay_cache", android.content.Context.MODE_PRIVATE)
                    .getInt(com.meowplay.tv.player.CacheManager.KEY_RAM_BUFFER, 0)
                val opts = com.meowplay.tv.player.CacheManager.RamBufferOptions
                val idx = opts.VALUES.indexOf(current).coerceAtLeast(0)

                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Video Buffer Size (RAM)")
                    .setMessage("Amount of video data held in memory.\n\nCauses crashes if set too high on devices with low memory, such as Android TV.")
                    .setSingleChoiceItems(opts.LABELS, idx) { dialog, which ->
                        cm.setRamBufferSize(opts.VALUES[which])
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            private fun showBufferLengthDialog() {
                val cm = com.meowplay.tv.player.CacheManager.getInstance(requireContext())
                val current = requireContext().getSharedPreferences("meowplay_cache", android.content.Context.MODE_PRIVATE)
                    .getInt(com.meowplay.tv.player.CacheManager.KEY_BUFFER_LENGTH, 0)
                val opts = com.meowplay.tv.player.CacheManager.BufferLengthOptions
                val idx = opts.VALUES.indexOf(current).coerceAtLeast(0)

                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Video Buffer Length")
                    .setMessage("How far ahead to buffer video data.\n\nCauses crashes if set too high on devices with low memory, such as Android TV.")
                    .setSingleChoiceItems(opts.LABELS, idx) { dialog, which ->
                        cm.setBufferLength(opts.VALUES[which])
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            private fun showBackBufferDialog() {
                val cm = com.meowplay.tv.player.CacheManager.getInstance(requireContext())
                val current = requireContext().getSharedPreferences("meowplay_cache", android.content.Context.MODE_PRIVATE)
                    .getInt(com.meowplay.tv.player.CacheManager.KEY_BACK_BUFFER, 0)
                val opts = com.meowplay.tv.player.CacheManager.BackBufferOptions
                val idx = opts.VALUES.indexOf(current).coerceAtLeast(0)

                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Back Buffer (Rewind Cache)")
                    .setMessage("How much video data behind current position to keep.")
                    .setSingleChoiceItems(opts.LABELS, idx) { dialog, which ->
                        cm.setBackBuffer(opts.VALUES[which])
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            private fun showClearCacheDialog() {
                val cm = com.meowplay.tv.player.CacheManager.getInstance(requireContext())
                val size = cm.getTotalCacheSize()
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Clear Video & Image Cache")
                    .setMessage("Current cache: ${cm.formatCacheSize(size)}\n\nThis will delete all cached video and image data. Your history will be preserved.")
                    .setPositiveButton("Clear Cache") { _, _ -> cm.clearAllCache() }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            private fun toggleRemote() {
                val prefs = requireContext().getSharedPreferences("meowplay_prefs", android.content.Context.MODE_PRIVATE)
                val enabled = !prefs.getBoolean("remote_enabled", false)
                prefs.edit().putBoolean("remote_enabled", enabled).apply()
                val app = requireContext().applicationContext as MeowPlayApp
                if (enabled) app.startRemoteServer() else app.stopRemoteServer()
                android.widget.Toast.makeText(requireContext(),
                    "Remote control ${if (enabled) "enabled" else "disabled"}",
                    android.widget.Toast.LENGTH_SHORT).show()
            }

            private fun showAbout() {
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("MeowPlay v1.0.0")
                    .setMessage("Advanced Android TV Video Player\n\nExternal player for Stremio, CloudStream, Kodi\n\nPowered by Media3 ExoPlayer\n\nSupports: MP4, MKV, M3U8/HLS, DASH, WebM, FLV, MOV, and more")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    /** Simple data class for menu cards */
    data class MenuCard(
        val title: String,
        val description: String,
        val action: String
    )

    /** Presenter for rendering menu cards in the Leanback grid */
    class CardPresenter : Presenter() {
        override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
            val container = android.widget.LinearLayout(parent.context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(32, 24, 32, 24)
                background = createCardBackground(context)
                isFocusable = true
                isFocusableInTouchMode = false
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            val title = android.widget.TextView(parent.context).apply {
                setTextColor(android.graphics.Color.WHITE)
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            val desc = android.widget.TextView(parent.context).apply {
                setTextColor(android.graphics.Color.parseColor("#B0B0B0"))
                textSize = 14f
            }
            container.addView(title)
            container.addView(desc)

            // Focus scale effect for TV
            container.onFocusChangeListener = android.view.View.OnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.05f else 1.0f)
                    .scaleY(if (hasFocus) 1.05f else 1.0f)
                    .setDuration(150).start()
                v.background = createCardBackground(v.context, hasFocus)
            }

            return ViewHolder(container)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
            val card = item as MenuCard
            val container = viewHolder.view as android.widget.LinearLayout
            (container.getChildAt(0) as android.widget.TextView).text = card.title
            (container.getChildAt(1) as android.widget.TextView).text = card.description
        }

        override fun onUnbindViewHolder(viewHolder: ViewHolder) {}

        private fun createCardBackground(context: android.content.Context, focused: Boolean = false) =
            android.graphics.drawable.GradientDrawable().apply {
                setColor(if (focused) android.graphics.Color.parseColor("#2D2D5E") else android.graphics.Color.parseColor("#1E1E1E"))
                cornerRadius = 12f
                setStroke(if (focused) 3 else 0, if (focused) android.graphics.Color.parseColor("#BB86FC") else android.graphics.Color.TRANSPARENT)
            }
    }
}
