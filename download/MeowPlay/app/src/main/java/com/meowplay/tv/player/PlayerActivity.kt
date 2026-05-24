package com.meowplay.tv.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.fragment.app.FragmentActivity
import com.meowplay.tv.R
import com.meowplay.tv.data.HistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * FULL-SCREEN VIDEO PLAYER — This is the core of the app.
 *
 * FIXES from v1:
 * 1. Properly extracts URL from ALL intent types (ClipData, EXTRA_TEXT, data)
 * 2. Surface ready before player init (post {})
 * 3. HTTP cleartext enabled (usesCleartextTraffic in manifest)
 * 4. Cross-protocol redirects allowed
 * 5. Cache DataSource actually integrated
 * 6. D-pad key handling for TV remote
 * 7. Lifecycle: pause in onStop, release in onDestroy (not onPause)
 * 8. Returns result to calling app (Stremio/CloudStream/Kodi)
 */
class PlayerActivity : FragmentActivity() {

    companion object {
        private const val TAG = "MeowPlay"
    }

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var errorText: TextView

    private var currentUrl: String? = null
    private var currentTitle: String? = null
    private var resumePosition: Long = 0
    private var hasPlayedSuccessfully = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set content view FIRST so surface exists
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.player_view)
        loadingIndicator = findViewById(R.id.loading_indicator)
        errorText = findViewById(R.id.error_text)

        // Full immersive mode for TV
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )

        // Handle incoming intent
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    override fun onStart() {
        super.onStart()
        if (player != null) {
            playerView.onResume()
        }
    }

    override fun onStop() {
        super.onStop()
        // PAUSE, don't release — user might switch apps briefly
        playerView.onPause()
        savePosition()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

    // ─── Intent Handling (THE CRITICAL FIX) ────────────────────────────────────

    private fun handleIntent(intent: Intent) {
        Log.d(TAG, "handleIntent: action=${intent.action}, data=${intent.data}, type=${intent.type}")
        Log.d(TAG, "handleIntent: extras=${intent.extras}")
        Log.d(TAG, "handleIntent: clipData=${intent.clipData}")

        var url: String? = null
        var title: String? = null
        var position: Long = 0

        when (intent.action) {
            Intent.ACTION_VIEW -> {
                // METHOD 1: intent.data (most common)
                url = intent.data?.toString()

                // METHOD 2: ClipData (Stremio sometimes puts URL here)
                if (url.isNullOrBlank() && intent.clipData != null) {
                    for (i in 0 until intent.clipData!!.itemCount) {
                        val item = intent.clipData!!.getItemAt(i)
                        val candidate = item.uri?.toString() ?: item.text?.toString()
                        if (!candidate.isNullOrBlank() && isValidVideoUrl(candidate)) {
                            url = candidate
                            break
                        }
                    }
                }

                // METHOD 3: Extras (some apps pass URL as string extra)
                if (url.isNullOrBlank()) {
                    url = intent.getStringExtra(Intent.EXTRA_TEXT)
                }
                if (url.isNullOrBlank()) {
                    url = intent.getStringExtra("url")
                }
                if (url.isNullOrBlank()) {
                    url = intent.getStringExtra("video_url")
                }
                if (url.isNullOrBlank()) {
                    url = intent.getStringExtra("stream_url")
                }

                // Title extraction
                title = intent.getStringExtra("title")
                    ?: intent.getStringExtra("android.intent.extra.TITLE")
                    ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
                    ?: intent.data?.lastPathSegment
                    ?: extractTitleFromUrl(url)

                // Position (for resume)
                position = intent.getLongExtra("position", 0L)
                    .coerceAtLeast(intent.getLongExtra("resume_position", 0L))
                    .coerceAtLeast(intent.getIntExtra("position", 0).toLong())
            }

            Intent.ACTION_SEND -> {
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

            // Internal play action from history or paste
            "com.meowplay.tv.PLAY" -> {
                url = intent.getStringExtra("url")
                title = intent.getStringExtra("title")
                position = intent.getLongExtra("resume_position", 0L)
            }
        }

        if (url.isNullOrBlank()) {
            Log.e(TAG, "No video URL found in intent!")
            showError("No video URL received from ${intent.action}")
            return
        }

        Log.d(TAG, "Playing URL: $url, title: $title, position: $position")
        currentUrl = url
        currentTitle = title
        resumePosition = position

        // Initialize player AFTER layout is ready (surface must exist)
        playerView.post { initializePlayer(url, title, position) }
    }

    private fun isValidVideoUrl(url: String): Boolean {
        return url.startsWith("http") || url.startsWith("rtmp") || url.startsWith("rtsp") ||
               url.startsWith("magnet:") || url.startsWith("content://")
    }

    private fun extractTitleFromUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return try {
            val path = url.substringAfterLast("/")
            path.substringBeforeLast(".").replace("_", " ").replace("-", " ").take(50)
        } catch (e: Exception) { url.take(50) }
    }

    // ─── Player Initialization (WITH ACTUAL CACHE) ──────────────────────────────

    private fun initializePlayer(url: String, title: String?, positionMs: Long) {
        releasePlayer()

        val cacheManager = CacheManager.getInstance(this)

        // Build HTTP data source with cross-protocol redirect support
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("MeowPlay/1.0")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setAllowCrossProtocolRedirects(true)

        // Build cache data source if disk cache is enabled
        val cacheDataSourceFactory = cacheManager.createCacheDataSourceFactory(httpDataSourceFactory)

        // Build media source factory — use cache if available
        val mediaSourceFactory = if (cacheDataSourceFactory != null) {
            DefaultMediaSourceFactory(cacheDataSourceFactory)
        } else {
            DefaultMediaSourceFactory(httpDataSourceFactory)
        }

        // Build load control with user's cache settings
        val loadControl = cacheManager.createLoadControl()

        // Create the player
        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build()

        // Set up player view
        playerView.player = exoPlayer
        playerView.useController = true
        playerView.keepScreenOn = true

        // Build media item with title
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(url))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title ?: "MeowPlay")
                    .build()
            )
            .build()

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()

        // Resume from position
        if (positionMs > 0) {
            exoPlayer.seekTo(positionMs)
        }

        exoPlayer.playWhenReady = true

        // Error listener
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        loadingIndicator.visibility = View.VISIBLE
                        errorText.visibility = View.GONE
                    }
                    Player.STATE_READY -> {
                        loadingIndicator.visibility = View.GONE
                        errorText.visibility = View.GONE
                        hasPlayedSuccessfully = true
                        // Save to history
                        saveToHistory(url, title)
                    }
                    Player.STATE_ENDED -> {
                        // Mark as fully watched — clear resume position
                        clearResumePosition(url)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                loadingIndicator.visibility = View.GONE
                val msg = getUserFriendlyError(error)
                showError(msg)
                Log.e(TAG, "Playback error", error)
            }
        })

        player = exoPlayer
    }

    private fun getUserFriendlyError(error: PlaybackException): String {
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                "Network connection failed. Check your internet."
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                "Server error. Stream may be unavailable or expired."
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                "Stream not found. The URL may be invalid."
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ->
                "Cannot parse this stream format."
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
                "Decoder failed. This codec may not be supported on your TV."
            PlaybackException.ERROR_CODE_DECODING_FAILED ->
                "Decoding failed. Video codec not supported."
            else -> "Playback error: ${error.message}"
        }
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
        loadingIndicator.visibility = View.GONE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    // ─── D-Pad / Remote Key Handling ──────────────────────────────────────────

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val exoPlayer = player ?: return super.onKeyDown(keyCode, event)

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                exoPlayer.seekTo(exoPlayer.currentPosition + 10000)
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                exoPlayer.seekTo(maxOf(0, exoPlayer.currentPosition - 10000))
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                // Could be used for subtitle toggle, chapter skip, etc.
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                savePosition()
                returnResult()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // ─── History / Position Tracking ──────────────────────────────────────────

    private fun saveToHistory(url: String, title: String?) {
        val repo = HistoryRepository.getInstance(this)
        scope.launch { repo.addOrUpdateEntry(url = url, title = title) }
    }

    private fun savePosition() {
        val url = currentUrl ?: return
        val position = player?.currentPosition ?: 0L
        if (position > 0) {
            val repo = HistoryRepository.getInstance(this)
            scope.launch { repo.updatePosition(url, position) }
        }
    }

    private fun clearResumePosition(url: String) {
        val repo = HistoryRepository.getInstance(this)
        scope.launch { repo.updatePosition(url, 0L) }
    }

    private fun returnResult() {
        val position = player?.currentPosition ?: 0L
        val duration = player?.duration?.let { if (it == C.TIME_UNSET) 0L else it } ?: 0L

        val resultIntent = Intent().apply {
            putExtra("extra_position", position)
            putExtra("extra_duration", duration)
        }
        setResult(RESULT_OK, resultIntent)
        releasePlayer()
        finish()
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }
}
