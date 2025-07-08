package com.lecturo.lecturo.ui.tasks

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lecturo.lecturo.AddScheduleActivity
import com.lecturo.lecturo.CameraOCRActivity
import com.lecturo.lecturo.R
import com.lecturo.lecturo.Schedule
import com.lecturo.lecturo.ScheduleAdapter
import com.lecturo.lecturo.databinding.ActivityTasksBinding

class TasksActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTasksBinding
    private lateinit var scheduleAdapter: ScheduleAdapter

    private val viewModel by viewModels<TasksViewModel> {
        TasksViewModelFactory(ScheduleRepository(this))
    }

    private val addScheduleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) {
            viewModel.loadSchedules() // Refresh jadwal setelah kembali
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTasksBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupFabActions()

        viewModel.schedules.observe(this) {
            scheduleAdapter.updateData(it)
        }

        viewModel.loadSchedules()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadSchedules() // refresh otomatis setiap kali Activity aktif kembali
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Jadwal Tugas"
    }

    private fun setupFabActions() {
        binding.fabAddSchedule.setOnClickListener {
            val intent = Intent(this, AddScheduleActivity::class.java)
            addScheduleLauncher.launch(intent)
        }

        binding.fabCamera.setOnClickListener {
            startActivity(Intent(this, CameraOCRActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        scheduleAdapter = ScheduleAdapter(mutableListOf()) { schedule, action ->
            when (action) {
                "edit" -> {
                    val intent = Intent(this, AddScheduleActivity::class.java)
                    intent.putExtra("schedule_id", schedule.id)
                    startActivity(intent)
                }
                "delete" -> showDeleteConfirmation(schedule)
            }
        }

        binding.recyclerViewSchedules.apply {
            layoutManager = LinearLayoutManager(this@TasksActivity)
            adapter = scheduleAdapter
        }
    }

    private fun showDeleteConfirmation(schedule: Schedule) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Hapus Jadwal")
            .setMessage("Apakah kamu yakin ingin menghapus jadwal ini?")
            .setPositiveButton("Ya") { _, _ ->
                viewModel.deleteSchedule(schedule.id)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.tasks_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                viewModel.loadSchedules()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
