package com.meowplay.tv.ui

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import androidx.fragment.app.FragmentActivity
import com.meowplay.tv.R
import com.meowplay.tv.player.PlayerActivity

/**
 * Dialog-style activity for pasting a video URL.
 * Provides a large text field optimized for TV remote input.
 */
class PasteLinkActivity : FragmentActivity() {

    private lateinit var urlInput: EditText
    private lateinit var playButton: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_paste_link)

        urlInput = findViewById(R.id.paste_url_input)
        playButton = findViewById(R.id.paste_play_button)

        // Auto-paste from clipboard
        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0)?.text?.toString()?.trim() ?: ""
            if (text.startsWith("http") || text.startsWith("magnet:") ||
                text.startsWith("rtmp:") || text.startsWith("rtsp:")) {
                urlInput.setText(text)
            }
        }

        playButton.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isNotEmpty()) {
                val intent = Intent(this, PlayerActivity::class.java).apply {
                    action = "com.meowplay.tv.PLAY"
                    putExtra("url", url)
                }
                startActivity(intent)
                finish()
            }
        }

        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                val url = urlInput.text.toString().trim()
                if (url.isNotEmpty()) {
                    val intent = Intent(this, PlayerActivity::class.java).apply {
                        action = "com.meowplay.tv.PLAY"
                        putExtra("url", url)
                    }
                    startActivity(intent)
                    finish()
                }
                true
            } else false
        }
    }
}
