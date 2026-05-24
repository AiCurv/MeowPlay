package com.meowplay.tv.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.meowplay.tv.R
import com.meowplay.tv.player.PlayerActivity

/**
 * Home tab — Main landing page with paste-link functionality.
 *
 * Features:
 * - App logo and welcome message
 * - Paste URL text field for manual video link entry
 * - Quick play button
 * - Info about supported formats
 */
class HomeFragment : Fragment() {

    private lateinit var urlInput: EditText
    private lateinit var playButton: Button
    private lateinit var logoImage: ImageView
    private lateinit var welcomeText: TextView
    private lateinit var supportedFormatsText: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        urlInput = view.findViewById(R.id.url_input)
        playButton = view.findViewById(R.id.play_button)
        logoImage = view.findViewById(R.id.logo_image)
        welcomeText = view.findViewById(R.id.welcome_text)
        supportedFormatsText = view.findViewById(R.id.supported_formats_text)

        playButton.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isNotEmpty()) {
                playUrl(url)
            }
        }

        // Handle paste from clipboard
        urlInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // Auto-paste from clipboard if available
                val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val pasteText = clip.getItemAt(0)?.text?.toString()?.trim() ?: ""
                    if (pasteText.startsWith("http") || pasteText.startsWith("magnet:") ||
                        pasteText.startsWith("rtmp:") || pasteText.startsWith("rtsp:")) {
                        if (urlInput.text.isEmpty()) {
                            urlInput.setText(pasteText)
                        }
                    }
                }
            }
        }

        // D-pad center on the EditText also triggers play
        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                val url = urlInput.text.toString().trim()
                if (url.isNotEmpty()) {
                    playUrl(url)
                }
                true
            } else {
                false
            }
        }
    }

    private fun playUrl(url: String) {
        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            action = "com.meowplay.tv.PLAY"
            putExtra("url", url)
            putExtra("title", extractTitle(url))
        }
        startActivity(intent)
    }

    private fun extractTitle(url: String): String {
        return try {
            val path = url.substringAfterLast("/")
            path.substringBeforeLast(".").replace("_", " ").replace("-", " ").take(50)
        } catch (e: Exception) {
            url.take(50)
        }
    }
}
