package com.meowplay.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for history data. Handles all CRUD operations
 * and provides clean APIs for the UI layer.
 */
class HistoryRepository private constructor(context: Context) {

    private val historyDao: HistoryDao = AppDatabase.getInstance(context).historyDao()

    /** Observe all history entries (sorted by last played) */
    fun observeAllHistory() = historyDao.getAllHistory()

    /** Search history by title or URL */
    fun searchHistory(query: String) = historyDao.searchHistory(query)

    /** Get all history synchronously */
    suspend fun getAllHistory() = historyDao.getAllHistorySync()

    /** Add or update a history entry. If URL exists, increments play count. */
    suspend fun addOrUpdateEntry(
        url: String,
        title: String? = null,
        sourceApp: String? = null,
        mimeType: String? = null,
        thumbnailUrl: String? = null
    ) {
        withContext(Dispatchers.IO) {
            val existing = historyDao.getByUrl(url)
            if (existing != null) {
                // Update existing entry
                val updated = existing.copy(
                    title = title ?: existing.title,
                    sourceApp = sourceApp ?: existing.sourceApp,
                    mimeType = mimeType ?: existing.mimeType,
                    thumbnailUrl = thumbnailUrl ?: existing.thumbnailUrl,
                    lastPlayedAt = System.currentTimeMillis(),
                    playCount = existing.playCount + 1
                )
                historyDao.update(updated)
            } else {
                // Create new entry
                val entry = HistoryEntry(
                    url = url,
                    title = title ?: extractTitleFromUrl(url),
                    sourceApp = sourceApp,
                    mimeType = mimeType,
                    thumbnailUrl = thumbnailUrl
                )
                historyDao.insert(entry)
            }
        }
    }

    /** Update playback position for resume feature */
    suspend fun updatePosition(url: String, position: Long) {
        withContext(Dispatchers.IO) {
            historyDao.updatePosition(url, position)
        }
    }

    /** Update duration when known */
    suspend fun updateDuration(url: String, duration: Long) {
        withContext(Dispatchers.IO) {
            val existing = historyDao.getByUrl(url)
            if (existing != null) {
                historyDao.update(existing.copy(duration = duration))
            }
        }
    }

    /** Delete a single history entry */
    suspend fun deleteEntry(entry: HistoryEntry) {
        withContext(Dispatchers.IO) {
            historyDao.delete(entry)
        }
    }

    /** Delete entry by ID */
    suspend fun deleteById(id: Long) {
        withContext(Dispatchers.IO) {
            historyDao.deleteById(id)
        }
    }

    /** Clear all history */
    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            historyDao.deleteAll()
        }
    }

    /** Get entry by URL for resume playback */
    suspend fun getByUrl(url: String): HistoryEntry? {
        return withContext(Dispatchers.IO) {
            historyDao.getByUrl(url)
        }
    }

    private fun extractTitleFromUrl(url: String): String {
        return try {
            val path = url.substringAfterLast("/")
            val withoutExt = path.substringBeforeLast(".")
            withoutExt.replace("_", " ").replace("-", " ").take(50)
        } catch (e: Exception) {
            url.take(50)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: HistoryRepository? = null

        fun getInstance(context: Context): HistoryRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = HistoryRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
