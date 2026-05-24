package com.meowplay.tv

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
import com.meowplay.tv.data.HistoryRepository
import com.meowplay.tv.player.CacheManager
import com.meowplay.tv.player.PlayerActivity
import com.meowplay.tv.ui.AboutActivity
import com.meowplay.tv.ui.CacheManagerActivity
import com.meowplay.tv.ui.SettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Main Activity — 4-Tab Leanback Browse UI.
 * Tabs: Home | Media | Settings | About
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

        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIncomingIntent(it) }
    }

    private fun handleIncomingIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_SEND) {
            val playerIntent = Intent(this, PlayerActivity::class.java)
            playerIntent.action = intent.action
            playerIntent.data = intent.data
            playerIntent.putExtras(intent.extras ?: Bundle())
            playerIntent.type = intent.type
            if (intent.clipData != null) playerIntent.setClipData(intent.clipData)
            startActivity(playerIntent)
        }
    }

    class MainBrowseFragment : BrowseSupportFragment() {

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        override fun onActivityCreated(savedInstanceState: Bundle?) {
            super.onActivityCreated(savedInstanceState)

            title = "MeowPlay"
            headersState = BrowseSupportFragment.HEADERS_ENABLED
            isHeadersTransitionOnBackEnabled = true
            brandColor = resources.getColor(R.color.accent_purple, null)

            val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

            // ─── Tab 1: Home ──────────────────────────────────────────
            val homeHeader = HeaderItem(0, "Home")
            val homeAdapter = ArrayObjectAdapter(CardPresenter())
            homeAdapter.add(MenuCard("Paste Link", "Enter a video URL to play", "paste"))
            homeAdapter.add(MenuCard("Recent History", "Your recently played videos", "history"))
            rowsAdapter.add(ListRow(homeHeader, homeAdapter))

            // ─── Tab 2: Media ─────────────────────────────────────────
            val mediaHeader = HeaderItem(1, "Media")
            val mediaAdapter = ArrayObjectAdapter(CardPresenter())
            mediaAdapter.add(MenuCard("Media Browser", "Coming Soon", "media_placeholder"))
            rowsAdapter.add(ListRow(mediaHeader, mediaAdapter))

            // ─── Tab 3: Settings ──────────────────────────────────────
            val settingsHeader = HeaderItem(2, "Settings")
            val settingsAdapter = ArrayObjectAdapter(CardPresenter())
            settingsAdapter.add(MenuCard("Cache Manager", "Advanced cache & buffer settings", "cache_manager"))
            settingsAdapter.add(MenuCard("All Settings", "Decoder, Audio, Subtitle, Network...", "all_settings"))
            rowsAdapter.add(ListRow(settingsHeader, settingsAdapter))

            // ─── Tab 4: About ─────────────────────────────────────────
            val aboutHeader = HeaderItem(3, "About")
            val aboutAdapter = ArrayObjectAdapter(CardPresenter())
            aboutAdapter.add(MenuCard("About MeowPlay", "v1.2.0 • Credits • Privacy", "about"))
            rowsAdapter.add(ListRow(aboutHeader, aboutAdapter))

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
                        "paste" -> startActivity(Intent(activity, com.meowplay.tv.ui.PasteLinkActivity::class.java))
                        "history" -> startActivity(Intent(activity, com.meowplay.tv.ui.HistoryActivity::class.java))
                        "media_placeholder" -> Toast.makeText(activity, "Media browser coming soon!", Toast.LENGTH_SHORT).show()
                        "cache_manager" -> startActivity(Intent(activity, CacheManagerActivity::class.java))
                        "all_settings" -> startActivity(Intent(activity, SettingsActivity::class.java))
                        "about" -> startActivity(Intent(activity, AboutActivity::class.java))
                    }
                }
            }
        }
    }

    data class MenuCard(
        val title: String,
        val description: String,
        val action: String
    )

    class CardPresenter : Presenter() {
        override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
            val container = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 24, 32, 24)
                background = createCardBackground(context, false)
                isFocusable = true
                isFocusableInTouchMode = false
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                minimumWidth = 280
            }
            val title = TextView(parent.context).apply {
                setTextColor(android.graphics.Color.WHITE)
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            val desc = TextView(parent.context).apply {
                setTextColor(android.graphics.Color.parseColor("#B0B0B0"))
                textSize = 14f
            }
            container.addView(title)
            container.addView(desc)

            container.onFocusChangeListener = android.view.View.OnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.03f else 1.0f)
                    .scaleY(if (hasFocus) 1.03f else 1.0f)
                    .setDuration(150).start()
                v.background = createCardBackground(v.context, hasFocus)
            }

            return ViewHolder(container)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
            val card = item as MenuCard
            val container = viewHolder.view as LinearLayout
            (container.getChildAt(0) as TextView).text = card.title
            (container.getChildAt(1) as TextView).text = card.description
        }

        override fun onUnbindViewHolder(viewHolder: ViewHolder) {}

        private fun createCardBackground(context: android.content.Context, focused: Boolean) =
            android.graphics.drawable.GradientDrawable().apply {
                setColor(if (focused) 0x2D2D5E else 0xFF1A1A2E.toInt())
                cornerRadius = 16f
                if (focused) {
                    setStroke(2, 0x40FFFFFF.toInt())  // Glass border
                }
            }
    }
}
