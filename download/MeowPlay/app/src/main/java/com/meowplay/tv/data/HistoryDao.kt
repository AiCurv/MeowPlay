package com.meowplay.tv.data

import androidx.lifecycle.LiveData
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY lastPlayedAt DESC")
    fun getAllHistory(): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): HistoryEntry?

    @Query("SELECT * FROM history WHERE title LIKE '%' || :q || '%' OR url LIKE '%' || :q || '%' ORDER BY lastPlayedAt DESC")
    fun searchHistory(q: String): Flow<List<HistoryEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HistoryEntry): Long

    @Update
    suspend fun update(entry: HistoryEntry)

    @Delete
    suspend fun delete(entry: HistoryEntry)

    @Query("DELETE FROM history")
    suspend fun deleteAll()

    @Query("UPDATE history SET lastPosition = :position, lastPlayedAt = :ts WHERE url = :url")
    suspend fun updatePosition(url: String, position: Long, ts: Long = System.currentTimeMillis())

    @Query("UPDATE history SET playCount = playCount + 1, lastPlayedAt = :ts WHERE url = :url")
    suspend fun incrementPlayCount(url: String, ts: Long = System.currentTimeMillis())
}
