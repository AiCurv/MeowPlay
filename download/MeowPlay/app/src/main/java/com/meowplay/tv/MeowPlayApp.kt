package com.meowplay.tv

import android.app.Application
import com.meowplay.tv.data.AppDatabase
import com.meowplay.tv.player.CacheManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MeowPlayApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val cacheManager: CacheManager by lazy { CacheManager.getInstance(this) }

    var remoteServer: com.meowplay.tv.remote.RemoteServer? = null

    override fun onCreate() {
        super.onCreate()
        cacheManager.initialize()

        appScope.launch {
            val prefs = getSharedPreferences("meowplay_prefs", MODE_PRIVATE)
            if (prefs.getBoolean("remote_enabled", false)) {
                startRemoteServer()
            }
        }
    }

    fun startRemoteServer() {
        if (remoteServer == null) {
            remoteServer = com.meowplay.tv.remote.RemoteServer(this, 8080)
            try { remoteServer?.start() } catch (_: Exception) { remoteServer = null }
        }
    }

    fun stopRemoteServer() {
        remoteServer?.stop()
        remoteServer = null
    }

    override fun onTerminate() {
        super.onTerminate()
        cacheManager.release()
        stopRemoteServer()
    }
}
