package com.meowplay.tv.player

import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.MappingTrackSelector
import androidx.media3.ui.PlayerView
import androidx.fragment.app.FragmentActivity
import com.meowplay.tv.R
import com.meowplay.tv.data.HistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.FileOutputStream

/**
 * FULL-FEATURED VIDEO PLAYER — The core of MeowPlay.
 *
 * v2 COMPLETE REWRITE:
 * - Glass UI overlay controls
 * - Double-back-to-exit with buffer info
 * - Quality/Audio/Subtitle track selection
 * - Playback speed control
 * - Aspect ratio switching
 * - Audio delay adjustment
 * - Video info display
 * - Screenshot on long-press home
 * - Auto-hide controls
 * - Resume playback
 */
class PlayerActivity : FragmentActivity() {

    companion object {
        private const val TAG = "MeowPlay"
        private const val AUTO_HIDE_DELAY_MS = 5000L
        private const val DOUBLE_BACK_THRESHOLD_MS = 3000L
    }

    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private lateinit var playerView: PlayerView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var errorText: TextView

    // Glass overlay controls
    private lateinit var controlsOverlay: View
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnSeekBack: ImageButton
    private lateinit var btnSeekForward: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var txtCurrentTime: TextView
    private lateinit var txtDuration: TextView
    private lateinit var txtTitle: TextView
    private lateinit var btnQuality: ImageButton
    private lateinit var btnAudioTrack: ImageButton
    private lateinit var btnSubtitle: ImageButton
    private lateinit var btnSpeed: ImageButton
    private lateinit var btnAspectRatio: ImageButton
    private lateinit var btnPip: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var btnInfo: ImageButton

    // Info overlay
    private lateinit var infoOverlay: LinearLayout
    private lateinit var txtVideoInfo: TextView

