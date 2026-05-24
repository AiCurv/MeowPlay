package com.meowplay.tv.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.meowplay.tv.R
import com.meowplay.tv.player.PlayerActivity

/**
 * Paste Link screen — TV-optimized with D-pad.
 * Large text input field + play button, auto-paste from clipboard.
 */
class PasteLinkActivity : FragmentActivity() {

    private lateinit var urlInput: EditText
    private lateinit var playBtn: Button
    private lateinit var backBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_paste_link)

        urlInput = findViewById(R.id.paste_url_input)
        playBtn = findViewById(R.id.paste_play_btn)
        backBtn = findViewById(R.id.back_btn)

        // D-pad focus chain
        backBtn.nextFocusDownId = R.id.paste_url_input
        urlInput.nextFocusDownId = R.id.paste_play_btn
        playBtn.nextFocusUpId = R.id.paste_url_input

        // Auto-paste from clipboard
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0)?.text?.toString()?.trim() ?: ""
            if (text.startsWith("http") || text.startsWith("magnet:") || text.startsWith("rtmp")) {
                if (urlInput.text.isEmpty()) urlInput.setText(text)
            }
        }

        // Play button
        playBtn.setOnClickListener { playUrl() }

        // Enter key on EditText also plays
        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO) { playUrl(); true } else false
        }

        // Back
        backBtn.setOnClickListener { finish() }
    }

    private fun playUrl() {
        val url = urlInput.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, PlayerActivity::class.java).apply {
            action = "com.meowplay.tv.PLAY"
            putExtra("url", url)
        }
        startActivity(intent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true }
        return super.onKeyDown(keyCode, event)
    }
}
