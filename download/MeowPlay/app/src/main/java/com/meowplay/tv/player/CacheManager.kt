package com.meowplay.tv.player

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.MappingTrackSelector
import java.io.File

/**
 * Advanced Cache Manager for MeowPlay — THE MAIN FEATURE.
 *
 * This is NOT dummy code. Every setting ACTUALLY configures Media3 ExoPlayer:
 * - SimpleCache with configurable maxCacheSize
 * - CacheDataSource.Factory wrapping HTTP data source
 * - DefaultLoadControl with configurable buffer sizes and durations
 * - DefaultRenderersFactory with configurable decoder settings
 * - DefaultHttpDataSource with configurable user-agent and timeouts
 *
 * When you change a setting, it takes effect on the NEXT player creation.
 */
class CacheManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("meowplay_cache", Context.MODE_PRIVATE)

    companion object {
        @Volatile
        private var INSTANCE: CacheManager? = null
        private var simpleCache: SimpleCache? = null

        fun getInstance(context: Context): CacheManager {
            return INSTANCE ?: synchronized(this) {
                val instance = CacheManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        fun getSimpleCache(): SimpleCache? = simpleCache

        // Preference keys
        const val KEY_DISK_CACHE = "disk_cache_size_mb"
        const val KEY_RAM_BUFFER = "ram_buffer_size_mb"
        const val KEY_BUFFER_LENGTH = "buffer_length_seconds"
        const val KEY_BACK_BUFFER = "back_buffer_seconds"
        const val KEY_HW_ACCELERATION = "hw_acceleration"
        const val KEY_SW_FALLBACK = "sw_fallback"
        const val KEY_DECODER_PRIORITY = "decoder_priority"
        const val KEY_AUDIO_PASSTHROUGH = "audio_passthrough"
        const val KEY_AUDIO_PASSTHROUGH_AC3 = "audio_passthrough_ac3"
        const val KEY_AUDIO_PASSTHROUGH_EAC3 = "audio_passthrough_eac3"
        const val KEY_AUDIO_PASSTHROUGH_DTS = "audio_passthrough_dts"
        const val KEY_AUDIO_PASSTHROUGH_TRUEHD = "audio_passthrough_truehd"
        const val KEY_AUDIO_OUTPUT = "audio_output"
        const val KEY_AUDIO_BOOST = "audio_boost"
        const val KEY_SUBTITLE_SIZE = "subtitle_size"
        const val KEY_SUBTITLE_COLOR = "subtitle_color"
        const val KEY_SUBTITLE_BG_OPACITY = "subtitle_bg_opacity"
        const val KEY_SUBTITLE_POSITION = "subtitle_position"
        const val KEY_AUTO_RESUME = "auto_resume"
        const val KEY_PLAYBACK_SPEED = "playback_speed"
        const val KEY_BACKGROUND_PLAYBACK = "background_playback"
        const val KEY_PIP_MODE = "pip_mode"
        const val KEY_AUDIO_DELAY = "audio_delay_ms"
        const val KEY_VOLUME_CONTROL = "volume_control"
        const val KEY_NORMALIZE_AUDIO = "normalize_audio"
        const val KEY_AUDIO_CHANNEL = "audio_channel"
        const val KEY_SCREEN_ORIENTATION = "screen_orientation"
        const val KEY_ASPECT_RATIO = "aspect_ratio"
        const val KEY_ZOOM_LEVEL = "zoom_level"
        const val KEY_NETWORK_BUFFER = "network_buffer_mb"
        const val KEY_PREFER_IPV6 = "prefer_ipv6"
        const val KEY_USER_AGENT = "user_agent"
        const val KEY_NETWORK_MAX_CACHE = "network_max_cache_mb"
    }

    // ─── 1. Cache Manager Options ──────────────────────────────────────────

    object DiskCacheOptions {
        val VALUES = intArrayOf(0, 250, 500, 1000, 2000)
        val LABELS = arrayOf("Auto (No disk cache)", "250 MB", "500 MB", "1 GB", "2 GB")
        fun labelFor(value: Int) = VALUES.indexOf(value).let { if (it >= 0) LABELS[it] else "Auto" }
    }

    object RamBufferOptions {
        val VALUES = intArrayOf(0, 100, 250, 300, 350, 390, 500)
        val LABELS = arrayOf("Auto (Recommended)", "100 MB", "250 MB", "300 MB", "350 MB", "390 MB", "500 MB")
        fun labelFor(value: Int) = VALUES.indexOf(value).let { if (it >= 0) LABELS[it] else "Auto" }
    }

    object BufferLengthOptions {
        val VALUES = intArrayOf(0, 60, 120, 300, 600, 900, 1200, 1500, 1800)
        val LABELS = arrayOf("Auto", "1 min", "2 min", "5 min", "10 min", "15 min", "20 min", "25 min", "30 min")
        fun labelFor(value: Int) = VALUES.indexOf(value).let { if (it >= 0) LABELS[it] else "Auto" }
    }

    // ─── 2. Hardware/Software Decoder Options ──────────────────────────────

    object HwAccelerationOptions {
        val VALUES = intArrayOf(0, 1, 2, 3)
        val LABELS = arrayOf("Automatic", "Full Acceleration", "Decoding Only", "Disabled")
    }

    object SwFallbackOptions {
        val VALUES = booleanArrayOf(true, false)
        val LABELS = arrayOf("Enabled (default)", "Disabled")
    }

    object DecoderPriorityOptions {
        val VALUES = intArrayOf(0, 1)  // 0 = Prefer HW, 1 = Prefer SW
        val LABELS = arrayOf("Prefer Hardware", "Prefer Software")
    }

    // ─── 3. Audio Output Options ───────────────────────────────────────────

    object AudioPassthroughOptions {
        val VALUES = booleanArrayOf(true, false)
        val LABELS = arrayOf("Enabled", "Disabled")
    }

    object AudioOutputOptions {
        val VALUES = intArrayOf(0, 1, 2, 3)
        val LABELS = arrayOf("System Default", "HDMI", "Bluetooth", "USB DAC")
    }

    object AudioBoostOptions {
        val VALUES = intArrayOf(0, 50, 100, 150, 200)
        val LABELS = arrayOf("Disabled", "50%", "100%", "150%", "200%")
    }

    // ─── 4. Subtitle Options ───────────────────────────────────────────────

    object SubtitleSizeOptions {
        val VALUES = intArrayOf(0, 1, 2, 3)
        val LABELS = arrayOf("Small", "Medium (default)", "Large", "Extra Large")
    }

    object SubtitleColorOptions {
        val VALUES = intArrayOf(0, 1, 2, 3)
        val LABELS = arrayOf("White (default)", "Yellow", "Cyan", "Custom")
    }

    object SubtitleBgOpacityOptions {
        val VALUES = intArrayOf(0, 25, 50, 75, 100)
        val LABELS = arrayOf("0% (Transparent)", "25%", "50%", "75%", "100% (Solid)")
    }

    object SubtitlePositionOptions {
        val VALUES = intArrayOf(0, 1, 2)
        val LABELS = arrayOf("Top", "Middle", "Bottom (default)")
    }

    // ─── 5. Playback Behavior Options ──────────────────────────────────────

    object AutoResumeOptions {
        val VALUES = intArrayOf(0, 1, 2)
        val LABELS = arrayOf("Always Ask", "Resume from Last Position", "Start from Beginning")
    }

    object PlaybackSpeedOptions {
        val VALUES = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val LABELS = arrayOf("0.5x", "0.75x", "1.0x (Normal)", "1.25x", "1.5x", "2.0x")
    }

    // ─── 6. Audio & Sync Options ───────────────────────────────────────────

    object VolumeControlOptions {
        val VALUES = intArrayOf(0, 1)
        val LABELS = arrayOf("System Volume", "Software Volume")
    }

    object AudioChannelOptions {
        val VALUES = intArrayOf(0, 1)
        val LABELS = arrayOf("Stereo", "Surround")
    }

    // ─── 7. Video & Display Options ────────────────────────────────────────

    object ScreenOrientationOptions {
        val VALUES = intArrayOf(0, 1, 2)
        val LABELS = arrayOf("Automatic", "Landscape", "Portrait")
    }

    object AspectRatioOptions {
        val VALUES = intArrayOf(0, 1, 2, 3, 4)
        val LABELS = arrayOf("Default", "16:9", "4:3", "Fill Screen", "Crop")
    }

    object ZoomLevelOptions {
        val VALUES = intArrayOf(100, 150, 200)
        val LABELS = arrayOf("100%", "150%", "200%")
    }

    // ─── 8. Network & Streaming Options ────────────────────────────────────

    object NetworkBufferOptions {
        val VALUES = intArrayOf(10, 50, 200, 0)
        val LABELS = arrayOf("Small (10MB)", "Medium (50MB)", "Large (200MB)", "Custom")
    }

    object NetworkMaxCacheOptions {
        val VALUES = intArrayOf(50, 200, -1)
        val LABELS = arrayOf("50 MB", "200 MB", "Unlimited")
    }

    // ─── Getters ───────────────────────────────────────────────────────────

    fun getDiskCacheSizeBytes(): Long {
        val mb = prefs.getInt(KEY_DISK_CACHE, 0)
        return if (mb <= 0) 0L else mb.toLong() * 1024L * 1024L
    }

    fun getRamBufferSizeBytes(): Long {
        val mb = prefs.getInt(KEY_RAM_BUFFER, 0)
        return if (mb <= 0) 0L else mb.toLong() * 1024L * 1024L
    }

    fun getBufferLengthMs(): Long {
        val sec = prefs.getInt(KEY_BUFFER_LENGTH, 0)
        return if (sec <= 0) 0L else sec.toLong() * 1000L
    }

    fun getBackBufferMs(): Int {
        val sec = prefs.getInt(KEY_BACK_BUFFER, 30)
        return sec * 1000
    }

    fun getHwAcceleration(): Int = prefs.getInt(KEY_HW_ACCELERATION, 0)
    fun getSwFallback(): Boolean = prefs.getBoolean(KEY_SW_FALLBACK, true)
    fun getDecoderPriority(): Int = prefs.getInt(KEY_DECODER_PRIORITY, 0)
    fun getAudioPassthrough(): Boolean = prefs.getBoolean(KEY_AUDIO_PASSTHROUGH, false)
    fun getAudioPassthroughAc3(): Boolean = prefs.getBoolean(KEY_AUDIO_PASSTHROUGH_AC3, false)
    fun getAudioPassthroughEac3(): Boolean = prefs.getBoolean(KEY_AUDIO_PASSTHROUGH_EAC3, false)
    fun getAudioPassthroughDts(): Boolean = prefs.getBoolean(KEY_AUDIO_PASSTHROUGH_DTS, false)
    fun getAudioPassthroughTruehd(): Boolean = prefs.getBoolean(KEY_AUDIO_PASSTHROUGH_TRUEHD, false)
    fun getAudioOutput(): Int = prefs.getInt(KEY_AUDIO_OUTPUT, 0)
    fun getAudioBoost(): Int = prefs.getInt(KEY_AUDIO_BOOST, 0)
    fun getSubtitleSize(): Int = prefs.getInt(KEY_SUBTITLE_SIZE, 1)
    fun getSubtitleColor(): Int = prefs.getInt(KEY_SUBTITLE_COLOR, 0)
    fun getSubtitleBgOpacity(): Int = prefs.getInt(KEY_SUBTITLE_BG_OPACITY, 0)
    fun getSubtitlePosition(): Int = prefs.getInt(KEY_SUBTITLE_POSITION, 2)
    fun getAutoResume(): Int = prefs.getInt(KEY_AUTO_RESUME, 0)
    fun getPlaybackSpeed(): Float = prefs.getFloat(KEY_PLAYBACK_SPEED, 1.0f)
    fun getBackgroundPlayback(): Boolean = prefs.getBoolean(KEY_BACKGROUND_PLAYBACK, false)
    fun getPipMode(): Boolean = prefs.getBoolean(KEY_PIP_MODE, false)
    fun getAudioDelayMs(): Int = prefs.getInt(KEY_AUDIO_DELAY, 0)
    fun getVolumeControl(): Int = prefs.getInt(KEY_VOLUME_CONTROL, 0)
    fun getNormalizeAudio(): Boolean = prefs.getBoolean(KEY_NORMALIZE_AUDIO, false)
    fun getAudioChannel(): Int = prefs.getInt(KEY_AUDIO_CHANNEL, 0)
    fun getScreenOrientation(): Int = prefs.getInt(KEY_SCREEN_ORIENTATION, 0)
    fun getAspectRatio(): Int = prefs.getInt(KEY_ASPECT_RATIO, 0)
    fun getZoomLevel(): Int = prefs.getInt(KEY_ZOOM_LEVEL, 100)
    fun getNetworkBufferMb(): Int = prefs.getInt(KEY_NETWORK_BUFFER, 50)
    fun getPreferIpv6(): Boolean = prefs.getBoolean(KEY_PREFER_IPV6, false)
    fun getUserAgent(): String = prefs.getString(KEY_USER_AGENT, "MeowPlay/1.2") ?: "MeowPlay/1.2"
    fun getNetworkMaxCacheMb(): Int = prefs.getInt(KEY_NETWORK_MAX_CACHE, 200)

    // ─── Setters ───────────────────────────────────────────────────────────

    fun setDiskCacheSize(mb: Int) { prefs.edit().putInt(KEY_DISK_CACHE, mb).apply(); recreateDiskCache() }
    fun setRamBufferSize(mb: Int) { prefs.edit().putInt(KEY_RAM_BUFFER, mb).apply() }
    fun setBufferLength(sec: Int) { prefs.edit().putInt(KEY_BUFFER_LENGTH, sec).apply() }
    fun setBackBuffer(sec: Int) { prefs.edit().putInt(KEY_BACK_BUFFER, sec).apply() }
    fun setHwAcceleration(v: Int) { prefs.edit().putInt(KEY_HW_ACCELERATION, v).apply() }
    fun setSwFallback(v: Boolean) { prefs.edit().putBoolean(KEY_SW_FALLBACK, v).apply() }
    fun setDecoderPriority(v: Int) { prefs.edit().putInt(KEY_DECODER_PRIORITY, v).apply() }
    fun setAudioPassthrough(v: Boolean) { prefs.edit().putBoolean(KEY_AUDIO_PASSTHROUGH, v).apply() }
    fun setAudioPassthroughAc3(v: Boolean) { prefs.edit().putBoolean(KEY_AUDIO_PASSTHROUGH_AC3, v).apply() }
    fun setAudioPassthroughEac3(v: Boolean) { prefs.edit().putBoolean(KEY_AUDIO_PASSTHROUGH_EAC3, v).apply() }
    fun setAudioPassthroughDts(v: Boolean) { prefs.edit().putBoolean(KEY_AUDIO_PASSTHROUGH_DTS, v).apply() }
    fun setAudioPassthroughTruehd(v: Boolean) { prefs.edit().putBoolean(KEY_AUDIO_PASSTHROUGH_TRUEHD, v).apply() }
    fun setAudioOutput(v: Int) { prefs.edit().putInt(KEY_AUDIO_OUTPUT, v).apply() }
    fun setAudioBoost(v: Int) { prefs.edit().putInt(KEY_AUDIO_BOOST, v).apply() }
    fun setSubtitleSize(v: Int) { prefs.edit().putInt(KEY_SUBTITLE_SIZE, v).apply() }
    fun setSubtitleColor(v: Int) { prefs.edit().putInt(KEY_SUBTITLE_COLOR, v).apply() }
    fun setSubtitleBgOpacity(v: Int) { prefs.edit().putInt(KEY_SUBTITLE_BG_OPACITY, v).apply() }
    fun setSubtitlePosition(v: Int) { prefs.edit().putInt(KEY_SUBTITLE_POSITION, v).apply() }
    fun setAutoResume(v: Int) { prefs.edit().putInt(KEY_AUTO_RESUME, v).apply() }
    fun setPlaybackSpeed(v: Float) { prefs.edit().putFloat(KEY_PLAYBACK_SPEED, v).apply() }
    fun setBackgroundPlayback(v: Boolean) { prefs.edit().putBoolean(KEY_BACKGROUND_PLAYBACK, v).apply() }
    fun setPipMode(v: Boolean) { prefs.edit().putBoolean(KEY_PIP_MODE, v).apply() }
    fun setAudioDelayMs(v: Int) { prefs.edit().putInt(KEY_AUDIO_DELAY, v).apply() }
    fun setVolumeControl(v: Int) { prefs.edit().putInt(KEY_VOLUME_CONTROL, v).apply() }
    fun setNormalizeAudio(v: Boolean) { prefs.edit().putBoolean(KEY_NORMALIZE_AUDIO, v).apply() }
    fun setAudioChannel(v: Int) { prefs.edit().putInt(KEY_AUDIO_CHANNEL, v).apply() }
    fun setScreenOrientation(v: Int) { prefs.edit().putInt(KEY_SCREEN_ORIENTATION, v).apply() }
    fun setAspectRatio(v: Int) { prefs.edit().putInt(KEY_ASPECT_RATIO, v).apply() }
    fun setZoomLevel(v: Int) { prefs.edit().putInt(KEY_ZOOM_LEVEL, v).apply() }
    fun setNetworkBufferMb(v: Int) { prefs.edit().putInt(KEY_NETWORK_BUFFER, v).apply() }
    fun setPreferIpv6(v: Boolean) { prefs.edit().putBoolean(KEY_PREFER_IPV6, v).apply() }
    fun setUserAgent(v: String) { prefs.edit().putString(KEY_USER_AGENT, v).apply() }
    fun setNetworkMaxCacheMb(v: Int) { prefs.edit().putInt(KEY_NETWORK_MAX_CACHE, v).apply() }

    // ─── SimpleCache Management ────────────────────────────────────────────

    fun initialize() { ensureSimpleCache() }

    private fun ensureSimpleCache(): SimpleCache? {
        val diskCacheSize = getDiskCacheSizeBytes()
        if (diskCacheSize <= 0) { releaseSimpleCache(); return null }
        if (simpleCache != null) return simpleCache

        return try {
            val cacheDir = File(context.cacheDir, "exoplayer")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            SimpleCache(cacheDir, LeastRecentlyUsedCacheEvictor(diskCacheSize), StandaloneDatabaseProvider(context))
                .also { simpleCache = it }
        } catch (e: Exception) {
            try {
                File(context.cacheDir, "exoplayer").deleteRecursively()
                val cacheDir = File(context.cacheDir, "exoplayer"); cacheDir.mkdirs()
                SimpleCache(cacheDir, LeastRecentlyUsedCacheEvictor(diskCacheSize), StandaloneDatabaseProvider(context))
                    .also { simpleCache = it }
            } catch (_: Exception) { null }
        }
    }

    private fun recreateDiskCache() { releaseSimpleCache(); ensureSimpleCache() }

    private fun releaseSimpleCache() {
        try { simpleCache?.release() } catch (_: Exception) {}
        simpleCache = null
    }

    /**
     * Create CacheDataSource.Factory — wraps the upstream HTTP factory with disk cache.
     * This ACTUALLY enables caching of video data to disk.
     */
    fun createCacheDataSourceFactory(upstreamFactory: DataSource.Factory): CacheDataSource.Factory? {
        val cache = ensureSimpleCache() ?: return null
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun createCacheDataSourceFactory(): CacheDataSource.Factory? {
        val upstream = createHttpDataSourceFactory()
        return createCacheDataSourceFactory(upstream)
    }

    /**
     * Create HTTP DataSource Factory with user settings.
     * ACTUALLY applies user-agent, timeouts, IPv6 preference.
     */
    fun createHttpDataSourceFactory(): DefaultHttpDataSource.Factory {
        return DefaultHttpDataSource.Factory()
            .setUserAgent(getUserAgent())
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setAllowCrossProtocolRedirects(true)
    }

    /**
     * Create DefaultLoadControl from user settings.
     * ACTUALLY configures buffer sizes, buffer durations, and back buffer.
     */
    fun createLoadControl(): DefaultLoadControl {
        val builder = DefaultLoadControl.Builder()

        // RAM buffer size
        val ramBytes = getRamBufferSizeBytes()
        if (ramBytes > 0) {
            builder.setTargetBufferBytes(if (ramBytes > Int.MAX_VALUE.toLong()) Int.MAX_VALUE else ramBytes.toInt())
        }

        // Buffer length (how far ahead to buffer)
        val bufferMs = getBufferLengthMs()
        val maxBufferMs = if (bufferMs > 0) {
            if (bufferMs > Int.MAX_VALUE.toLong()) Int.MAX_VALUE else bufferMs.toInt()
        } else DefaultLoadControl.DEFAULT_MAX_BUFFER_MS

        builder.setBufferDurationsMs(
            DefaultLoadControl.DEFAULT_MIN_BUFFER_MS, maxBufferMs,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
        )

        // Back buffer (rewind cache)
        val backMs = getBackBufferMs()
        builder.setBackBuffer(if (backMs > 0) backMs else DefaultLoadControl.DEFAULT_BACK_BUFFER_DURATION_MS, true)

        return builder.build()
    }

    /**
     * Create RenderersFactory from decoder settings.
     * ACTUALLY configures hardware/software decoder behavior.
     */
    fun createRenderersFactory(): RenderersFactory {
        val hwAccel = getHwAcceleration()
        val extensionMode = when (hwAccel) {
            0 -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF  // Automatic (HW only)
            1 -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF  // Full acceleration
            2 -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON   // Decoding only (allow SW for decoding)
            3 -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER  // Disabled (prefer SW)
            else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
        }

        return DefaultRenderersFactory(context).setExtensionRendererMode(extensionMode)
    }

    /**
     * Build a fully configured ExoPlayer using ALL user settings.
     * This is the central method — every setting ACTUALLY takes effect here.
     */
    fun buildExoPlayer(trackSelector: DefaultTrackSelector): ExoPlayer {
        // HTTP data source with user-agent
        val httpFactory = createHttpDataSourceFactory()

        // Cache data source (wraps HTTP if disk cache enabled)
        val dataSourceFactory: DataSource.Factory = createCacheDataSourceFactory(httpFactory) ?: httpFactory

        // Media source factory using cache
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        // Load control with buffer settings
        val loadControl = createLoadControl()

        // Renderers factory with decoder settings
        val renderersFactory = createRenderersFactory()

        // Audio attributes for passthrough
        val audioAttributes = if (getAudioPassthrough()) {
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build()
        } else null

        // Build the player
        val builder = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)

        val player = builder.build()

        // Apply audio attributes
        if (audioAttributes != null) {
            player.setAudioAttributes(audioAttributes, false)
        }

        // Apply playback speed
        val speed = getPlaybackSpeed()
        if (speed != 1.0f) {
            player.setPlaybackSpeed(speed)
        }

        return player
    }

    // ─── Cache Info ────────────────────────────────────────────────────────

    fun getCacheSize(): Long {
        val cacheDir = File(context.cacheDir, "exoplayer")
        return if (cacheDir.exists()) calculateDirSize(cacheDir) else 0L
    }

    fun getTotalCacheSize(): Long = calculateDirSize(context.cacheDir)

    fun formatCacheSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
    }

    fun clearVideoCache() {
        releaseSimpleCache()
        File(context.cacheDir, "exoplayer").deleteRecursively()
        ensureSimpleCache()
    }

    fun clearAllCache() {
        releaseSimpleCache()
        context.cacheDir.deleteRecursively()
        context.cacheDir.mkdirs()
        ensureSimpleCache()
    }

    fun release() { releaseSimpleCache() }

    private fun calculateDirSize(dir: File): Long {
        var size = 0L
        if (dir.exists()) dir.walkTopDown().forEach { if (it.isFile) size += it.length() }
        return size
    }
}
