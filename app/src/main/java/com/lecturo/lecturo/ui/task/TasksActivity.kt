package com.lecturo.lecturo.ui.task

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView // Import yang benar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.model.Tasks
import com.lecturo.lecturo.data.repository.CalendarRepository
import com.lecturo.lecturo.data.repository.TasksRepository
import com.lecturo.lecturo.databinding.ActivityTasksBinding
import com.lecturo.lecturo.ui.cameraocr.CameraOCRActivity

class TasksActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTasksBinding

    private val viewModel: TasksViewModel by viewModels {
        getViewModelFactory()
    }

    fun getViewModelFactory(): TasksViewModelFactory {
        val database = AppDatabase.getDatabase(applicationContext)
        val tasksRepository = TasksRepository(database.tasksDao())
        val calendarRepository = CalendarRepository(database.calendarEntryDao())
        return TasksViewModelFactory(tasksRepository, calendarRepository, application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTasksBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Mengganti toolbar lama dengan yang baru dari binding
        setSupportActionBar(binding.taskToolbar)
        setupToolbar()
        setupViewPager()
        setupFabActions()
        observeTabTitles()
    }

    private fun setupToolbar() {
        supportActionBar?.title = "Tugas"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // --- PERBAIKAN UTAMA DI SINI ---
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.tasks_menu, menu)

        // 1. Dapatkan item menu search
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView

        // 2. Tambahkan listener untuk mendeteksi input teks
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            // Dipanggil saat pengguna menekan tombol search di keyboard (tidak kita gunakan di sini)
            override fun onQueryTextSubmit(query: String?): Boolean {
                return true
            }

            // Dipanggil setiap kali teks di search bar berubah
            override fun onQueryTextChange(newText: String?): Boolean {
                // 3. Kirim query ke ViewModel untuk memfilter daftar secara real-time
                viewModel.setSearchQuery(newText ?: "")
                return true
            }
        })

        // (Opsional tapi direkomendasikan) Hapus filter saat search ditutup
        searchItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                return true // Harus return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                viewModel.setSearchQuery("") // Hapus query saat search ditutup
                return true // Harus return true
            }
        })

        return true
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // ... (logika item menu Anda yang lain bisa ditambahkan di sini)
        return super.onOptionsItemSelected(item)
    }

    private fun setupViewPager() {
        val pagerAdapter = TasksPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = if (position == 0) "Tugas" else "Selesai"
        }.attach()
    }

    fun handleTasksAction(tasks: Tasks, action: String) {
        when (action) {
            "delete" -> showDeleteConfirmation(tasks)
            "complete" -> viewModel.updateTasksCompletedStatus(tasks.id, true)
            "uncomplete" -> viewModel.updateTasksCompletedStatus(tasks.id, false)
        }
    }

    private fun setupFabActions() {
        binding.fabAddTasks.setOnClickListener {
            startActivity(Intent(this, AddTasksActivity::class.java))
        }
    }

    private fun observeTabTitles() {
        viewModel.pendingTasks.observe(this) { pending ->
            binding.tabLayout.getTabAt(0)?.text = "Tugas (${pending.size})"
        }
        viewModel.completedTasks.observe(this) { completed ->
            binding.tabLayout.getTabAt(1)?.text = "Selesai (${completed.size})"
        }
    }

    private fun showDeleteConfirmation(tasks: Tasks) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Hapus Tugas")
            .setMessage("Yakin ingin menghapus tugas \"${tasks.title}\"?")
            .setPositiveButton("Ya") { _, _ -> viewModel.deleteTasks(tasks.id) }
            .setNegativeButton("Batal", null)
            .show()
    }
}
