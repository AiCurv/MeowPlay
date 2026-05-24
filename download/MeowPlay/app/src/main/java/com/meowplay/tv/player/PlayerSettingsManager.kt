package com.meowplay.tv.player

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer

/**
 * Manages ALL player settings via SharedPreferences.
 * Every getter reads from prefs, every setter writes and applies immediately.
 * Used by CacheManager.buildExoPlayer() to create a fully configured player.
 */
class PlayerSettingsManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("meowplay_settings", Context.MODE_PRIVATE)

    companion object {
        @Volatile
        private var INSTANCE: PlayerSettingsManager? = null

        fun getInstance(context: Context): PlayerSettingsManager {
            return INSTANCE ?: synchronized(this) {
                PlayerSettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        // ─── Cache Keys ──────────────────────────────────────────────
        const val KEY_DISK_CACHE = "disk_cache_size_mb"
        const val KEY_RAM_BUFFER = "ram_buffer_size_mb"
        const val KEY_BUFFER_LENGTH = "buffer_length_seconds"
        const val KEY_BACK_BUFFER = "back_buffer_seconds"

        // ─── Decoder Keys ────────────────────────────────────────────
        const val KEY_HW_ACCEL = "hw_accel"          // 0=Auto,1=Full,2=DecodingOnly,3=Disabled
        const val KEY_SW_FALLBACK = "sw_fallback"     // true/false
        const val KEY_DECODER_PRIORITY = "decoder_priority" // 0=PreferHW,1=PreferSW

        // ─── Audio Keys ──────────────────────────────────────────────
        const val KEY_AUDIO_PASSTHROUGH = "audio_passthrough"
        const val KEY_AUDIO_PASSTHROUGH_AC3 = "passthrough_ac3"
        const val KEY_AUDIO_PASSTHROUGH_EAC3 = "passthrough_eac3"
        const val KEY_AUDIO_PASSTHROUGH_DTS = "passthrough_dts"
        const val KEY_AUDIO_PASSTHROUGH_TRUEHD = "passthrough_truehd"
        const val KEY_AUDIO_OUTPUT_DEVICE = "audio_output_device" // 0=System,1=HDMI,2=BT,3=USB
        const val KEY_AUDIO_BOOST = "audio_boost"      // 0,50,100,150,200

        // ─── Subtitle Keys ───────────────────────────────────────────
        const val KEY_SUBTITLE_FONT_SIZE = "subtitle_font_size" // 0=Small,1=Med,2=Large,3=XL
        const val KEY_SUBTITLE_COLOR = "subtitle_color" // 0=White,1=Yellow,2=Cyan,3=Custom
        const val KEY_SUBTITLE_BG_OPACITY = "subtitle_bg_opacity" // 0,25,50,75,100
        const val KEY_SUBTITLE_POSITION = "subtitle_position" // 0=Top,1=Middle,2=Bottom

        // ─── Playback Keys ───────────────────────────────────────────
        const val KEY_AUTO_RESUME = "auto_resume"     // 0=Ask,1=Resume,2=StartOver
        const val KEY_PLAYBACK_SPEED = "playback_speed" // "1.0","1.5","2.0", etc.
        const val KEY_BACKGROUND_PLAYBACK = "background_playback"
        const val KEY_PIP = "pip_enabled"

        // ─── Audio Sync Keys ─────────────────────────────────────────
        const val KEY_AUDIO_DELAY_MS = "audio_delay_ms" // -500..500
        const val KEY_VOLUME_CONTROL = "volume_control" // 0=System,1=Software
        const val KEY_NORMALIZE_AUDIO = "normalize_audio"
        const val KEY_AUDIO_CHANNEL_DOWNMIX = "audio_downmix" // 0=Stereo,1=Surround

        // ─── Video/Display Keys ──────────────────────────────────────
        const val KEY_SCREEN_ORIENTATION = "screen_orientation" // 0=Auto,1=Landscape,2=Portrait
        const val KEY_ASPECT_RATIO = "aspect_ratio" // 0=Default,1=16:9,2=4:3,3=Fill,4=Crop
        const val KEY_ZOOM_LEVEL = "zoom_level"     // 100,150,200
        const val KEY_BRIGHTNESS = "brightness"      // 0-100
        const val KEY_CONTRAST = "contrast"
        const val KEY_SATURATION = "saturation"
        const val KEY_HUE = "hue"

        // ─── Network Keys ────────────────────────────────────────────
        const val KEY_NETWORK_BUFFER = "network_buffer" // 0=Small(10MB),1=Medium(50MB),2=Large(200MB),3=Custom
        const val KEY_NETWORK_BUFFER_CUSTOM_MB = "network_buffer_custom_mb"
        const val KEY_PREFER_IPV6 = "prefer_ipv6"
        const val KEY_USER_AGENT = "user_agent"
        const val KEY_NETWORK_CACHE_MAX = "network_cache_max" // 0=50MB,1=200MB,2=Unlimited
    }

    // ═══════════════════════════════════════════════════════════════════
    // CACHE SETTINGS
    // ═══════════════════════════════════════════════════════════════════

    object DiskCacheOptions {
        val VALUES = intArrayOf(0, 250, 500, 1000, 2000, -1) // -1 = Custom
        val LABELS = arrayOf("Auto (No disk cache)", "250 MB", "500 MB", "1 GB", "2 GB", "Custom")
        fun labelFor(value: Int) = VALUES.indexOf(value).let { if (it >= 0) LABELS[it] else "Auto" }
    }

    object RamBufferOptions {
        val VALUES = intArrayOf(0, 100, 250, 300, 350, 390, 500, -1)
        val LABELS = arrayOf("Auto (Recommended)", "100 MB", "250 MB", "300 MB", "350 MB", "390 MB", "500 MB", "Custom")
        fun labelFor(value: Int) = VALUES.indexOf(value).let { if (it >= 0) LABELS[it] else "Auto" }
    }

    object BufferLengthOptions {
        val VALUES = intArrayOf(0, 60, 120, 300, 600, 900, 1200, 1500, 1800, -1)
        val LABELS = arrayOf("Auto", "1 min", "2 min", "5 min", "10 min", "15 min", "20 min", "25 min", "30 min", "Custom")
        fun labelFor(value: Int) = VALUES.indexOf(value).let { if (it >= 0) LABELS[it] else "Auto" }
    }

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS — Cache
    // ═══════════════════════════════════════════════════════════════════

    fun getDiskCacheSizeMb(): Int = prefs.getInt(KEY_DISK_CACHE, 0)
    fun getRamBufferSizeMb(): Int = prefs.getInt(KEY_RAM_BUFFER, 0)
    fun getBufferLengthSec(): Int = prefs.getInt(KEY_BUFFER_LENGTH, 0)
    fun getBackBufferSec(): Int = prefs.getInt(KEY_BACK_BUFFER, 0)

    fun getDiskCacheSizeBytes(): Long {
        val mb = getDiskCacheSizeMb()
        return if (mb <= 0) 0L else mb.toLong() * 1024L * 1024L
    }

    fun getRamBufferSizeBytes(): Long {
        val mb = getRamBufferSizeMb()
        return if (mb <= 0) 0L else mb.toLong() * 1024L * 1024L
    }

    fun getBufferLengthMs(): Long {
        val sec = getBufferLengthSec()
        return if (sec <= 0) 0L else sec.toLong() * 1000L
    }

    fun getBackBufferMs(): Int {
        val sec = getBackBufferSec()
        return if (sec <= 0) 0 else sec * 1000
    }

    // ═══════════════════════════════════════════════════════════════════
    // SETTERS — Cache
    // ═══════════════════════════════════════════════════════════════════

    fun setDiskCacheSize(mb: Int) { prefs.edit().putInt(KEY_DISK_CACHE, mb).apply() }
    fun setRamBufferSize(mb: Int) { prefs.edit().putInt(KEY_RAM_BUFFER, mb).apply() }
    fun setBufferLength(sec: Int) { prefs.edit().putInt(KEY_BUFFER_LENGTH, sec).apply() }
    fun setBackBuffer(sec: Int) { prefs.edit().putInt(KEY_BACK_BUFFER, sec).apply() }

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS — Decoder
    // ═══════════════════════════════════════════════════════════════════

    fun getHwAccel(): Int = prefs.getInt(KEY_HW_ACCEL, 0)
    fun getSwFallback(): Boolean = prefs.getBoolean(KEY_SW_FALLBACK, true)
    fun getDecoderPriority(): Int = prefs.getInt(KEY_DECODER_PRIORITY, 0)

    fun getExtensionRendererMode(): Int {
        val hwAccel = getHwAccel()
        val swFallback = getSwFallback()
        return when {
            hwAccel == 3 -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
            hwAccel == 2 -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            swFallback -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
            else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
        }
    }

    fun setHwAccel(mode: Int) { prefs.edit().putInt(KEY_HW_ACCEL, mode).apply() }
    fun setSwFallback(enabled: Boolean) { prefs.edit().putBoolean(KEY_SW_FALLBACK, enabled).apply() }
    fun setDecoderPriority(priority: Int) { prefs.edit().putInt(KEY_DECODER_PRIORITY, priority).apply() }

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS/SETTERS — Audio
    // ═══════════════════════════════════════════════════════════════════

    fun getAudioPassthrough(): Boolean = prefs.getBoolean(KEY_AUDIO_PASSTHROUGH, false)
    fun getPassthroughAc3(): Boolean = prefs.getBoolean(KEY_AUDIO_PASSTHROUGH_AC3, true)
    fun getPassthroughEac3(): Boolean = prefs.getBoolean(KEY_AUDIO_PASSTHROUGH_EAC3, true)
    fun getPassthroughDts(): Boolean = prefs.getBoolean(KEY_AUDIO_PASSTHROUGH_DTS, true)
    fun getPassthroughTruehd(): Boolean = prefs.getBoolean(KEY_AUDIO_PASSTHROUGH_TRUEHD, false)
    fun getAudioOutputDevice(): Int = prefs.getInt(KEY_AUDIO_OUTPUT_DEVICE, 0)
    fun getAudioBoost(): Int = prefs.getInt(KEY_AUDIO_BOOST, 0)

    fun setAudioPassthrough(enabled: Boolean) { prefs.edit().putBoolean(KEY_AUDIO_PASSTHROUGH, enabled).apply() }
    fun setPassthroughAc3(enabled: Boolean) { prefs.edit().putBoolean(KEY_AUDIO_PASSTHROUGH_AC3, enabled).apply() }
    fun setPassthroughEac3(enabled: Boolean) { prefs.edit().putBoolean(KEY_AUDIO_PASSTHROUGH_EAC3, enabled).apply() }
    fun setPassthroughDts(enabled: Boolean) { prefs.edit().putBoolean(KEY_AUDIO_PASSTHROUGH_DTS, enabled).apply() }
    fun setPassthroughTruehd(enabled: Boolean) { prefs.edit().putBoolean(KEY_AUDIO_PASSTHROUGH_TRUEHD, enabled).apply() }
    fun setAudioOutputDevice(device: Int) { prefs.edit().putInt(KEY_AUDIO_OUTPUT_DEVICE, device).apply() }
    fun setAudioBoost(percent: Int) { prefs.edit().putInt(KEY_AUDIO_BOOST, percent).apply() }

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS/SETTERS — Subtitle
    // ═══════════════════════════════════════════════════════════════════

    fun getSubtitleFontSize(): Int = prefs.getInt(KEY_SUBTITLE_FONT_SIZE, 1)
    fun getSubtitleColor(): Int = prefs.getInt(KEY_SUBTITLE_COLOR, 0)
    fun getSubtitleBgOpacity(): Int = prefs.getInt(KEY_SUBTITLE_BG_OPACITY, 50)
    fun getSubtitlePosition(): Int = prefs.getInt(KEY_SUBTITLE_POSITION, 2)

    fun setSubtitleFontSize(size: Int) { prefs.edit().putInt(KEY_SUBTITLE_FONT_SIZE, size).apply() }
    fun setSubtitleColor(color: Int) { prefs.edit().putInt(KEY_SUBTITLE_COLOR, color).apply() }
    fun setSubtitleBgOpacity(opacity: Int) { prefs.edit().putInt(KEY_SUBTITLE_BG_OPACITY, opacity).apply() }
    fun setSubtitlePosition(position: Int) { prefs.edit().putInt(KEY_SUBTITLE_POSITION, position).apply() }

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS/SETTERS — Playback
    // ═══════════════════════════════════════════════════════════════════

    fun getAutoResume(): Int = prefs.getInt(KEY_AUTO_RESUME, 0)
    fun getPlaybackSpeed(): Float = prefs.getString(KEY_PLAYBACK_SPEED, "1.0")?.toFloatOrNull() ?: 1.0f
    fun getBackgroundPlayback(): Boolean = prefs.getBoolean(KEY_BACKGROUND_PLAYBACK, false)
    fun getPipEnabled(): Boolean = prefs.getBoolean(KEY_PIP, false)

    fun setAutoResume(mode: Int) { prefs.edit().putInt(KEY_AUTO_RESUME, mode).apply() }
    fun setPlaybackSpeed(speed: String) { prefs.edit().putString(KEY_PLAYBACK_SPEED, speed).apply() }
    fun setBackgroundPlayback(enabled: Boolean) { prefs.edit().putBoolean(KEY_BACKGROUND_PLAYBACK, enabled).apply() }
    fun setPipEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_PIP, enabled).apply() }

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS/SETTERS — Audio Sync
    // ═══════════════════════════════════════════════════════════════════

    fun getAudioDelayMs(): Int = prefs.getInt(KEY_AUDIO_DELAY_MS, 0)
    fun getVolumeControl(): Int = prefs.getInt(KEY_VOLUME_CONTROL, 0)
    fun getNormalizeAudio(): Boolean = prefs.getBoolean(KEY_NORMALIZE_AUDIO, false)
    fun getAudioChannelDownmix(): Int = prefs.getInt(KEY_AUDIO_CHANNEL_DOWNMIX, 0)

    fun setAudioDelayMs(delayMs: Int) { prefs.edit().putInt(KEY_AUDIO_DELAY_MS, delayMs.coerceIn(-500, 500)).apply() }
    fun setVolumeControl(mode: Int) { prefs.edit().putInt(KEY_VOLUME_CONTROL, mode).apply() }
    fun setNormalizeAudio(enabled: Boolean) { prefs.edit().putBoolean(KEY_NORMALIZE_AUDIO, enabled).apply() }
    fun setAudioChannelDownmix(mode: Int) { prefs.edit().putInt(KEY_AUDIO_CHANNEL_DOWNMIX, mode).apply() }

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS/SETTERS — Video/Display
    // ═══════════════════════════════════════════════════════════════════

    fun getScreenOrientation(): Int = prefs.getInt(KEY_SCREEN_ORIENTATION, 0)
    fun getAspectRatio(): Int = prefs.getInt(KEY_ASPECT_RATIO, 0)
    fun getZoomLevel(): Int = prefs.getInt(KEY_ZOOM_LEVEL, 100)
    fun getBrightness(): Int = prefs.getInt(KEY_BRIGHTNESS, 50)
    fun getContrast(): Int = prefs.getInt(KEY_CONTRAST, 50)
    fun getSaturation(): Int = prefs.getInt(KEY_SATURATION, 50)
    fun getHue(): Int = prefs.getInt(KEY_HUE, 50)

    fun setScreenOrientation(orientation: Int) { prefs.edit().putInt(KEY_SCREEN_ORIENTATION, orientation).apply() }
    fun setAspectRatio(ratio: Int) { prefs.edit().putInt(KEY_ASPECT_RATIO, ratio).apply() }
    fun setZoomLevel(level: Int) { prefs.edit().putInt(KEY_ZOOM_LEVEL, level).apply() }
    fun setBrightness(value: Int) { prefs.edit().putInt(KEY_BRIGHTNESS, value).apply() }
    fun setContrast(value: Int) { prefs.edit().putInt(KEY_CONTRAST, value).apply() }
    fun setSaturation(value: Int) { prefs.edit().putInt(KEY_SATURATION, value).apply() }
    fun setHue(value: Int) { prefs.edit().putInt(KEY_HUE, value).apply() }

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS/SETTERS — Network
    // ═══════════════════════════════════════════════════════════════════

    fun getNetworkBuffer(): Int = prefs.getInt(KEY_NETWORK_BUFFER, 1)
    fun getNetworkBufferCustomMb(): Int = prefs.getInt(KEY_NETWORK_BUFFER_CUSTOM_MB, 50)
    fun getPreferIpv6(): Boolean = prefs.getBoolean(KEY_PREFER_IPV6, false)
    fun getUserAgent(): String = prefs.getString(KEY_USER_AGENT, "MeowPlay/1.2") ?: "MeowPlay/1.2"
    fun getNetworkCacheMax(): Int = prefs.getInt(KEY_NETWORK_CACHE_MAX, 1)

    fun setNetworkBuffer(mode: Int) { prefs.edit().putInt(KEY_NETWORK_BUFFER, mode).apply() }
    fun setNetworkBufferCustomMb(mb: Int) { prefs.edit().putInt(KEY_NETWORK_BUFFER_CUSTOM_MB, mb).apply() }
    fun setPreferIpv6(enabled: Boolean) { prefs.edit().putBoolean(KEY_PREFER_IPV6, enabled).apply() }
    fun setUserAgent(agent: String) { prefs.edit().putString(KEY_USER_AGENT, agent).apply() }
    fun setNetworkCacheMax(mode: Int) { prefs.edit().putInt(KEY_NETWORK_CACHE_MAX, mode).apply() }

    fun getNetworkBufferBytes(): Long {
        return when (getNetworkBuffer()) {
            0 -> 10L * 1024 * 1024
            1 -> 50L * 1024 * 1024
            2 -> 200L * 1024 * 1024
            3 -> getNetworkBufferCustomMb().toLong() * 1024 * 1024
            else -> 50L * 1024 * 1024
        }
    }

    fun getNetworkCacheMaxBytes(): Long {
        return when (getNetworkCacheMax()) {
            0 -> 50L * 1024 * 1024
            1 -> 200L * 1024 * 1024
            2 -> Long.MAX_VALUE
            else -> 200L * 1024 * 1024
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // LABEL HELPERS
    // ═══════════════════════════════════════════════════════════════════

    object HwAccelLabels {
        val LABELS = arrayOf("Automatic", "Full Acceleration", "Decoding Only", "Disabled")
    }

    object DecoderPriorityLabels {
        val LABELS = arrayOf("Prefer Hardware", "Prefer Software")
    }

    object AudioOutputLabels {
        val LABELS = arrayOf("System Default", "HDMI", "Bluetooth", "USB DAC")
    }

    object AudioBoostLabels {
        val VALUES = intArrayOf(0, 50, 100, 150, 200)
        val LABELS = arrayOf("Disabled", "50%", "100%", "150%", "200%")
    }

    object SubtitleFontSizeLabels {
        val LABELS = arrayOf("Small", "Medium", "Large", "Extra Large")
    }

    object SubtitleColorLabels {
        val LABELS = arrayOf("White", "Yellow", "Cyan", "Custom")
    }

    object SubtitleBgOpacityLabels {
        val VALUES = intArrayOf(0, 25, 50, 75, 100)
        val LABELS = arrayOf("0%", "25%", "50%", "75%", "100%")
    }

    object SubtitlePositionLabels {
        val LABELS = arrayOf("Top", "Middle", "Bottom")
    }

    object AutoResumeLabels {
        val LABELS = arrayOf("Always Ask", "Resume from Last Position", "Start from Beginning")
    }

    object PlaybackSpeedLabels {
        val VALUES = arrayOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x")
        val KEYS = arrayOf("0.5", "0.75", "1.0", "1.25", "1.5", "2.0")
    }

    object AspectRatioLabels {
        val LABELS = arrayOf("Default", "16:9", "4:3", "Fill Screen", "Crop")
    }

    object NetworkBufferLabels {
        val LABELS = arrayOf("Small (10MB)", "Medium (50MB)", "Large (200MB)", "Custom")
    }

    object NetworkCacheMaxLabels {
        val LABELS = arrayOf("50 MB", "200 MB", "Unlimited")
    }

    object ScreenOrientationLabels {
        val LABELS = arrayOf("Automatic", "Landscape", "Portrait")
    }

    object ZoomLevelLabels {
        val VALUES = intArrayOf(100, 150, 200)
        val LABELS = arrayOf("100%", "150%", "200%")
    }

    object VolumeControlLabels {
        val LABELS = arrayOf("System Volume", "Software Volume")
    }

    object ChannelDownmixLabels {
        val LABELS = arrayOf("Stereo", "Surround")
    }
}
