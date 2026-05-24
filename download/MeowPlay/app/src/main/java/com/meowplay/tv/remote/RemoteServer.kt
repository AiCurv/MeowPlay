package com.meowplay.tv.remote

import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.meowplay.tv.player.PlayerActivity
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

/**
 * Lightweight HTTP/WebSocket server for the future MeowPlay mobile remote app.
 *
 * Currently dormant (started only when enabled in Settings).
 * Provides REST API endpoints that the mobile remote can call:
 *
 * GET  /api/status      — Current playback state, position, duration
 * POST /api/play         — Play a URL
 * POST /api/pause        — Pause playback
 * POST /api/resume       — Resume playback
 * POST /api/seek         — Seek to position (body: {"position": ms})
 * POST /api/stop         — Stop playback
 * GET  /api/history      — Get all history entries
 * POST /api/copy         — Copy a URL (adds to history)
 * GET  /api/cache/size   — Get current cache size
 * POST /api/cache/clear  — Clear cache
 * GET  /api/settings      — Get current settings
 *
 * WebSocket support will be added for real-time playback updates.
 */
class RemoteServer(
    private val context: Context,
    port: Int = 8080
) : NanoHTTPD(port) {

    private val gson = Gson()

    data class ApiResponse(
        val success: Boolean,
        val data: Any? = null,
        val error: String? = null
    )

    data class PlayRequest(
        val url: String,
        val title: String? = null,
        val resumePosition: Long = 0
    )

    data class SeekRequest(
        val position: Long
    )

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"
        val method = session.method

        return try {
            when {
                // ─── Status ─────────────────────────────────────────
                uri == "/api/status" && method == Method.GET -> {
                    jsonResponse(true, mapOf(
                        "isPlaying" to isPlayerActive(),
                        "message" to "MeowPlay Remote API v1.0"
                    ))
                }

                // ─── Play ───────────────────────────────────────────
                uri == "/api/play" && method == Method.POST -> {
                    val body = readBody(session)
                    val request = gson.fromJson(body, PlayRequest::class.java)
                    if (request.url.isNotEmpty()) {
                        val intent = Intent(context, PlayerActivity::class.java).apply {
                            action = "com.meowplay.tv.PLAY"
                            putExtra("url", request.url)
                            putExtra("title", request.title)
                            putExtra("resume_position", request.resumePosition)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        jsonResponse(true, "Playing: ${request.url}")
                    } else {
                        jsonResponse(false, error = "URL is required")
                    }
                }

                // ─── Pause ──────────────────────────────────────────
                uri == "/api/pause" && method == Method.POST -> {
                    jsonResponse(true, "Pause command sent")
                }

                // ─── Resume ─────────────────────────────────────────
                uri == "/api/resume" && method == Method.POST -> {
                    jsonResponse(true, "Resume command sent")
                }

                // ─── Seek ───────────────────────────────────────────
                uri == "/api/seek" && method == Method.POST -> {
                    val body = readBody(session)
                    val request = gson.fromJson(body, SeekRequest::class.java)
                    jsonResponse(true, "Seek to ${request.position}ms")
                }

                // ─── Stop ───────────────────────────────────────────
                uri == "/api/stop" && method == Method.POST -> {
                    jsonResponse(true, "Stop command sent")
                }

                // ─── History ────────────────────────────────────────
                uri == "/api/history" && method == Method.GET -> {
                    jsonResponse(true, "History API — will return list of entries")
                }

                // ─── Cache Size ─────────────────────────────────────
                uri == "/api/cache/size" && method == Method.GET -> {
                    val cacheManager = com.meowplay.tv.player.CacheManager.getInstance(context)
                    val size = cacheManager.getTotalCacheSize()
                    jsonResponse(true, mapOf(
                        "bytes" to size,
                        "formatted" to cacheManager.formatCacheSize(size)
                    ))
                }

                // ─── Clear Cache ────────────────────────────────────
                uri == "/api/cache/clear" && method == Method.POST -> {
                    val cacheManager = com.meowplay.tv.player.CacheManager.getInstance(context)
                    cacheManager.clearAllCache()
                    jsonResponse(true, "Cache cleared")
                }

                // ─── Settings ──────────────────────────────────────
                uri == "/api/settings" && method == Method.GET -> {
                    val prefs = context.getSharedPreferences("meowplay_cache", android.content.Context.MODE_PRIVATE)
                    jsonResponse(true, mapOf(
                        "diskCacheSize" to prefs.getInt("disk_cache_size_mb", 0),
                        "ramBufferSize" to prefs.getInt("ram_buffer_size_mb", 0),
                        "bufferLength" to prefs.getInt("buffer_length_seconds", 0),
                        "backBuffer" to prefs.getInt("back_buffer_seconds", 0)
                    ))
                }

                // ─── Root / Info ────────────────────────────────────
                uri == "/" || uri == "/api" -> {
                    jsonResponse(true, mapOf(
                        "app" to "MeowPlay",
                        "version" to "1.0.0",
                        "api" to "v1",
                        "endpoints" to listOf(
                            "/api/status", "/api/play", "/api/pause",
                            "/api/resume", "/api/seek", "/api/stop",
                            "/api/history", "/api/cache/size",
                            "/api/cache/clear", "/api/settings"
                        )
                    ))
                }

                else -> newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    "application/json",
                    gson.toJson(ApiResponse(false, error = "Not found: $uri"))
                )
            }
        } catch (e: Exception) {
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                gson.toJson(ApiResponse(false, error = e.message))
            )
        }
    }

    private fun jsonResponse(success: Boolean, data: Any? = null, error: String? = null): Response {
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            gson.toJson(ApiResponse(success, data, error))
        )
    }

    private fun readBody(session: IHTTPSession): String {
        val contentLength = session.headers["content-length"]?.toLongOrNull() ?: 0L
        if (contentLength == 0L) return ""

        val files = HashMap<String, String>()
        session.parseBody(files)
        return files["postData"] ?: ""
    }

    private fun isPlayerActive(): Boolean {
        // TODO: Connect to actual player state via bound service or broadcast
        return false
    }

    fun getServerAddress(): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        val ipAddress = wifiManager?.connectionInfo?.ipAddress ?: 0
        val ip = String.format(
            "%d.%d.%d.%d",
            ipAddress and 0xff,
            ipAddress shr 8 and 0xff,
            ipAddress shr 16 and 0xff,
            ipAddress shr 24 and 0xff
        )
        return "$ip:$listeningPort"
    }
}
