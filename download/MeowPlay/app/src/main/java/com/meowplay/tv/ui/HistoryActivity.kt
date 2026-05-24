package com.meowplay.tv.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.meowplay.tv.R
import com.meowplay.tv.data.HistoryEntry
import com.meowplay.tv.data.HistoryRepository
import com.meowplay.tv.player.PlayerActivity
import kotlinx.coroutines.launch

/**
 * History screen — TV-optimized with D-pad navigation.
 * Shows all previously played links with:
 * - Click to play with resume
 * - Copy button
 * - Long-press for "Open with"
 * - Search
 */
class HistoryActivity : FragmentActivity() {

    private lateinit var searchInput: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var clearAllBtn: Button
    private lateinit var backBtn: Button
    private lateinit var adapter: HistoryAdapter
    private lateinit var historyRepository: HistoryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        historyRepository = HistoryRepository.getInstance(this)

        searchInput = findViewById(R.id.search_input)
        recyclerView = findViewById(R.id.history_recycler)
        emptyView = findViewById(R.id.empty_view)
        clearAllBtn = findViewById(R.id.clear_all_btn)
        backBtn = findViewById(R.id.back_btn)

        adapter = HistoryAdapter(
            onPlay = { entry -> playWithResume(entry) },
            onCopy = { entry -> copyToClipboard(entry.url) },
            onOpenWith = { entry -> openWith(entry) },
            onDelete = { entry -> deleteEntry(entry) }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // D-pad focus chain
        backBtn.nextFocusDownId = R.id.search_input
        searchInput.nextFocusDownId = R.id.history_recycler
        clearAllBtn.nextFocusDownId = R.id.history_recycler

        // Back button
        backBtn.setOnClickListener { finish() }

        // Clear all
        clearAllBtn.setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("Clear All History?")
                .setMessage("This will remove all links from your history.")
                .setPositiveButton("Clear All") { _, _ ->
                    lifecycleScope.launch { historyRepository.clearAll() }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Search
        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString()?.trim() ?: ""
                if (q.isEmpty()) {
                    historyRepository.observeAll().collectSafely { updateList(it) }
                } else {
                    historyRepository.search(q).collectSafely { updateList(it) }
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Observe history
        lifecycleScope.launch {
            historyRepository.observeAll().collect { entries ->
                updateList(entries)
            }
        }
    }

    private fun updateList(entries: List<HistoryEntry>) {
        if (entries.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            adapter.submitList(entries)
        }
    }

    // Extension to safely collect Flow
    private fun <T> kotlinx.coroutines.flow.Flow<T>.collectSafely(action: (T) -> Unit) {
        lifecycleScope.launch { collect { action(it) } }
    }

    private fun playWithResume(entry: HistoryEntry) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            action = "com.meowplay.tv.PLAY"
            putExtra("url", entry.url)
            putExtra("title", entry.title)
            putExtra("resume_position", entry.lastPosition)
        }
        startActivity(intent)
    }

    private fun copyToClipboard(url: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("MeowPlay URL", url))
        Toast.makeText(this, "Link copied!", Toast.LENGTH_SHORT).show()
    }

    private fun openWith(entry: HistoryEntry) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse(entry.url)
            type = entry.mimeType ?: "video/*"
        }
        val chooser = Intent.createChooser(intent, "Open with...")
        if (chooser.resolveActivity(packageManager) != null) startActivity(chooser)
        else Toast.makeText(this, "No other player found", Toast.LENGTH_SHORT).show()
    }

    private fun deleteEntry(entry: HistoryEntry) {
        lifecycleScope.launch { historyRepository.delete(entry) }
    }

    // D-pad key handling
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true }
        return super.onKeyDown(keyCode, event)
    }

    // ─── Adapter ──────────────────────────────────────────────────────────────

    class HistoryAdapter(
        private val onPlay: (HistoryEntry) -> Unit,
        private val onCopy: (HistoryEntry) -> Unit,
        private val onOpenWith: (HistoryEntry) -> Unit,
        private val onDelete: (HistoryEntry) -> Unit
    ) : RecyclerView.Adapter<HistoryAdapter.VH>() {

        private var items: List<HistoryEntry> = emptyList()

        fun submitList(newItems: List<HistoryEntry>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            private val titleText: TextView = view.findViewById(R.id.text_title)
            private val urlText: TextView = view.findViewById(R.id.text_url)
            private val timestampText: TextView = view.findViewById(R.id.text_timestamp)
            private val resumeText: TextView = view.findViewById(R.id.text_resume)
            private val copyBtn: View = view.findViewById(R.id.btn_copy)
            private val deleteBtn: View = view.findViewById(R.id.btn_delete)

            fun bind(entry: HistoryEntry) {
                titleText.text = entry.title ?: "Untitled"
                urlText.text = if (entry.url.length > 80) entry.url.substring(0, 77) + "..." else entry.url
                timestampText.text = formatTime(entry.lastPlayedAt)

                if (entry.lastPosition > 0) {
                    resumeText.text = "Resume at ${formatDuration(entry.lastPosition)}"
                    resumeText.visibility = View.VISIBLE
                } else {
                    resumeText.visibility = View.GONE
                }

                // D-pad focus
                itemView.isFocusable = true
                itemView.setOnFocusChangeListener { v, hasFocus ->
                    v.setBackgroundColor(if (hasFocus) 0x1A237E.toInt() else 0x1E1E1E.toInt())
                }

                // Click = Play
                itemView.setOnClickListener { onPlay(entry) }

                // Long click = Open with
                itemView.setOnLongClickListener { onOpenWith(entry); true }

                // Copy button
                copyBtn.setOnClickListener { onCopy(entry) }
                copyBtn.isFocusable = true

                // Delete
                deleteBtn.setOnClickListener { onDelete(entry) }
                deleteBtn.isFocusable = true
            }

            private fun formatTime(ts: Long): String {
                val diff = System.currentTimeMillis() - ts
                return when {
                    diff < 60000 -> "Just now"
                    diff < 3600000 -> "${diff / 60000} min ago"
                    diff < 86400000 -> "${diff / 3600000}h ago"
                    else -> java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault()).format(java.util.Date(ts))
                }
            }

            private fun formatDuration(ms: Long): String {
                val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
                return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
            }
        }
    }
}
