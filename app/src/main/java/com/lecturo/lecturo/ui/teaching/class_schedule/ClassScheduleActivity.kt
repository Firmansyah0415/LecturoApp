package com.lecturo.lecturo.ui.teaching.class_schedule

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.repository.CalendarRepository
import com.lecturo.lecturo.databinding.ActivityClassScheduleBinding
import com.lecturo.lecturo.ui.teaching.AddTeachingActivity
import com.lecturo.lecturo.viewmodel.teaching.ClassScheduleViewModel
import com.lecturo.lecturo.viewmodel.teaching.ClassScheduleViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

class ClassScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityClassScheduleBinding
    private lateinit var classScheduleAdapter: ClassScheduleAdapter
    private var allClassEntries: List<DisplayableClassSchedule> = emptyList()

    private val viewModel: ClassScheduleViewModel by viewModels {
        val database = AppDatabase.getDatabase(this)
        val repository = CalendarRepository(database.calendarEntryDao())
        ClassScheduleViewModelFactory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClassScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // bikin status bar transparan sekali untuk semua activity
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 1. Cek apakah aplikasi sedang di Mode Gelap
        val isNightMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

        // 2. atur warna status bar
        window.statusBarColor = getColor(R.color.colorPrimary)

        // 3. atur warna teks/icon status bar
        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = !isNightMode

        // otomatis kasih padding top di root view sesuai status bar
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(
                view.paddingLeft,
                statusBarInsets.top,
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }

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
        classScheduleAdapter = ClassScheduleAdapter { item ->
            val intent = Intent(this, AddTeachingActivity::class.java)
            intent.putExtra("rule_id", item.entry.sourceFeatureId)
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
                allClassEntries.filter { it.entry.date == today }
            }
            "Besok" -> {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
                val tomorrow = dateFormat.format(calendar.time)
                allClassEntries.filter { it.entry.date == tomorrow }
            }
            "Minggu Ini" -> {
                // Set kalender ke hari Senin minggu ini
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                val startOfWeek = calendar.time
                // Set kalender ke hari Minggu minggu ini
                calendar.add(Calendar.DAY_OF_WEEK, 6)
                val endOfWeek = calendar.time

                allClassEntries.filter { item ->
                    try {
                        val entryDate = dateFormat.parse(item.entry.date)
                        // Pastikan tanggal entri berada di antara awal dan akhir minggu
                        entryDate != null && !entryDate.before(startOfWeek) && !entryDate.after(endOfWeek)
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
        viewModel.displayableSchedules.observe(this) { displayableItems ->
            allClassEntries = displayableItems
            val selectedTab = binding.tabLayoutDays.getTabAt(binding.tabLayoutDays.selectedTabPosition)
            filterByDay(selectedTab?.text.toString())
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
