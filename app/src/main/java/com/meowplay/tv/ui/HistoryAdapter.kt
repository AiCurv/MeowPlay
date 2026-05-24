package com.meowplay.tv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.meowplay.tv.R
import com.meowplay.tv.data.HistoryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * RecyclerView adapter for the History list.
 *
 * Each item shows:
 * - Title (or URL if no title)
 * - URL (truncated)
 * - Last played timestamp
 * - Play count
 * - Resume indicator (if lastPosition > 0)
 * - Copy button (left side)
 * - Delete button
 *
 * Interactions:
 * - Click: Play with resume
 * - Long click: Open with (other player)
 * - Copy button: Copy URL to clipboard
 */
class HistoryAdapter(
    private val onCopyClick: (HistoryEntry) -> Unit,
    private val onPlayClick: (HistoryEntry) -> Unit,
    private val onLongClick: (HistoryEntry) -> Unit,
    private val onDeleteClick: (HistoryEntry) -> Unit
) : ListAdapter<HistoryEntry, HistoryAdapter.HistoryViewHolder>(HistoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val entry = getItem(position)
        holder.bind(entry)
    }

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val copyButton: ImageView = itemView.findViewById(R.id.btn_copy)
        private val titleText: TextView = itemView.findViewById(R.id.text_title)
        private val urlText: TextView = itemView.findViewById(R.id.text_url)
        private val timestampText: TextView = itemView.findViewById(R.id.text_timestamp)
        private val playCountText: TextView = itemView.findViewById(R.id.text_play_count)
        private val resumeIndicator: TextView = itemView.findViewById(R.id.text_resume)
        private val deleteButton: ImageView = itemView.findViewById(R.id.btn_delete)

        fun bind(entry: HistoryEntry) {
            // Title
            titleText.text = entry.title ?: "Untitled"

            // URL (truncated for display)
            urlText.text = if (entry.url.length > 80) {
                entry.url.substring(0, 77) + "..."
            } else {
                entry.url
            }

            // Timestamp
            timestampText.text = formatTimestamp(entry.lastPlayedAt)

            // Play count
            playCountText.text = if (entry.playCount > 1) {
                "Played ${entry.playCount}x"
            } else {
                ""
            }

            // Resume indicator
            if (entry.lastPosition > 0 && entry.duration > 0) {
                val progressPercent = (entry.lastPosition * 100) / entry.duration
                val resumeTime = formatDuration(entry.lastPosition)
                resumeIndicator.text = "Resume at $resumeTime ($progressPercent%)"
                resumeIndicator.visibility = View.VISIBLE
            } else if (entry.lastPosition > 0) {
                val resumeTime = formatDuration(entry.lastPosition)
                resumeIndicator.text = "Resume at $resumeTime"
                resumeIndicator.visibility = View.VISIBLE
            } else {
                resumeIndicator.visibility = View.GONE
            }

            // Copy button (left side)
            copyButton.setOnClickListener {
                onCopyClick(entry)
            }

            // Focus for D-pad navigation on TV
            copyButton.nextFocusRightId = R.id.text_title

            // Delete button
            deleteButton.setOnClickListener {
                onDeleteClick(entry)
            }

            // Click = Play with resume
            itemView.setOnClickListener {
                onPlayClick(entry)
            }

            // Long click = Open with
            itemView.setOnLongClickListener {
                onLongClick(entry)
                true
            }

            // Focus handling for TV D-pad
            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = true
        }

        private fun formatTimestamp(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp

            return when {
                diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
                diff < TimeUnit.HOURS.toMillis(1) -> {
                    val mins = TimeUnit.MILLISECONDS.toMinutes(diff)
                    "$mins min ago"
                }
                diff < TimeUnit.DAYS.toMillis(1) -> {
                    val hours = TimeUnit.MILLISECONDS.toHours(diff)
                    "$hours hour${if (hours > 1) "s" else ""} ago"
                }
                diff < TimeUnit.DAYS.toMillis(7) -> {
                    val days = TimeUnit.MILLISECONDS.toDays(diff)
                    "$days day${if (days > 1) "s" else ""} ago"
                }
                else -> {
                    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    sdf.format(Date(timestamp))
                }
            }
        }

        private fun formatDuration(ms: Long): String {
            val totalSeconds = ms / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return when {
                hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
                else -> String.format("%d:%02d", minutes, seconds)
            }
        }
    }

    class HistoryDiffCallback : DiffUtil.ItemCallback<HistoryEntry>() {
        override fun areItemsTheSame(oldItem: HistoryEntry, newItem: HistoryEntry): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: HistoryEntry, newItem: HistoryEntry): Boolean {
            return oldItem == newItem
        }
    }
}
