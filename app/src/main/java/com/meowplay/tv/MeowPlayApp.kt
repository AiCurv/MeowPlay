package com.meowplay.tv

import android.app.Application
import com.meowplay.tv.data.AppDatabase
import com.meowplay.tv.player.CacheManager
import com.meowplay.tv.remote.RemoteServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MeowPlayApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Lazy-initialized singletons
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val cacheManager: CacheManager by lazy { CacheManager.getInstance(this) }

    // Future remote server (dormant, activated from settings)
    var remoteServer: RemoteServer? = null

    override fun onCreate() {
        super.onCreate()

        // Initialize cache manager
        cacheManager.initialize()

        // Start remote server if previously enabled (for future remote app)
        appScope.launch {
            val prefs = getSharedPreferences("meowplay_prefs", MODE_PRIVATE)
            if (prefs.getBoolean("remote_enabled", false)) {
                startRemoteServer()
            }
        }
    }

    fun startRemoteServer() {
        if (remoteServer == null) {
            remoteServer = RemoteServer(this, 8080)
            try {
                remoteServer?.start()
            } catch (e: Exception) {
                remoteServer = null
            }
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
