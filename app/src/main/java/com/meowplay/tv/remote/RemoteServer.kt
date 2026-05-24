package com.meowplay.tv.remote

import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.meowplay.tv.player.CacheManager
import com.meowplay.tv.player.PlayerActivity
import fi.iki.elonen.NanoHTTPD

class RemoteServer(private val context: Context, port: Int = 8080) : NanoHTTPD(port) {
    private val gson = Gson()

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"
        return try {
            when {
                uri == "/" || uri == "/api" -> jsonResponse(mapOf("app" to "MeowPlay", "version" to "1.0.0"))
                uri == "/api/status" -> jsonResponse(mapOf("isPlaying" to false))
                uri == "/api/play" && session.method == Method.POST -> {
                    val body = readBody(session)
                    val req = gson.fromJson(body, PlayRequest::class.java)
                    if (!req.url.isNullOrEmpty()) {
                        context.startActivity(Intent(context, PlayerActivity::class.java).apply {
                            action = "com.meowplay.tv.PLAY"
                            putExtra("url", req.url)
                            putExtra("title", req.title)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                        jsonResponse(mapOf("success" to true))
                    } else jsonResponse(mapOf("error" to "URL required"), 400)
                }
                uri == "/api/cache/size" -> {
                    val cm = CacheManager.getInstance(context)
                    jsonResponse(mapOf("bytes" to cm.getTotalCacheSize(), "formatted" to cm.formatCacheSize(cm.getTotalCacheSize())))
                }
                uri == "/api/cache/clear" && session.method == Method.POST -> {
                    CacheManager.getInstance(context).clearAllCache()
                    jsonResponse(mapOf("success" to true))
                }
                else -> jsonResponse(mapOf("error" to "Not found"), 404)
            }
        } catch (e: Exception) { jsonResponse(mapOf("error" to e.message), 500) }
    }

    private fun jsonResponse(data: Any, status: Int = 200) =
        newFixedLengthResponse(Response.Status.lookup(status), "application/json", gson.toJson(data))

    private fun readBody(session: IHTTPSession): String {
        val files = HashMap<String, String>()
        session.parseBody(files)
        return files["postData"] ?: ""
    }

    data class PlayRequest(val url: String? = null, val title: String? = null)
}
