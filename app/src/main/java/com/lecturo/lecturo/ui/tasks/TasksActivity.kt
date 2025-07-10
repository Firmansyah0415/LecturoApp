package com.lecturo.lecturo.ui.tasks

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.lecturo.lecturo.AddScheduleActivity
import com.lecturo.lecturo.CameraOCRActivity
import com.lecturo.lecturo.R
import com.lecturo.lecturo.Schedule
import com.lecturo.lecturo.databinding.ActivityTasksBinding
import com.lecturo.lecturo.db.AppDatabase

class TasksActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTasksBinding

    private val viewModel: TasksViewModel by viewModels {
        getViewModelFactory()
    }

    fun getViewModelFactory(): TasksViewModelFactory {
        val dao = AppDatabase.getDatabase(applicationContext).scheduleDao()
        val repository = ScheduleRepository(dao)
        return TasksViewModelFactory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTasksBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupViewPager()
        setupFabActions()
        observeTabTitles()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Jadwal Tugas"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish() // Kembali ke activity sebelumnya
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.tasks_menu, menu)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as androidx.appcompat.widget.SearchView

        searchView.queryHint = "Cari jadwal..."

        searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                // Tidak perlu aksi khusus saat submit, karena pencarian sudah live
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // Kirim teks pencarian ke ViewModel setiap kali pengguna mengetik
                viewModel.setSearchQuery(newText.orEmpty())
                return true
            }
        })

        // Reset pencarian saat search view ditutup
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true
            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                viewModel.setSearchQuery("")
                return true
            }
        })

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            // TODO: Tambahkan logika untuk setiap item menu filter dan lainnya
            R.id.filter_all -> {
                // Contoh: viewModel.setFilter(FilterType.ALL)
                true
            }
            R.id.filter_today -> {
                // Contoh: viewModel.setFilter(FilterType.TODAY)
                true
            }
            R.id.action_export -> {
                // Logika untuk export
                true
            }
            R.id.action_settings -> {
                // Logika untuk pengaturan
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupViewPager() {
        val pagerAdapter = SchedulePagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, _ ->
            // Judul akan diupdate oleh observer
        }.attach()
    }

    fun handleScheduleAction(schedule: Schedule, action: String) {
        when (action) {
            "delete" -> showDeleteConfirmation(schedule)
            "complete" -> viewModel.updateScheduleCompletedStatus(schedule.id, true)
            "uncomplete" -> viewModel.updateScheduleCompletedStatus(schedule.id, false)
        }
    }

    private fun setupFabActions() {
        binding.fabAddSchedule.setOnClickListener {
            startActivity(Intent(this, AddScheduleActivity::class.java))
        }

        binding.fabCamera.setOnClickListener {
            startActivity(Intent(this, CameraOCRActivity::class.java))
        }
    }

    private fun observeTabTitles() {
        viewModel.pendingSchedules.observe(this) { pending ->
            binding.tabLayout.getTabAt(0)?.text = "Jadwal (${pending.size})"
        }

        viewModel.completedSchedules.observe(this) { completed ->
            binding.tabLayout.getTabAt(1)?.text = "Selesai (${completed.size})"
        }
    }

    private fun showDeleteConfirmation(schedule: Schedule) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Hapus Jadwal")
            .setMessage("Yakin ingin menghapus jadwal \"${schedule.title}\"?")
            .setPositiveButton("Ya") { _, _ -> viewModel.deleteSchedule(schedule.id) }
            .setNegativeButton("Batal", null)
            .show()
    }
}