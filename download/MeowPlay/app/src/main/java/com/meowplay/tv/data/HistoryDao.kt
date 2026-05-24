package com.meowplay.tv.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history ORDER BY lastPlayedAt DESC")
    fun getAllHistory(): LiveData<List<HistoryEntry>>

    @Query("SELECT * FROM history ORDER BY lastPlayedAt DESC")
    suspend fun getAllHistorySync(): List<HistoryEntry>

    @Query("SELECT * FROM history WHERE id = :id")
    suspend fun getById(id: Long): HistoryEntry?

    @Query("SELECT * FROM history WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): HistoryEntry?

    @Query("SELECT * FROM history WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY lastPlayedAt DESC")
    fun searchHistory(query: String): LiveData<List<HistoryEntry>>

    @Query("SELECT * FROM history WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY lastPlayedAt DESC")
    suspend fun searchHistorySync(query: String): List<HistoryEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HistoryEntry): Long

    @Update
    suspend fun update(entry: HistoryEntry)

    @Delete
    suspend fun delete(entry: HistoryEntry)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM history")
    suspend fun getCount(): Int

    @Query("UPDATE history SET lastPosition = :position, lastPlayedAt = :timestamp WHERE url = :url")
    suspend fun updatePosition(url: String, position: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE history SET playCount = playCount + 1, lastPlayedAt = :timestamp WHERE url = :url")
    suspend fun incrementPlayCount(url: String, timestamp: Long = System.currentTimeMillis())
}
