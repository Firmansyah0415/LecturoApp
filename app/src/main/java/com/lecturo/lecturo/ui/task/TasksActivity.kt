package com.lecturo.lecturo.ui.task

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.lecturo.lecturo.ui.task.AddTasksActivity
import com.lecturo.lecturo.ui.cameraocr.CameraOCRActivity
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.model.Tasks
import com.lecturo.lecturo.databinding.ActivityTasksBinding
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.repository.TasksRepository

class TasksActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTasksBinding

    private val viewModel: TasksViewModel by viewModels {
        getViewModelFactory()
    }

    fun getViewModelFactory(): TasksViewModelFactory {
        val dao = AppDatabase.getDatabase(applicationContext).tasksDao()
        val repository = TasksRepository(dao)
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
        supportActionBar?.title = "Tugas"
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

        searchView.queryHint = "Cari tugas..."

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
        val pagerAdapter = TasksPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, _ ->
            // Judul akan diupdate oleh observer
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

        binding.fabCamera.setOnClickListener {
            startActivity(Intent(this, CameraOCRActivity::class.java))
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