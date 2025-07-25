package com.lecturo.lecturo.ui.teaching

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.model.TeachingRule
import com.lecturo.lecturo.data.repository.TeachingRepository
import com.lecturo.lecturo.databinding.ActivityTeachingBinding
import com.lecturo.lecturo.ui.teaching.class_schedule.ClassScheduleActivity

class TeachingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeachingBinding
    private lateinit var teachingAdapter: TeachingRuleAdapter
    private var allTeachingRules: List<TeachingRule> = emptyList()

    // --- PERBAIKAN DI SINI ---
    private val viewModel: TeachingViewModel by viewModels {
        val database = AppDatabase.getDatabase(this)
        // Hapus eventDao dari sini agar cocok dengan konstruktor Repository
        val repository = TeachingRepository(
            database.teachingRuleDao(),
            database.calendarEntryDao()
        )
        // Tambahkan 'application' sebagai parameter kedua untuk Factory
        TeachingViewModelFactory(repository, application)
    }

    private val addTeachingLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Data akan refresh otomatis melalui LiveData
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTeachingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupTabs()
        setupFab()
        observeViewModel()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.teachingToolbar)
        supportActionBar?.title = "Aturan Jadwal Mengajar"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupRecyclerView() {
        teachingAdapter = TeachingRuleAdapter(
            onDeleteClick = { rule ->
                showDeleteConfirmation(rule)
            },
            onItemClick = { rule ->
                val intent = Intent(this, AddTeachingActivity::class.java)
                intent.putExtra("rule_id", rule.id)
                addTeachingLauncher.launch(intent)
            }
        )

        binding.recyclerViewTeaching.apply {
            layoutManager = LinearLayoutManager(this@TeachingActivity)
            adapter = teachingAdapter
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
        val filteredRules = when (day) {
            "Semua" -> allTeachingRules
            else -> allTeachingRules.filter { it.dayOfWeek == day }
        }
        teachingAdapter.submitList(filteredRules)
        updateEmptyState(filteredRules.isEmpty())
    }

    private fun setupFab() {
        binding.fabAddTeaching.setOnClickListener {
            val intent = Intent(this, AddTeachingActivity::class.java)
            addTeachingLauncher.launch(intent)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.teaching_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_view_class_schedule -> {
                val intent = Intent(this, ClassScheduleActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun observeViewModel() {
        viewModel.teachingRules.observe(this) { rules ->
            allTeachingRules = rules
            val selectedTab = binding.tabLayoutDays.getTabAt(binding.tabLayoutDays.selectedTabPosition)
            filterByDay(selectedTab?.text.toString())
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.layoutEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerViewTeaching.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun showDeleteConfirmation(rule: TeachingRule) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Hapus Aturan Jadwal")
            .setMessage("Yakin ingin menghapus aturan \"${rule.courseName} - ${rule.className}\"?\n\nPerhatian: Ini akan menghapus semua jadwal kelas terkait dari kalender.")
            .setPositiveButton("Hapus") { _, _ ->
                viewModel.deleteTeachingRule(rule.id)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
