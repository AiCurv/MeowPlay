package com.meowplay.tv.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.meowplay.tv.R

/**
 * About Activity — Version, Credits, Changelog, Privacy Policy
 */
class AboutActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val backBtn = findViewById<Button>(R.id.back_btn)
        backBtn.setOnClickListener { finish() }

        val creditsLayout = findViewById<LinearLayout>(R.id.credits_layout)

        // Credits
        addCreditItem(creditsLayout, "Powered by GLM 5.1 AI", "AI model from z.ai — Built this app") {
            openUrl("https://z.ai")
        }
        addCreditItem(creditsLayout, "AiCurv", "Developer & Designer") {
            openUrl("https://github.com/AiCurv")
        }
        addCreditItem(creditsLayout, "AndroidX Media3", "ExoPlayer video engine by Google") {
            openUrl("https://developer.android.com/media/media3")
        }
        addCreditItem(creditsLayout, "Android TV Leanback", "TV UI framework by Google") {
            openUrl("https://developer.android.com/training/tv")
        }

        // Changelog
        val changelogText = findViewById<TextView>(R.id.txt_changelog)
        changelogText.text = """
v1.2.0 — Complete Rewrite
• Cache settings now ACTUALLY configure Media3 ExoPlayer
• Glass UI overlay controls in player
• Double-back-to-exit with buffer info
• Quality/Audio/Subtitle track selection
• Playback speed control
• Aspect ratio switching
• Audio delay adjustment
• Video info display
• 8 settings categories with selectable options
• About page with credits

v1.0.0 — Initial Release
• Basic external player support
• History with Room database
• Cache manager skeleton
• Remote server API
        """.trimIndent()

        // Privacy Policy
        val privacyText = findViewById<TextView>(R.id.txt_privacy)
        privacyText.text = """
MeowPlay Privacy Policy

MeowPlay does not collect, store, or transmit any personal data. All video URLs, playback history, and settings are stored locally on your device only.

• No analytics or tracking
• No data shared with third parties
• Cache data is stored locally and can be cleared at any time from Settings
• The optional remote server feature only operates on your local WiFi network

If you have questions, contact: meowplay@aicurv.com
        """.trimIndent()
    }

    private fun addCreditItem(layout: LinearLayout, title: String, subtitle: String, onClick: () -> Unit) {
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
        }

        container.addView(titleView)
        container.addView(subtitleView)
        layout.addView(container)
    }

    private fun createRowBackground(focused: Boolean) = android.graphics.drawable.GradientDrawable().apply {
        setColor(if (focused) 0x2D2D5E else 0xFF1A1A2E.toInt())
        cornerRadius = 12f
        if (focused) setStroke(2, 0x40FFFFFF.toInt())
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            // No browser available on TV
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true }
        return super.onKeyDown(keyCode, event)
    }
}
