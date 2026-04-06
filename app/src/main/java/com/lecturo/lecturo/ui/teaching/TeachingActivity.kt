package com.lecturo.lecturo.ui.teaching

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.model.TeachingRule
import com.lecturo.lecturo.data.remote.RetrofitClient
import com.lecturo.lecturo.data.repository.TeachingRepository
import com.lecturo.lecturo.databinding.ActivityTeachingBinding
import com.lecturo.lecturo.ui.teaching.class_schedule.ClassScheduleActivity
import com.lecturo.lecturo.viewmodel.teaching.TeachingViewModel
import com.lecturo.lecturo.viewmodel.teaching.TeachingViewModelFactory

class TeachingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeachingBinding
    private lateinit var teachingAdapter: TeachingRuleAdapter
    private var allTeachingRules: List<TeachingRule> = emptyList()

    private val viewModel: TeachingViewModel by viewModels {
        val database = AppDatabase.getDatabase(this)

        // PANGGIL DI SINI
        val apiService = RetrofitClient.instance

        val repository = TeachingRepository(
            database.teachingRuleDao(),
            database.calendarEntryDao(),
            applicationContext
        )
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

        // bikin status bar transparan sekali untuk semua activity
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // atur warna status bar
        window.statusBarColor = getColor(R.color.colorPrimary)

        // atur warna teks/icon status bar → true = icon gelap (hitam), false = icon terang (putih)
        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = true

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

        // [SOLUSI PRO: Mendorong FAB ke atas Navigasi Sistem]
        ViewCompat.setOnApplyWindowInsetsListener(binding.fabAddTeaching) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Konversi margin dasar 20dp dari XML ke satuan Pixel
            val baseMarginPx = (20 * resources.displayMetrics.density).toInt()

            // Update HANYA margin bawahnya, ditambahkan dengan tinggi navigasi sistem
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = systemBars.bottom + baseMarginPx
                rightMargin = baseMarginPx // Sesuaikan juga margin kanan agar presisi
            }

            insets
        }

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
                intent.putExtra("rule_id", rule.localId)
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
            .setMessage("Yakin ingin menghapus aturan \"${rule.courseName} - ${rule.classCode}\"?\n\nPerhatian: Ini akan menghapus semua jadwal kelas terkait dari kalender.")
            .setPositiveButton("Hapus") { _, _ ->
                viewModel.deleteTeachingRule(rule.localId)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
