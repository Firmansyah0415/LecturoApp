package com.lecturo.lecturo.ui.teaching.class_schedule

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.model.CalendarEntry
import com.lecturo.lecturo.data.repository.CalendarRepository
import com.lecturo.lecturo.databinding.ActivityClassScheduleBinding
import com.lecturo.lecturo.ui.teaching.AddTeachingActivity
import java.text.SimpleDateFormat
import java.util.*

class ClassScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityClassScheduleBinding
    private lateinit var classScheduleAdapter: ClassScheduleAdapter
    private var allClassEntries: List<CalendarEntry> = emptyList()

    private val viewModel: ClassScheduleViewModel by viewModels {
        val database = AppDatabase.getDatabase(this)
        val repository = CalendarRepository(database.calendarEntryDao())
        ClassScheduleViewModelFactory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClassScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupTabs()
        observeViewModel()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Jadwal Kelas"
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun setupRecyclerView() {
        classScheduleAdapter = ClassScheduleAdapter { entry ->
            // Navigate ke detail teaching rule
            val intent = Intent(this, AddTeachingActivity::class.java)
            intent.putExtra("rule_id", entry.sourceFeatureId)
            startActivity(intent)
        }

        binding.recyclerViewClassSchedule.apply {
            layoutManager = LinearLayoutManager(this@ClassScheduleActivity)
            adapter = classScheduleAdapter
        }
    }

    private fun setupTabs() {
        binding.tabLayoutDays.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                filterByDay(tab?.text.toString())
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun filterByDay(day: String) {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        val filteredEntries = when (day) {
            "Semua" -> allClassEntries
            "Hari Ini" -> {
                val today = dateFormat.format(calendar.time)
                allClassEntries.filter { it.date == today }
            }
            "Besok" -> {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
                val tomorrow = dateFormat.format(calendar.time)
                allClassEntries.filter { it.date == tomorrow }
            }
            "Minggu Ini" -> {
                // Get start of week (Monday)
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                val startOfWeek = calendar.time

                // Get end of week (Sunday)
                calendar.add(Calendar.DAY_OF_WEEK, 6)
                val endOfWeek = calendar.time

                allClassEntries.filter { entry ->
                    try {
                        val entryDate = dateFormat.parse(entry.date)
                        entryDate != null && entryDate >= startOfWeek && entryDate <= endOfWeek
                    } catch (e: Exception) {
                        false
                    }
                }
            }
            else -> allClassEntries
        }

        classScheduleAdapter.submitList(filteredEntries)
        updateEmptyState(filteredEntries.isEmpty())
    }

    private fun observeViewModel() {
        viewModel.teachingEntries.observe(this) { entries ->
            allClassEntries = entries
            classScheduleAdapter.submitList(entries)
            updateEmptyState(entries.isEmpty())
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.layoutEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerViewClassSchedule.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
