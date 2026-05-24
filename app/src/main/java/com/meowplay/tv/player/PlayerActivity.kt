package com.meowplay.tv.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.meowplay.tv.R
import com.meowplay.tv.data.HistoryEntry
import com.meowplay.tv.data.HistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Full-screen video player activity.
 *
 * Receives video URLs from:
 * - External apps (Stremio, CloudStream, Kodi) via ACTION_VIEW / ACTION_SEND intents
 * - Internal history (resume playback)
 * - Manual URL paste
 */
class PlayerActivity : FragmentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var meowPlayer: MeowExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var errorText: TextView

    private var currentUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.player_view)
        loadingIndicator = findViewById(R.id.loading_indicator)
        errorText = findViewById(R.id.error_text)

        // Hide system UI for immersive playback on TV
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )

        // Initialize player
        meowPlayer = MeowExoPlayer(
            context = this,
            onPlayerError = { error ->
                runOnUiThread {
                    loadingIndicator.visibility = View.GONE
                    errorText.visibility = View.VISIBLE
                    errorText.text = error
                }
            },
            onPlaybackStateChanged = { state ->
                runOnUiThread {
                    when (state) {
                        Player.STATE_BUFFERING -> {
                            loadingIndicator.visibility = View.VISIBLE
                            errorText.visibility = View.GONE
                        }
                        Player.STATE_READY -> {
                            loadingIndicator.visibility = View.GONE
                            errorText.visibility = View.GONE
                        }
                        Player.STATE_ENDED -> {
                            // Don't auto-finish, let user decide
                        }
                    }
                }
            },
            onPositionUpdate = { position, duration ->
                // Position updates for future remote app API
            }
        )

        val exoPlayer = meowPlayer.initialize()
        playerView.player = exoPlayer
        playerView.useController = true

        // Handle incoming intent
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: Intent) {
        var url: String? = null
        var title: String? = null

        when (intent.action) {
            Intent.ACTION_VIEW -> {
                // URL from external app (Stremio, CloudStream, browser, etc.)
                url = intent.data?.toString()
                title = intent.getStringExtra("title")
                    ?: intent.data?.lastPathSegment
            }

            Intent.ACTION_SEND -> {
                // Shared text/URL
                if (intent.type == "text/plain") {
                    url = intent.getStringExtra(Intent.EXTRA_TEXT)
                    title = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                } else if (intent.type?.startsWith("video/") == true) {
                    val clipData = intent.clipData
                    if (clipData != null && clipData.itemCount > 0) {
                        url = clipData.getItemAt(0)?.uri?.toString()
                    }
                }
            }

            "com.meowplay.tv.PLAY" -> {
                // Internal play action from history or paste
                url = intent.getStringExtra("url")
                title = intent.getStringExtra("title")
                val resumePosition = intent.getLongExtra("resume_position", 0L)
                if (!url.isNullOrEmpty()) {
                    meowPlayer.play(url, title, resumePosition)
                    currentUrl = url
                    return
                }
            }
        }

        if (!url.isNullOrEmpty()) {
            // Play with resume support
            scope.launch {
                meowPlayer.play(url, title)
            }
            currentUrl = url
        } else {
            errorText.visibility = View.VISIBLE
            errorText.text = "No valid video URL received."
        }
    }

    override fun onPause() {
        super.onPause()
        meowPlayer.saveCurrentPosition()
    }

    override fun onStop() {
        super.onStop()
        meowPlayer.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        meowPlayer.release()
    }

    @Deprecated("Use OnBackInvokedCallback for API 33+")
    override fun onBackPressed() {
        meowPlayer.saveCurrentPosition()
        meowPlayer.release()
        super.onBackPressed()
    }
}
