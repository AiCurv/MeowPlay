package com.meowplay.tv

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.meowplay.tv.ui.MainPagerAdapter

/**
 * Main Activity for MeowPlay on Android TV.
 *
 * Uses a TabLayout + ViewPager2 for the 4 tabs:
 * - Home (Player / Paste Link)
 * - History (Saved links with copy, open-with, resume)
 * - Settings (Advanced cache configuration)
 * - Browse (Placeholder for future)
 */
class MainActivity : FragmentActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewPager = findViewById(R.id.view_pager)
        tabLayout = findViewById(R.id.tab_layout)

        // Setup ViewPager with tabs
        val pagerAdapter = MainPagerAdapter(this)
        viewPager.adapter = pagerAdapter

        // Connect TabLayout with ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Home"
                1 -> "History"
                2 -> "Settings"
                3 -> "Browse"
                else -> "Tab $position"
            }
            tab.setIcon(
                when (position) {
                    0 -> R.drawable.ic_home
                    1 -> R.drawable.ic_history
                    2 -> R.drawable.ic_settings
                    3 -> R.drawable.ic_browse
                    else -> 0
                }
            )
        }.attach()

        // Check if launched from external app with a video URL
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIncomingIntent(it) }
    }

    private fun handleIncomingIntent(intent: Intent) {
        // If launched from external app, route directly to player
        if (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_SEND) {
            val playerIntent = Intent(this, com.meowplay.tv.player.PlayerActivity::class.java)
            playerIntent.action = intent.action
            playerIntent.data = intent.data
            playerIntent.putExtras(intent.extras ?: Bundle())
            playerIntent.type = intent.type
            startActivity(playerIntent)
        }
    }
}
