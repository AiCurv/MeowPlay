package com.meowplay.tv.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * Pager adapter for the 4 main tabs in MeowPlay.
 */
class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    private val fragments = listOf(
        { HomeFragment() },
        { HistoryFragment() },
        { SettingsFragment() },
        { BrowseFragment() }
    )

    override fun getItemCount(): Int = fragments.size

    override fun createFragment(position: Int): Fragment {
        return fragments[position]()
    }
}
