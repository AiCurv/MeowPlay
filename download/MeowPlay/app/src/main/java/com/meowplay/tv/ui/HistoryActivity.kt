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

        backBtn.setOnClickListener { finish() }

        clearAllBtn.setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("Clear All History?")
                .setPositiveButton("Clear All") { _, _ ->
                    lifecycleScope.launch { historyRepository.clearAll() }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString()?.trim() ?: ""
                lifecycleScope.launch {
                    val entries = if (q.isEmpty()) historyRepository.observeAll()
                    else historyRepository.search(q)
                    entries.collect { list -> runOnUiThread { updateList(list) } }
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        lifecycleScope.launch {
            historyRepository.observeAll().collect { entries ->
                runOnUiThread { updateList(entries) }
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true }
        return super.onKeyDown(keyCode, event)
    }

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

                // D-pad focus with glass effect
                itemView.isFocusable = true
                itemView.isClickable = true
                itemView.setOnFocusChangeListener { v, hasFocus ->
                    val bg = android.graphics.drawable.GradientDrawable().apply {
                        setColor(if (hasFocus) 0x2D2D5E else 0xFF1A1A2E.toInt())
                        cornerRadius = 12f
                        if (hasFocus) setStroke(2, 0x40FFFFFF.toInt())
                    }
                    v.background = bg
                }

                // Click = Play (D-pad center)
                itemView.setOnClickListener { onPlay(entry) }

                // Long click = Open with
                itemView.setOnLongClickListener { onOpenWith(entry); true }

                copyBtn.setOnClickListener { onCopy(entry) }
                copyBtn.isFocusable = true
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
