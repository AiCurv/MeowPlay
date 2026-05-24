package com.meowplay.tv.player

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import java.io.File

/**
 * Advanced Cache Manager for MeowPlay.
 *
 * v2 FIXES:
 * - createCacheDataSourceFactory now takes the upstream factory as parameter
 * - Settings changes take effect immediately on next player creation
 * - Cache options match CloudStream's ranges
 * - Added General player options
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

        // Keys
        const val KEY_DISK_CACHE = "disk_cache_size_mb"
        const val KEY_RAM_BUFFER = "ram_buffer_size_mb"
        const val KEY_BUFFER_LENGTH = "buffer_length_seconds"
        const val KEY_BACK_BUFFER = "back_buffer_seconds"
    }

    // ─── Cache Options (matching CloudStream's ranges) ────────────────────────

    object DiskCacheOptions {
        val VALUES = intArrayOf(0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 150, 200, 250, 300, 400, 500, 750, 1000, 1500, 2000, 3000, 4000, 5000)
        val LABELS = arrayOf("Auto (No disk cache)", "10 MB", "20 MB", "30 MB", "40 MB", "50 MB", "60 MB", "70 MB", "80 MB", "90 MB", "100 MB", "150 MB", "200 MB", "250 MB", "300 MB", "400 MB", "500 MB", "750 MB", "1 GB", "1.5 GB", "2 GB", "3 GB", "4 GB", "5 GB")
        fun labelFor(value: Int) = VALUES.indexOf(value).let { if (it >= 0) LABELS[it] else "Auto" }
    }

    object RamBufferOptions {
        val VALUES = intArrayOf(0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 150, 200, 250, 300, 400, 500, 750, 1000)
        val LABELS = arrayOf("Auto (Recommended)", "10 MB", "20 MB", "30 MB", "40 MB", "50 MB", "60 MB", "70 MB", "80 MB", "90 MB", "100 MB", "150 MB", "200 MB", "250 MB", "300 MB", "400 MB", "500 MB", "750 MB", "1 GB")
        fun labelFor(value: Int) = VALUES.indexOf(value).let { if (it >= 0) LABELS[it] else "Auto" }
    }

    object BufferLengthOptions {
        val VALUES = intArrayOf(0, 30, 60, 90, 120, 150, 180, 240, 300, 360, 420, 480, 540, 600, 900, 1200, 1500, 1800)
        val LABELS = arrayOf("Auto (Default ~50s)", "30 seconds", "1 minute", "1 min 30s", "2 minutes", "2 min 30s", "3 minutes", "4 minutes", "5 minutes", "6 minutes", "7 minutes", "8 minutes", "9 minutes", "10 minutes", "15 minutes", "20 minutes", "25 minutes", "30 minutes")
        fun labelFor(value: Int) = VALUES.indexOf(value).let { if (it >= 0) LABELS[it] else "Auto" }
    }

    object BackBufferOptions {
        val VALUES = intArrayOf(0, 10, 20, 30, 45, 60, 90, 120)
        val LABELS = arrayOf("Auto (30s default)", "10 seconds", "20 seconds", "30 seconds", "45 seconds", "1 minute", "1 min 30s", "2 minutes")
        fun labelFor(value: Int) = VALUES.indexOf(value).let { if (it >= 0) LABELS[it] else "Auto" }
    }

    // ─── Getters ─────────────────────────────────────────────────────────────

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
        val sec = prefs.getInt(KEY_BACK_BUFFER, 0)
        return if (sec <= 0) 0 else sec * 1000
    }

    // ─── Setters ─────────────────────────────────────────────────────────────

    fun setDiskCacheSize(mb: Int) { prefs.edit().putInt(KEY_DISK_CACHE, mb).apply(); recreateDiskCache() }
    fun setRamBufferSize(mb: Int) { prefs.edit().putInt(KEY_RAM_BUFFER, mb).apply() }
    fun setBufferLength(sec: Int) { prefs.edit().putInt(KEY_BUFFER_LENGTH, sec).apply() }
    fun setBackBuffer(sec: Int) { prefs.edit().putInt(KEY_BACK_BUFFER, sec).apply() }

    // ─── SimpleCache Management ──────────────────────────────────────────────

    fun initialize() { ensureSimpleCache() }

    private fun ensureSimpleCache(): SimpleCache? {
        val diskCacheSize = getDiskCacheSizeBytes()
        if (diskCacheSize <= 0) { releaseSimpleCache(); return null }
        if (simpleCache != null) return simpleCache

        return try {
            val cacheDir = File(context.cacheDir, "exoplayer")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            SimpleCache(cacheDir, LeastRecentlyUsedCacheEvictor(diskCacheSize), StandaloneDatabaseProvider(context)).also { simpleCache = it }
        } catch (e: Exception) {
            try {
                File(context.cacheDir, "exoplayer").deleteRecursively()
                val cacheDir = File(context.cacheDir, "exoplayer"); cacheDir.mkdirs()
                SimpleCache(cacheDir, LeastRecentlyUsedCacheEvictor(diskCacheSize), StandaloneDatabaseProvider(context)).also { simpleCache = it }
            } catch (_: Exception) { null }
        }
    }

    private fun recreateDiskCache() { releaseSimpleCache(); ensureSimpleCache() }

    private fun releaseSimpleCache() {
        try { simpleCache?.release() } catch (_: Exception) {}
        simpleCache = null
    }

    /**
     * Create CacheDataSource.Factory — takes the upstream factory as parameter.
     * This ensures cache wraps the actual HTTP data source.
     */
    fun createCacheDataSourceFactory(upstreamFactory: DataSource.Factory): CacheDataSource.Factory? {
        val cache = ensureSimpleCache() ?: return null
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /** Overload that creates its own HTTP factory (for simple cases) */
    fun createCacheDataSourceFactory(): CacheDataSource.Factory? {
        val upstream = DefaultHttpDataSource.Factory()
            .setUserAgent("MeowPlay/1.0")
            .setAllowCrossProtocolRedirects(true)
        return createCacheDataSourceFactory(upstream)
    }

    /** Create DefaultLoadControl from current settings */
    fun createLoadControl(): DefaultLoadControl {
        val builder = DefaultLoadControl.Builder()

        val ramBytes = getRamBufferSizeBytes()
        if (ramBytes > 0) {
            builder.setTargetBufferBytes(if (ramBytes > Int.MAX_VALUE.toLong()) Int.MAX_VALUE else ramBytes.toInt())
        }

        val bufferMs = getBufferLengthMs()
        val maxBufferMs = if (bufferMs > 0) {
            if (bufferMs > Int.MAX_VALUE.toLong()) Int.MAX_VALUE else bufferMs.toInt()
        } else DefaultLoadControl.DEFAULT_MAX_BUFFER_MS

        builder.setBufferDurationsMs(
            DefaultLoadControl.DEFAULT_MIN_BUFFER_MS, maxBufferMs,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
        )

        val backMs = getBackBufferMs()
        builder.setBackBuffer(if (backMs > 0) backMs else 30000, true)

        return builder.build()
    }

    // ─── Cache Info ──────────────────────────────────────────────────────────

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

    fun clearVideoCache() { releaseSimpleCache(); File(context.cacheDir, "exoplayer").deleteRecursively(); ensureSimpleCache() }
    fun clearAllCache() { releaseSimpleCache(); context.cacheDir.deleteRecursively(); context.cacheDir.mkdirs(); ensureSimpleCache() }
    fun release() { releaseSimpleCache() }

    private fun calculateDirSize(dir: File): Long {
        var size = 0L
        if (dir.exists()) dir.walkTopDown().forEach { if (it.isFile) size += it.length() }
        return size
    }
}
