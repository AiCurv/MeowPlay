package com.meowplay.tv.player

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import java.io.File

/**
 * Advanced Cache Manager for MeowPlay.
 *
 * Inspired by CloudStream's cache implementation but with significantly
 * more granular control and extended ranges.
 *
 * Three-layer caching architecture:
 * 1. RAM Buffer (DefaultLoadControl.setTargetBufferBytes) — controls in-memory buffer
 * 2. Disk Cache (SimpleCache + LRU Evictor) — persists downloaded video data to disk
 * 3. Buffer Duration (DefaultLoadControl.setBufferDurationsMs) — controls how far ahead to buffer
 *
 * All settings are configurable from the Settings UI with granular step options.
 */
class CacheManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("meowplay_cache", Context.MODE_PRIVATE)

    // ─── SimpleCache Singleton ───────────────────────────────────────────────
    companion object {
        @Volatile
        private var INSTANCE: CacheManager? = null

        // SimpleCache is shared across all player instances
        private var simpleCache: SimpleCache? = null

        fun getInstance(context: Context): CacheManager {
            return INSTANCE ?: synchronized(this) {
                val instance = CacheManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        /** Get the shared SimpleCache instance (used by MeowExoPlayer) */
        fun getSimpleCache(): SimpleCache? = simpleCache
    }

    // ─── Cache Keys ──────────────────────────────────────────────────────────
    object Keys {
        const val DISK_CACHE_SIZE = "disk_cache_size_mb"
        const val RAM_BUFFER_SIZE = "ram_buffer_size_mb"
        const val BUFFER_LENGTH = "buffer_length_seconds"
        const val BACK_BUFFER = "back_buffer_seconds"
    }

    // ─── Disk Cache Size Options (MB) ────────────────────────────────────────
    // Extended range from CloudStream: Auto, 10MB to 5000MB (5GB)
    object DiskCacheOptions {
        val VALUES = intArrayOf(
            0,      // Auto (no disk cache)
            10, 20, 30, 40, 50, 75, 100, 150, 200, 250, 300, 400, 500,
            750, 1000, 1500, 2000, 3000, 4000, 5000
        )

        val LABELS = arrayOf(
            "Auto (No disk cache)",
            "10 MB", "20 MB", "30 MB", "40 MB", "50 MB", "75 MB",
            "100 MB", "150 MB", "200 MB", "250 MB", "300 MB", "400 MB", "500 MB",
            "750 MB", "1 GB", "1.5 GB", "2 GB", "3 GB", "4 GB", "5 GB"
        )

        fun getLabelForValue(value: Int): String {
            val index = VALUES.indexOf(value)
            return if (index >= 0) LABELS[index] else "Auto"
        }
    }

    // ─── RAM Buffer Size Options (MB) ────────────────────────────────────────
    // For armv7l (32-bit) devices, values above 300MB may cause OOM
    object RamBufferOptions {
        val VALUES = intArrayOf(
            0,      // Auto (ExoPlayer default ~2.5x max track bitrate)
            10, 20, 30, 40, 50, 75, 100, 150, 200, 250, 300,
            400, 500, 750, 1000
        )

        val LABELS = arrayOf(
            "Auto (Recommended)",
            "10 MB", "20 MB", "30 MB", "40 MB", "50 MB", "75 MB",
            "100 MB", "150 MB", "200 MB", "250 MB", "300 MB",
            "400 MB ⚠ High RAM", "500 MB ⚠ High RAM",
            "750 MB ⚠ May crash", "1 GB ⚠ May crash"
        )

        /** Warning level for RAM buffer values */
        fun getWarningLevel(value: Int): WarningLevel {
            return when {
                value <= 0 -> WarningLevel.NONE
                value <= 300 -> WarningLevel.NONE
                value <= 500 -> WarningLevel.HIGH_RAM
                else -> WarningLevel.MAY_CRASH
            }
        }

        fun getLabelForValue(value: Int): String {
            val index = VALUES.indexOf(value)
            return if (index >= 0) LABELS[index] else "Auto"
        }
    }

    // ─── Buffer Length Options (Seconds) ──────────────────────────────────────
    object BufferLengthOptions {
        val VALUES = intArrayOf(
            0,      // Auto (ExoPlayer default ~50s)
            30, 60, 90, 120, 150, 180, 240, 300, 360, 420, 480, 540,
            600, 900, 1200, 1500, 1800
        )

        val LABELS = arrayOf(
            "Auto (Default ~50s)",
            "30 seconds", "1 minute", "1 min 30s", "2 minutes", "2 min 30s",
            "3 minutes", "4 minutes", "5 minutes", "6 minutes", "7 minutes",
            "8 minutes", "9 minutes", "10 minutes", "15 minutes", "20 minutes",
            "25 minutes", "30 minutes"
        )

        fun getLabelForValue(value: Int): String {
            val index = VALUES.indexOf(value)
            return if (index >= 0) LABELS[index] else "Auto"
        }
    }

    // ─── Back Buffer Options (Seconds) ────────────────────────────────────────
    object BackBufferOptions {
        val VALUES = intArrayOf(
            0,      // Auto (30s default)
            10, 20, 30, 45, 60, 90, 120
        )

        val LABELS = arrayOf(
            "Auto (30s default)",
            "10 seconds", "20 seconds", "30 seconds", "45 seconds",
            "1 minute", "1 min 30s", "2 minutes"
        )

        fun getLabelForValue(value: Int): String {
            val index = VALUES.indexOf(value)
            return if (index >= 0) LABELS[index] else "Auto"
        }
    }

    enum class WarningLevel {
        NONE, HIGH_RAM, MAY_CRASH
    }

    // ─── Getters ─────────────────────────────────────────────────────────────

    /** Get configured disk cache size in bytes */
    fun getDiskCacheSizeBytes(): Long {
        val mb = prefs.getInt(Keys.DISK_CACHE_SIZE, 0)
        return if (mb <= 0) 0L else mb.toLong() * 1024L * 1024L
    }

    /** Get configured RAM buffer size in bytes */
    fun getRamBufferSizeBytes(): Long {
        val mb = prefs.getInt(Keys.RAM_BUFFER_SIZE, 0)
        return if (mb <= 0) 0L else mb.toLong() * 1024L * 1024L
    }

    /** Get configured max buffer duration in milliseconds */
    fun getBufferLengthMs(): Long {
        val seconds = prefs.getInt(Keys.BUFFER_LENGTH, 0)
        return if (seconds <= 0) 0L else seconds.toLong() * 1000L
    }

    /** Get configured back buffer duration in milliseconds */
    fun getBackBufferMs(): Int {
        val seconds = prefs.getInt(Keys.BACK_BUFFER, 0)
        return if (seconds <= 0) 0 else seconds * 1000
    }

    // ─── Setters ─────────────────────────────────────────────────────────────

    fun setDiskCacheSize(mb: Int) {
        prefs.edit().putInt(Keys.DISK_CACHE_SIZE, mb).apply()
        recreateDiskCache()
    }

    fun setRamBufferSize(mb: Int) {
        prefs.edit().putInt(Keys.RAM_BUFFER_SIZE, mb).apply()
    }

    fun setBufferLength(seconds: Int) {
        prefs.edit().putInt(Keys.BUFFER_LENGTH, seconds).apply()
    }

    fun setBackBuffer(seconds: Int) {
        prefs.edit().putInt(Keys.BACK_BUFFER, seconds).apply()
    }

    // ─── SimpleCache Management ──────────────────────────────────────────────

    /**
     * Initialize or get the SimpleCache instance.
     * Called once at app startup.
     */
    fun initialize() {
        ensureSimpleCache()
    }

    private fun ensureSimpleCache(): SimpleCache? {
        val diskCacheSize = getDiskCacheSizeBytes()
        if (diskCacheSize <= 0) {
            // Disk cache disabled (Auto = no disk cache)
            releaseSimpleCache()
            return null
        }

        if (simpleCache != null) {
            return simpleCache
        }

        return try {
            val cacheDir = File(context.cacheDir, "exoplayer")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val databaseProvider = StandaloneDatabaseProvider(context)
            val evictor = LeastRecentlyUsedCacheEvictor(diskCacheSize)

            SimpleCache(cacheDir, evictor, databaseProvider).also {
                simpleCache = it
            }
        } catch (e: Exception) {
            // If cache creation fails (e.g., corrupt index), delete and retry
            try {
                File(context.cacheDir, "exoplayer").deleteRecursively()
                val cacheDir = File(context.cacheDir, "exoplayer")
                cacheDir.mkdirs()

                val databaseProvider = StandaloneDatabaseProvider(context)
                val evictor = LeastRecentlyUsedCacheEvictor(diskCacheSize)

                SimpleCache(cacheDir, evictor, databaseProvider).also {
                    simpleCache = it
                }
            } catch (e2: Exception) {
                null
            }
        }
    }

    /**
     * Recreate the disk cache when settings change.
     * This releases the old cache and creates a new one with updated size.
     */
    private fun recreateDiskCache() {
        releaseSimpleCache()
        ensureSimpleCache()
    }

    private fun releaseSimpleCache() {
        try {
            simpleCache?.release()
        } catch (e: Exception) {
            // Ignore release errors
        }
        simpleCache = null
    }

    /**
     * Create a CacheDataSource.Factory for use with ExoPlayer.
     * This wraps the upstream data source with disk caching.
     */
    fun createCacheDataSourceFactory(): CacheDataSource.Factory? {
        val cache = ensureSimpleCache() ?: return null

        val upstreamFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("MeowPlay/1.0")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * Create a DefaultLoadControl configured with the user's buffer settings.
     * This controls the RAM buffer and buffer duration.
     */
    fun createLoadControl(): DefaultLoadControl {
        val builder = DefaultLoadControl.Builder()

        // RAM buffer size (target buffer bytes)
        val ramBufferBytes = getRamBufferSizeBytes()
        if (ramBufferBytes > 0) {
            builder.setTargetBufferBytes(
                if (ramBufferBytes > Int.MAX_VALUE.toLong()) Int.MAX_VALUE
                else ramBufferBytes.toInt()
            )
        }

        // Buffer duration (how far ahead to buffer)
        val bufferLengthMs = getBufferLengthMs()
        val maxBufferMs = if (bufferLengthMs > 0) {
            if (bufferLengthMs > Int.MAX_VALUE.toLong()) Int.MAX_VALUE
            else bufferLengthMs.toInt()
        } else {
            DefaultLoadControl.DEFAULT_MAX_BUFFER_MS
        }

        builder.setBufferDurationsMs(
            DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
            maxBufferMs,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
        )

        // Back buffer (how much behind current position to keep)
        val backBufferMs = getBackBufferMs()
        if (backBufferMs > 0) {
            builder.setBackBuffer(backBufferMs, true)
        } else {
            builder.setBackBuffer(30000, true) // Default 30s
        }

        return builder.build()
    }

    // ─── Cache Info & Clearing ────────────────────────────────────────────────

    /** Get the current disk cache size in bytes */
    fun getCacheSize(): Long {
        val cacheDir = File(context.cacheDir, "exoplayer")
        return if (cacheDir.exists()) calculateDirectorySize(cacheDir) else 0L
    }

    /** Get total app cache size (including image cache, etc.) */
    fun getTotalCacheSize(): Long {
        return calculateDirectorySize(context.cacheDir)
    }

    /** Format cache size for display */
    fun formatCacheSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }

    /** Clear video disk cache */
    fun clearVideoCache() {
        releaseSimpleCache()
        val cacheDir = File(context.cacheDir, "exoplayer")
        cacheDir.deleteRecursively()
        ensureSimpleCache()
    }

    /** Clear all cache (video + images + everything) */
    fun clearAllCache() {
        releaseSimpleCache()
        context.cacheDir.deleteRecursively()
        context.cacheDir.mkdirs()
        ensureSimpleCache()
    }

    /** Release all cache resources. Call on app termination. */
    fun release() {
        releaseSimpleCache()
    }

    private fun calculateDirectorySize(directory: File): Long {
        var size = 0L
        if (directory.exists()) {
            directory.walkTopDown().forEach { file ->
                if (file.isFile) {
                    size += file.length()
                }
            }
        }
        return size
    }
}