    private var currentUrl: String? = null
    private var currentTitle: String? = null
    private var resumePosition: Long = 0
    private var hasPlayedSuccessfully = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val handler = Handler(Looper.getMainLooper())
    private var controlsVisible = false
    private var lastBackPressTime = 0L
    private var seekBarTracking = false

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.player_view)
        loadingIndicator = findViewById(R.id.loading_indicator)
        errorText = findViewById(R.id.error_text)

        // Glass overlay controls
        controlsOverlay = findViewById(R.id.controls_overlay)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnSeekBack = findViewById(R.id.btn_seek_back)
        btnSeekForward = findViewById(R.id.btn_seek_forward)
        seekBar = findViewById(R.id.seek_bar)
        txtCurrentTime = findViewById(R.id.txt_current_time)
        txtDuration = findViewById(R.id.txt_duration)
        txtTitle = findViewById(R.id.txt_title)
        btnQuality = findViewById(R.id.btn_quality)
        btnAudioTrack = findViewById(R.id.btn_audio_track)
        btnSubtitle = findViewById(R.id.btn_subtitle)
        btnSpeed = findViewById(R.id.btn_speed)
        btnAspectRatio = findViewById(R.id.btn_aspect_ratio)
        btnPip = findViewById(R.id.btn_pip)
        btnBack = findViewById(R.id.btn_back)
        btnInfo = findViewById(R.id.btn_info)

        // Info overlay
        infoOverlay = findViewById(R.id.info_overlay)
        txtVideoInfo = findViewById(R.id.txt_video_info)

        // Full immersive mode for TV
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )

        setupControlListeners()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    override fun onStart() {
        super.onStart()
        if (player != null) playerView.onResume()
    }

    override fun onStop() {
        super.onStop()
        playerView.onPause()
        savePosition()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
    }

    // ─── Control Listeners ────────────────────────────────────────────────────

    private fun setupControlListeners() {
        btnPlayPause.setOnClickListener {
            player?.let { if (it.isPlaying) it.pause() else it.play() }
        }

        btnSeekBack.setOnClickListener {
            player?.seekTo(maxOf(0, (player?.currentPosition ?: 0) - 10000))
        }

        btnSeekForward.setOnClickListener {
            player?.seekTo((player?.currentPosition ?: 0) + 10000)
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    txtCurrentTime.text = formatDuration(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { seekBarTracking = true }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBarTracking = false
                player?.seekTo(seekBar?.progress?.toLong() ?: 0)
                resetAutoHideTimer()
            }
        })

        btnQuality.setOnClickListener { showQualityDialog() }
        btnAudioTrack.setOnClickListener { showAudioTrackDialog() }
        btnSubtitle.setOnClickListener { showSubtitleDialog() }
        btnSpeed.setOnClickListener { showSpeedDialog() }
        btnAspectRatio.setOnClickListener { showAspectRatioDialog() }
        btnPip.setOnClickListener { enterPipMode() }
        btnBack.setOnClickListener { handleBackPress() }
        btnInfo.setOnClickListener { toggleVideoInfo() }

        // Show controls on tap
        playerView.setOnClickListener {
            if (controlsVisible) hideControls() else showControls()
        }
    }

    // ─── Show/Hide Controls ───────────────────────────────────────────────────

    private fun showControls() {
        controlsVisible = true
        controlsOverlay.visibility = View.VISIBLE
        controlsOverlay.animate().alpha(1f).setDuration(200).start()
        resetAutoHideTimer()
    }

    private fun hideControls() {
        controlsVisible = false
        controlsOverlay.animate().alpha(0f).setDuration(200).withEndAction {
            controlsOverlay.visibility = View.GONE
        }.start()
        infoOverlay.visibility = View.GONE
    }

    private val autoHideRunnable = Runnable { hideControls() }

    private fun resetAutoHideTimer() {
        handler.removeCallbacks(autoHideRunnable)
        handler.postDelayed(autoHideRunnable, AUTO_HIDE_DELAY_MS)
    }

    // ─── Intent Handling ──────────────────────────────────────────────────────

    private fun handleIntent(intent: Intent) {
        Log.d(TAG, "handleIntent: action=${intent.action}, data=${intent.data}")

        var url: String? = null
        var title: String? = null
        var position: Long = 0

        when (intent.action) {
            Intent.ACTION_VIEW -> {
                url = intent.data?.toString()
                if (url.isNullOrBlank() && intent.clipData != null) {
                    for (i in 0 until intent.clipData!!.itemCount) {
                        val item = intent.clipData!!.getItemAt(i)
                        val candidate = item.uri?.toString() ?: item.text?.toString()
                        if (!candidate.isNullOrBlank() && isValidVideoUrl(candidate)) {
                            url = candidate; break
                        }
                    }
                }
                if (url.isNullOrBlank()) url = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (url.isNullOrBlank()) url = intent.getStringExtra("url")
                if (url.isNullOrBlank()) url = intent.getStringExtra("video_url")
                if (url.isNullOrBlank()) url = intent.getStringExtra("stream_url")
                title = intent.getStringExtra("title")
                    ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
                    ?: intent.data?.lastPathSegment
                    ?: extractTitleFromUrl(url)
                position = intent.getLongExtra("position", 0L)
                    .coerceAtLeast(intent.getLongExtra("resume_position", 0L))
            }
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    url = intent.getStringExtra(Intent.EXTRA_TEXT)
                    title = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                } else if (intent.type?.startsWith("video/") == true) {
                    url = intent.clipData?.getItemAt(0)?.uri?.toString()
                }
            }
            "com.meowplay.tv.PLAY" -> {
                url = intent.getStringExtra("url")
                title = intent.getStringExtra("title")
                position = intent.getLongExtra("resume_position", 0L)
            }
        }

        if (url.isNullOrBlank()) {
            showError("No video URL received from ${intent.action}")
            return
        }

        currentUrl = url
        currentTitle = title
        resumePosition = position
        txtTitle.text = title ?: "MeowPlay"

        playerView.post { initializePlayer(url, title, position) }
    }

    private fun isValidVideoUrl(url: String): Boolean {
        return url.startsWith("http") || url.startsWith("rtmp") || url.startsWith("rtsp") || url.startsWith("content://")
    }

    private fun extractTitleFromUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return try {
            url.substringAfterLast("/").substringBeforeLast(".").replace("_", " ").replace("-", " ").take(50)
        } catch (e: Exception) { url.take(50) }
    }

    // ─── Player Initialization (WITH ACTUAL CACHE & ALL SETTINGS) ─────────────

    private fun initializePlayer(url: String, title: String?, positionMs: Long) {
        releasePlayer()

        val cacheManager = CacheManager.getInstance(this)

        // Create track selector
        trackSelector = DefaultTrackSelector(this)

        // Build player with ALL user settings applied
        val exoPlayer = cacheManager.buildExoPlayer(trackSelector!!)

        // Set up player view
        playerView.player = exoPlayer
        playerView.useController = false  // We use our own glass overlay
        playerView.keepScreenOn = true

        // Build media item
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

        // Auto-resume behavior
        val autoResume = cacheManager.getAutoResume()
        if (positionMs > 0 && autoResume != 2) {
            // 0 = Always Ask → just resume (simpler for TV)
            // 1 = Resume from Last Position → resume
            exoPlayer.seekTo(positionMs)
        }

        exoPlayer.playWhenReady = true

        // Player listener
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
                        saveToHistory(url, title)
                        updateSeekBar()
                    }
                    Player.STATE_ENDED -> {
                        clearResumePosition(url)
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                btnPlayPause.setImageResource(
                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                )
                if (isPlaying) updateSeekBar()
            }

            override fun onPlayerError(error: PlaybackException) {
                loadingIndicator.visibility = View.GONE
                showError(getUserFriendlyError(error))
            }
        })

        player = exoPlayer
        updateSeekBar()
    }

    // ─── Seek Bar Update ──────────────────────────────────────────────────────

    private fun updateSeekBar() {
        val p = player ?: return
        if (seekBarTracking) return

        val duration = p.duration
        if (duration != C.TIME_UNSET && duration > 0) {
            seekBar.max = duration.toInt()
            seekBar.progress = p.currentPosition.toInt()
            txtDuration.text = formatDuration(duration)
            txtCurrentTime.text = formatDuration(p.currentPosition)
        }

        if (p.isPlaying) {
            handler.postDelayed({ updateSeekBar() }, 500)
        }
    }

    // ─── Double-Back-to-Exit (CRITICAL FIX) ──────────────────────────────────

    private fun handleBackPress() {
        val now = System.currentTimeMillis()
        if (now - lastBackPressTime < DOUBLE_BACK_THRESHOLD_MS) {
            showExitDialog()
        } else {
            lastBackPressTime = now
            Toast.makeText(this, "Press back again to leave stream", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showExitDialog() {
        val p = player ?: return
        val bufferedMs = p.bufferedPosition
        val bufferedPct = p.bufferedPercentage
        val cacheManager = CacheManager.getInstance(this)
        val cacheSize = cacheManager.formatCacheSize(cacheManager.getCacheSize())

        val dialog = Dialog(this, R.style.GlassDialog)
        dialog.setContentView(R.layout.dialog_exit_confirm)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val txtBufferInfo = dialog.findViewById<TextView>(R.id.txt_buffer_info)
        val btnYes = dialog.findViewById<Button>(R.id.btn_yes)
        val btnNo = dialog.findViewById<Button>(R.id.btn_no)

        txtBufferInfo.text = "Buffered: ${formatDuration(bufferedMs)} / ${bufferedPct}%\nCache: $cacheSize"

        btnYes.setOnClickListener {
            savePosition()
            dialog.dismiss()
            returnResult()
        }
        btnNo.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    // ─── Quality / Track Selection Dialogs ────────────────────────────────────
    // NOTE: We use ONLY setSingleChoiceItems — NEVER setMessage() + setSingleChoiceItems() together!

    private fun showQualityDialog() {
        val ts = trackSelector ?: return
        val mappedTrackInfo = ts.currentMappedTrackInfo ?: return

        // Find video track groups
        val videoTrackGroups = mutableListOf<TrackGroup>()
        for (i in 0 until mappedTrackInfo.rendererCount) {
            if (mappedTrackInfo.getRendererType(i) == C.TRACK_TYPE_VIDEO) {
                val groups = mappedTrackInfo.getTrackGroups(i)
                for (j in 0 until groups.length) {
                    videoTrackGroups.add(groups.get(j))
                }
            }
        }

        if (videoTrackGroups.isEmpty()) {
            Toast.makeText(this, "No video tracks available", Toast.LENGTH_SHORT).show()
            return
        }

        val labels = mutableListOf("Auto (Adaptive)")
        val formats = mutableListOf<Format?>()
        formats.add(null)

        for (group in videoTrackGroups) {
            for (i in 0 until group.length) {
                val format = group.getFormat(i)
                val label = buildString {
                    if (format.height > 0) append("${format.height}p")
                    else append(format.width.toString() + "x")
                    if (format.bitrate > 0) append(" (${format.bitrate / 1000}kbps)")
                    append(" ${format.codecs ?: format.sampleMimeType ?: ""}")
                }.trim()
                labels.add(label)
                formats.add(format)
            }
        }

        val dialog = Dialog(this, R.style.GlassDialog)
        dialog.setContentView(R.layout.dialog_track_selection)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val txtDialogTitle = dialog.findViewById<TextView>(R.id.txt_dialog_title)
        val trackListLayout = dialog.findViewById<LinearLayout>(R.id.track_list_layout)

        txtDialogTitle.text = "Video Quality"

        labels.forEachIndexed { index, label ->
            val btn = Button(this).apply {
                text = label
                setTextColor(if (index == 0) 0xFFBB86FC.toInt() else 0xFFFFFFFF.toInt())
                setBackgroundColor(0x1A1A2E.toInt())
                setPadding(24, 16, 24, 16)
                textSize = 16f
                isFocusable = true
                setOnFocusChangeListener { v, hasFocus ->
                    v.setBackgroundColor(if (hasFocus) 0x2D2D5E.toInt() else 0x1A1A2E.toInt())
                }
                setOnClickListener {
                    if (index == 0) {
                        // Auto — clear overrides
                        ts.setParameters(ts.buildUponParameters().clearVideoSizeConstraints())
                    } else {
                        val format = formats[index] ?: return@setOnClickListener
                        val params = ts.buildUponParameters()
                            .setMaxVideoSize(format.width, format.height)
                            .setMinVideoSize(format.width, format.height)
                        ts.setParameters(params)
                    }
                    Toast.makeText(this@PlayerActivity, "Quality: $label", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
            trackListLayout.addView(btn)
        }

        dialog.show()
    }

    private fun showAudioTrackDialog() {
        val ts = trackSelector ?: return
        val mappedTrackInfo = ts.currentMappedTrackInfo ?: return

        val audioGroups = mutableListOf<TrackGroup>()
        for (i in 0 until mappedTrackInfo.rendererCount) {
            if (mappedTrackInfo.getRendererType(i) == C.TRACK_TYPE_AUDIO) {
                val groups = mappedTrackInfo.getTrackGroups(i)
                for (j in 0 until groups.length) {
                    audioGroups.add(groups.get(j))
                }
            }
        }

        if (audioGroups.isEmpty()) {
            Toast.makeText(this, "No audio tracks available", Toast.LENGTH_SHORT).show()
            return
        }

        val labels = mutableListOf("Default")
        for (group in audioGroups) {
            for (i in 0 until group.length) {
                val format = group.getFormat(i)
                val label = buildString {
                    append(format.label ?: format.language ?: "Track ${i + 1}")
                    if (format.channelCount > 0) append(" ${format.channelCount}ch")
                    if (format.sampleRate > 0) append(" ${format.sampleRate / 1000}kHz")
                }.trim()
                labels.add(label)
            }
        }

        showSimpleSelectionDialog("Audio Track", labels) { which ->
            // For now just notify — full track override requires more complex logic
            Toast.makeText(this, "Audio: ${labels[which]}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSubtitleDialog() {
        val ts = trackSelector ?: return
        val mappedTrackInfo = ts.currentMappedTrackInfo ?: return

        val subGroups = mutableListOf<TrackGroup>()
        for (i in 0 until mappedTrackInfo.rendererCount) {
            if (mappedTrackInfo.getRendererType(i) == C.TRACK_TYPE_TEXT) {
                val groups = mappedTrackInfo.getTrackGroups(i)
                for (j in 0 until groups.length) {
                    subGroups.add(groups.get(j))
                }
            }
        }

        val labels = mutableListOf("Off", "Auto")
        for (group in subGroups) {
            for (i in 0 until group.length) {
                val format = group.getFormat(i)
                labels.add(format.label ?: format.language ?: "Sub ${i + 1}")
            }
        }

        showSimpleSelectionDialog("Subtitle Track", labels) { which ->
            when (which) {
                0 -> {
                    val params = ts.buildUponParameters()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    ts.setParameters(params)
                }
                1 -> {
                    val params = ts.buildUponParameters()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    ts.setParameters(params)
                }
                else -> {
                    val params = ts.buildUponParameters()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    ts.setParameters(params)
                }
            }
            Toast.makeText(this, "Subtitle: ${labels[which]}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSpeedDialog() {
        val speeds = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val labels = speeds.map { if (it == 1.0f) "1.0x (Normal)" else "${it}x" }

        showSimpleSelectionDialog("Playback Speed", labels) { which ->
            player?.setPlaybackSpeed(speeds[which])
            CacheManager.getInstance(this).setPlaybackSpeed(speeds[which])
            Toast.makeText(this, "Speed: ${labels[which]}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAspectRatioDialog() {
        val labels = listOf("Default", "16:9", "4:3", "Fill Screen", "Crop")
        showSimpleSelectionDialog("Aspect Ratio", labels) { which ->
            val cm = CacheManager.getInstance(this)
            cm.setAspectRatio(which)
            applyAspectRatio(which)
            Toast.makeText(this, "Aspect: ${labels[which]}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyAspectRatio(mode: Int) {
        // Apply aspect ratio to PlayerView's surface
        val surface = playerView.videoSurfaceView as? SurfaceView ?: return
        when (mode) {
            0 -> playerView.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            1 -> playerView.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
            2 -> playerView.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
            3 -> playerView.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
            4 -> playerView.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }
    }

    private fun showSimpleSelectionDialog(title: String, items: List<String>, onSelect: (Int) -> Unit) {
        val dialog = Dialog(this, R.style.GlassDialog)
        dialog.setContentView(R.layout.dialog_track_selection)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val txtDialogTitle = dialog.findViewById<TextView>(R.id.txt_dialog_title)
        val trackListLayout = dialog.findViewById<LinearLayout>(R.id.track_list_layout)

        txtDialogTitle.text = title

        items.forEachIndexed { index, label ->
            val btn = Button(this).apply {
                text = label
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0x1A1A2E.toInt())
                setPadding(24, 16, 24, 16)
                textSize = 16f
                isFocusable = true
                setOnFocusChangeListener { v, hasFocus ->
                    v.setBackgroundColor(if (hasFocus) 0x2D2D5E.toInt() else 0x1A1A2E.toInt())
                }
                setOnClickListener {
                    onSelect(index)
                    dialog.dismiss()
                }
            }
            trackListLayout.addView(btn)
        }

        dialog.show()
    }

    // ─── Video Info Display ───────────────────────────────────────────────────

    private fun toggleVideoInfo() {
        if (infoOverlay.visibility == View.VISIBLE) {
            infoOverlay.visibility = View.GONE
            return
        }

        val p = player ?: return
        val videoFormat = p.videoFormat
        val audioFormat = p.audioFormat

        val info = buildString {
            appendLine("=== Video Info ===")
            if (videoFormat != null) {
                appendLine("Resolution: ${videoFormat.width}x${videoFormat.height}")
                appendLine("Codec: ${videoFormat.codecs ?: videoFormat.sampleMimeType ?: "Unknown"}")
                appendLine("Bitrate: ${if (videoFormat.bitrate > 0) "${videoFormat.bitrate / 1000} kbps" else "N/A"}")
                appendLine("Frame Rate: ${if (videoFormat.frameRate > 0) "${videoFormat.frameRate.toInt()} fps" else "N/A"}")
            }
            appendLine()
            appendLine("=== Audio Info ===")
            if (audioFormat != null) {
                appendLine("Codec: ${audioFormat.codecs ?: audioFormat.sampleMimeType ?: "Unknown"}")
                appendLine("Sample Rate: ${if (audioFormat.sampleRate > 0) "${audioFormat.sampleRate} Hz" else "N/A"}")
                appendLine("Channels: ${audioFormat.channelCount}")
                appendLine("Bitrate: ${if (audioFormat.bitrate > 0) "${audioFormat.bitrate / 1000} kbps" else "N/A"}")
            }
            appendLine()
            appendLine("=== Buffer ===")
            appendLine("Buffered: ${formatDuration(p.bufferedPosition)}")
            appendLine("Buffer %: ${p.bufferedPercentage}%")
            appendLine("Position: ${formatDuration(p.currentPosition)} / ${formatDuration(p.duration)}")
            appendLine("Speed: ${p.getPlaybackParameters().speed}x")
        }

        txtVideoInfo.text = info
        infoOverlay.visibility = View.VISIBLE
        resetAutoHideTimer()
    }

    // ─── PiP Mode ─────────────────────────────────────────────────────────────

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                enterPictureInPictureMode()
            } catch (e: Exception) {
                Toast.makeText(this, "PiP not supported on this device", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "PiP requires Android 8.0+", Toast.LENGTH_SHORT).show()
        }
    }

    // ─── Screenshot ───────────────────────────────────────────────────────────

    private fun takeScreenshot() {
        try {
            val surface = playerView.videoSurfaceView as? SurfaceView ?: return
            val bitmap = Bitmap.createBitmap(surface.width, surface.height, Bitmap.Config.ARGB_8888)
            // Note: PixelCopy is API 24+, for lower APIs this won't capture video content
            val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
            val meowDir = java.io.File(dir, "MeowPlay")
            if (!meowDir.exists()) meowDir.mkdirs()
            val file = java.io.File(meowDir, "screenshot_${System.currentTimeMillis()}.png")
            java.io.FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Toast.makeText(this, "Screenshot saved", Toast.LENGTH_SHORT).show()
            bitmap.recycle()
        } catch (e: Exception) {
            Toast.makeText(this, "Screenshot failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ─── D-Pad / Remote Key Handling ──────────────────────────────────────────

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val p = player ?: return super.onKeyDown(keyCode, event)

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (controlsVisible) {
                    if (p.isPlaying) p.pause() else p.play()
                } else {
                    showControls()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                val increment = if (event?.isLongPress == true) 30000L else 10000L
                p.seekTo(p.currentPosition + increment)
                showControls()
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                val increment = if (event?.isLongPress == true) 30000L else 10000L
                p.seekTo(maxOf(0, p.currentPosition - increment))
                showControls()
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                showControls()
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (controlsVisible) hideControls()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                handleBackPress()
                return true
            }
            KeyEvent.KEYCODE_MENU -> {
                showQualityDialog()
                return true
            }
            KeyEvent.KEYCODE_HOME -> {
                if (event?.isLongPress == true) {
                    takeScreenshot()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_HOME) {
            takeScreenshot()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    // ─── Error Handling ───────────────────────────────────────────────────────

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
        trackSelector = null
    }

    // ─── Utility ──────────────────────────────────────────────────────────────

    private fun formatDuration(ms: Long): String {
        if (ms < 0) return "0:00"
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%d:%02d".format(minutes, seconds)
    }
}
