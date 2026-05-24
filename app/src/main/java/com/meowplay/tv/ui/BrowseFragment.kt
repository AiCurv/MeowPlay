package com.meowplay.tv.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.meowplay.tv.R

/**
 * Browse tab — Placeholder for future features.
 *
 * Will eventually support:
 * - Local file browsing
 * - Network/SMB browsing
 * - DLNA/uPnP discovery
 * - Plugin system for additional sources
 */
class BrowseFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_browse, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val comingSoon = view.findViewById<TextView>(R.id.coming_soon_text)
        comingSoon.text = "Browse\n\nComing Soon!\n\nFuture features:\n• Local file browsing\n• Network/SMB shares\n• DLNA/uPnP discovery\n• Plugin sources"
    }
}
