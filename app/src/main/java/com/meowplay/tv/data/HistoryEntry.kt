package com.meowplay.tv.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a single video link entry in the user's history.
 * Supports resume playback by storing the last position.
 */
@Entity(
    tableName = "history",
    indices = [
        Index(value = ["url"], unique = true),
        Index(value = ["lastPlayedAt"]),
        Index(value = ["title"])
    ]
)
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** The video URL (http, https, m3u8, magnet, etc.) */
    val url: String,

    /** Optional title extracted from the stream or entered by user */
    val title: String? = null,

    /** Duration of the video in milliseconds, 0 if unknown */
    val duration: Long = 0,

    /** Last playback position in milliseconds for resume */
    val lastPosition: Long = 0,

    /** Timestamp when this entry was first added */
    val addedAt: Long = System.currentTimeMillis(),

    /** Timestamp when this entry was last played */
    val lastPlayedAt: Long = System.currentTimeMillis(),

    /** Thumbnail URL if available */
    val thumbnailUrl: String? = null,

    /** Source app that sent the link (e.g., "Stremio", "CloudStream") */
    val sourceApp: String? = null,

    /** MIME type of the content */
    val mimeType: String? = null,

    /** Number of times this link has been played */
    val playCount: Int = 1
)
