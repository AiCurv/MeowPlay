package com.meowplay.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HistoryRepository private constructor(ctx: Context) {
    private val dao = AppDatabase.getInstance(ctx).historyDao()

    fun observeAll() = dao.getAllHistory()
    fun search(q: String) = dao.searchHistory(q)

    suspend fun addOrUpdateEntry(url: String, title: String? = null, sourceApp: String? = null, mimeType: String? = null) {
        withContext(Dispatchers.IO) {
            val existing = dao.getByUrl(url)
            if (existing != null) {
                dao.update(existing.copy(
                    title = title ?: existing.title,
                    sourceApp = sourceApp ?: existing.sourceApp,
                    mimeType = mimeType ?: existing.mimeType,
                    lastPlayedAt = System.currentTimeMillis(),
                    playCount = existing.playCount + 1
                ))
            } else {
                dao.insert(HistoryEntry(url = url, title = title ?: extractTitle(url), sourceApp = sourceApp, mimeType = mimeType))
            }
        }
    }

    suspend fun updatePosition(url: String, position: Long) = withContext(Dispatchers.IO) { dao.updatePosition(url, position) }
    suspend fun delete(entry: HistoryEntry) = withContext(Dispatchers.IO) { dao.delete(entry) }
    suspend fun clearAll() = withContext(Dispatchers.IO) { dao.deleteAll() }
    suspend fun getByUrl(url: String) = withContext(Dispatchers.IO) { dao.getByUrl(url) }

    private fun extractTitle(url: String): String = try {
        url.substringAfterLast("/").substringBeforeLast(".").replace("_", " ").replace("-", " ").take(50)
    } catch (_: Exception) { url.take(50) }

    companion object {
        @Volatile private var INSTANCE: HistoryRepository? = null
        fun getInstance(ctx: Context): HistoryRepository = INSTANCE ?: synchronized(this) {
            HistoryRepository(ctx.applicationContext).also { INSTANCE = it }
        }
    }
}
