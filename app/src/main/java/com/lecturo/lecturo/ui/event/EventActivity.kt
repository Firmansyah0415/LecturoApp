package com.lecturo.lecturo.ui.event

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.lecturo.lecturo.R
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.model.Event
import com.lecturo.lecturo.data.repository.EventRepository
import com.lecturo.lecturo.databinding.ActivityEventBinding

class EventActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEventBinding
    private lateinit var eventAdapter: EventAdapter

    private val viewModel: EventViewModel by viewModels {
        val database = AppDatabase.getDatabase(this)
        val repository = EventRepository(database.eventDao())
        EventViewModelFactory(repository)
    }

    private val addEventLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Event berhasil ditambah/diupdate, data akan otomatis refresh melalui LiveData
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupSearchView()
        setupFab()
        observeViewModel()
        setupFilterChips()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.eventToolbar)
        supportActionBar?.title = "Event Management"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupRecyclerView() {
        eventAdapter = EventAdapter(
            onCompletedChanged = { event, isCompleted ->
                viewModel.updateCompletedStatus(event.id, isCompleted)
            },
            onDeleteClick = { event ->
                showDeleteConfirmation(event)
            },
            onItemClick = { event ->
                // Navigate to AddEventActivity for editing
                val intent = Intent(this, AddEventActivity::class.java)
                intent.putExtra("event_id", event.id)
                addEventLauncher.launch(intent)
            }
        )

        binding.recyclerViewEvents.apply {
            layoutManager = LinearLayoutManager(this@EventActivity)
            adapter = eventAdapter
        }
    }

    private fun setupSearchView() {
        binding.editTextSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.setSearchQuery(s?.toString() ?: "")
            }
        })
    }

    private fun setupFab() {
        binding.fabAddEvent.setOnClickListener {
            val intent = Intent(this, AddEventActivity::class.java)
            addEventLauncher.launch(intent)
        }
    }

    private fun observeViewModel() {
        viewModel.filteredEvents.observe(this) { events ->
            eventAdapter.submitList(events)
            updateEmptyState(events.isEmpty())
        }

        viewModel.categories.observe(this) { categories ->
            setupCategoryChips(categories)
        }
    }

    private fun setupFilterChips() {
        binding.chipAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                viewModel.setCategoryFilter("")
            }
        }
    }

    private fun setupCategoryChips(categories: List<String>) {
        // Clear existing category chips (keep "Semua" chip)
        val chipGroup = binding.chipGroupFilters
        val childCount = chipGroup.childCount
        for (i in childCount - 1 downTo 1) {
            chipGroup.removeViewAt(i)
        }

        // Add category chips
        categories.forEach { category ->
            val chip = Chip(this)
            chip.text = category
            chip.isCheckable = true
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    binding.chipAll.isChecked = false
                    viewModel.setCategoryFilter(category)
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.layoutEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerViewEvents.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun showDeleteConfirmation(event: Event) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Hapus Event")
            .setMessage("Apakah Anda yakin ingin menghapus event \"${event.title}\"?")
            .setPositiveButton("Hapus") { _, _ ->
                viewModel.delete(event.id)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.event_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_clear_filters -> {
                viewModel.clearFilters()
                binding.editTextSearch.text?.clear()
                binding.chipAll.isChecked = true
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}