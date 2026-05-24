package com.meowplay.tv.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.meowplay.tv.data.HistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * MeowExoPlayer — Wraps Media3 ExoPlayer with advanced cache integration.
 *
 * Handles:
 * - Creating ExoPlayer with CacheManager-configured LoadControl
 * - Attaching CacheDataSource for disk caching
 * - Tracking playback position for resume feature
 * - Updating history on playback events
 */
class MeowExoPlayer(
    private val context: Context,
    private val onPlayerError: (String) -> Unit = {},
    private val onPlaybackStateChanged: (Int) -> Unit = {},
    private val onPositionUpdate: (Long, Long) -> Unit = { _, _ -> }
) {

    private val cacheManager = CacheManager.getInstance(context)
    private val historyRepository = HistoryRepository.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var player: ExoPlayer? = null
        private set

    private var currentUrl: String? = null
    private var isTrackingPosition = false

    /** Create and configure the ExoPlayer instance */
    fun initialize(): ExoPlayer {
        val loadControl = cacheManager.createLoadControl()

        // Build the media source factory with cache support
        val cacheDataSourceFactory = cacheManager.createCacheDataSourceFactory()

        val mediaSourceFactory = if (cacheDataSourceFactory != null) {
            DefaultMediaSourceFactory(context)
                .setDataSourceFactory(cacheDataSourceFactory)
        } else {
            DefaultMediaSourceFactory(context)
        }

        val exoPlayer = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .setSeekBackIncrementMs(10000)   // 10s seek back (D-pad left)
            .setSeekForwardIncrementMs(10000) // 10s seek forward (D-pad right)
            .build()

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                onPlaybackStateChanged(playbackState)

                if (playbackState == Player.STATE_ENDED) {
                    currentUrl?.let { url ->
                        scope.launch { historyRepository.updatePosition(url, 0L) }
                    }
                    stopPositionTracking()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val message = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                        "Network connection failed. Check your internet."
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                        "Server returned an error. The stream may be unavailable."
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                        "Stream not found. The URL may be invalid or expired."
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ->
                        "Cannot parse this stream format."
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
                        "Decoder initialization failed. Format may not be supported."
                    PlaybackException.ERROR_CODE_DECODING_FAILED ->
                        "Decoding failed. The video codec may not be supported on this device."
                    else -> "Playback error: ${error.message}"
                }
                onPlayerError(message)
            }
        })

        player = exoPlayer
        return exoPlayer
    }

    /** Play a video URL with optional resume position */
    fun play(url: String, title: String? = null, resumePosition: Long = 0) {
        val exoPlayer = player ?: return
        currentUrl = url

        // Build the media item
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(url))
            .build()

        exoPlayer.setMediaItem(mediaItem)

        // Seek to resume position if provided
        if (resumePosition > 0) {
            exoPlayer.seekTo(resumePosition)
        }

        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        // Add to history (suspend call in coroutine)
        scope.launch { historyRepository.addOrUpdateEntry(url = url, title = title) }

        // Start position tracking
        startPositionTracking()
    }

    /** Resume playback from saved position */
    suspend fun playWithResume(url: String, title: String? = null) {
        val entry = historyRepository.getByUrl(url)
        val resumePosition = entry?.lastPosition ?: 0L
        play(url, title, resumePosition)
    }

    /** Pause playback */
    fun pause() {
        player?.pause()
        saveCurrentPosition()
    }

    /** Resume playback */
    fun resume() {
        player?.play()
    }

    /** Seek to position in milliseconds */
    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    /** Get current position in milliseconds */
    fun getCurrentPosition(): Long {
        return player?.currentPosition ?: 0L
    }

    /** Get duration in milliseconds */
    fun getDuration(): Long {
        return player?.duration ?: 0L
    }

    /** Get buffered position in milliseconds */
    fun getBufferedPosition(): Long {
        return player?.bufferedPosition ?: 0L
    }

    /** Is the player currently playing */
    fun isPlaying(): Boolean {
        return player?.isPlaying ?: false
    }

    /** Save current position to history for resume feature */
    fun saveCurrentPosition() {
        val url = currentUrl ?: return
        val position = player?.currentPosition ?: 0L
        if (position > 0) {
            scope.launch { historyRepository.updatePosition(url, position) }
        }
    }

    /** Update duration in history */
    fun updateDuration() {
        val url = currentUrl ?: return
        val duration = player?.duration ?: 0L
        if (duration > 0 && duration != C.TIME_UNSET) {
            scope.launch { historyRepository.updateDuration(url, duration) }
        }
    }

    /** Release the player and save position */
    fun release() {
        saveCurrentPosition()
        updateDuration()
        stopPositionTracking()
        player?.release()
        player = null
    }

    // ─── Position Tracking (for periodic saves) ──────────────────────────────

    private val positionTrackingRunnable = object : Runnable {
        override fun run() {
            val exoPlayer = player ?: return
            val position = exoPlayer.currentPosition
            val duration = exoPlayer.duration.let { if (it == C.TIME_UNSET) 0L else it }
            onPositionUpdate(position, duration)
            saveCurrentPosition()

            // Schedule next update
            if (isTrackingPosition) {
                Handler(Looper.getMainLooper())
                    .postDelayed(this, 1000) // Save position every second
            }
        }
    }

    private fun startPositionTracking() {
        isTrackingPosition = true
        Handler(Looper.getMainLooper())
            .postDelayed(positionTrackingRunnable, 1000)
    }

    private fun stopPositionTracking() {
        isTrackingPosition = false
    }
}
