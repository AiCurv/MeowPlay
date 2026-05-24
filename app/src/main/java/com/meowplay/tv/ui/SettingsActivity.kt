package com.meowplay.tv.ui

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceFragmentCompat
import com.meowplay.tv.R

/**
 * Settings Activity (alternative entry point for Leanback settings).
 * The main settings are in SettingsFragment, but this provides
 * Android TV Leanback-compatible preference screen.
 */
class SettingsActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsPreferenceFragment())
                .commit()
        }
    }

    class SettingsPreferenceFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            // Settings are handled by SettingsFragment directly
            // This is a fallback for Leanback navigation
            setPreferencesFromResource(R.xml.preferences, rootKey)
        }
    }
}
