package com.meowplay.tv.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "history", indices = [Index(value = ["url"], unique = true), Index(value = ["lastPlayedAt"])])
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String? = null,
    val duration: Long = 0,
    val lastPosition: Long = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long = System.currentTimeMillis(),
    val sourceApp: String? = null,
    val mimeType: String? = null,
    val playCount: Int = 1
)
