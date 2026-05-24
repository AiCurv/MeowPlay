package com.meowplay.tv.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.meowplay.tv.R
import com.meowplay.tv.data.HistoryEntry
import com.meowplay.tv.data.HistoryRepository
import com.meowplay.tv.player.PlayerActivity
import kotlinx.coroutines.launch

/**
 * History tab — Shows all previously played video links.
 *
 * Features:
 * - Searchable list of all played links
 * - Copy button on each link
 * - Long press for "Open with" (open in another player like Kodi)
 * - Resume playback (auto-seeks to last position)
 * - Clear all history option
 */
class HistoryFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var searchInput: EditText
    private lateinit var emptyView: TextView
    private lateinit var clearAllButton: ImageView
    private lateinit var progressBar: ProgressBar

    private lateinit var historyRepository: HistoryRepository
    private lateinit var adapter: HistoryAdapter
    private var allEntries: List<HistoryEntry> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.history_recycler)
        searchInput = view.findViewById(R.id.search_input)
        emptyView = view.findViewById(R.id.empty_view)
        clearAllButton = view.findViewById(R.id.clear_all_button)
        progressBar = view.findViewById(R.id.progress_bar)

        historyRepository = HistoryRepository.getInstance(requireContext())

        // Setup RecyclerView
        adapter = HistoryAdapter(
            onCopyClick = { entry -> copyToClipboard(entry.url) },
            onPlayClick = { entry -> playWithResume(entry) },
            onLongClick = { entry -> showOpenWith(entry) },
            onDeleteClick = { entry -> deleteEntry(entry) }
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        // Search functionality
        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                filterHistory(query)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Clear all history
        clearAllButton.setOnClickListener {
            showClearAllConfirmation()
        }

        // Observe history
        historyRepository.observeAllHistory().observe(viewLifecycleOwner) { entries ->
            allEntries = entries
            updateList(entries)
        }
    }

    private fun updateList(entries: List<HistoryEntry>) {
        progressBar.isVisible = false
        if (entries.isEmpty()) {
            emptyView.isVisible = true
            recyclerView.isVisible = false
        } else {
            emptyView.isVisible = false
            recyclerView.isVisible = true
            adapter.submitList(entries)
        }
    }

    private fun filterHistory(query: String) {
        if (query.isEmpty()) {
            updateList(allEntries)
        } else {
            lifecycleScope.launch {
                val results = historyRepository.searchHistory(query).value ?: emptyList()
                updateList(results)
            }
        }
    }

    private fun playWithResume(entry: HistoryEntry) {
        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            action = "com.meowplay.tv.PLAY"
            putExtra("url", entry.url)
            putExtra("title", entry.title)
            putExtra("resume_position", entry.lastPosition)
        }
        startActivity(intent)
    }

    private fun copyToClipboard(url: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("MeowPlay URL", url)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), "Link copied!", Toast.LENGTH_SHORT).show()
    }

    private fun showOpenWith(entry: HistoryEntry) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse(entry.url)
            type = entry.mimeType ?: "video/*"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(intent, "Open with...")
        if (chooser.resolveActivity(requireContext().packageManager) != null) {
            startActivity(chooser)
        } else {
            Toast.makeText(requireContext(), "No other player found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteEntry(entry: HistoryEntry) {
        lifecycleScope.launch {
            historyRepository.deleteEntry(entry)
        }
    }

    private fun showClearAllConfirmation() {
        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setTitle("Clear All History?")
            .setMessage("This will remove all links from your history. This cannot be undone.")
            .setPositiveButton("Clear All") { _, _ ->
                lifecycleScope.launch {
                    historyRepository.clearAll()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
    }
}
