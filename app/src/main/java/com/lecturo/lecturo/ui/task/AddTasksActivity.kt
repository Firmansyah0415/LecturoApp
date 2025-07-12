package com.lecturo.lecturo.ui.task

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.model.Tasks
import com.lecturo.lecturo.data.repository.TasksRepository
import com.lecturo.lecturo.databinding.ActivityAddTasksBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddTasksActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddTasksBinding

    private val viewModel: TasksViewModel by viewModels {
        val dao = AppDatabase.Companion.getDatabase(applicationContext).tasksDao()
        val repository = TasksRepository(dao)
        TasksViewModelFactory(repository)
    }

    private var tasksId: Long = -1L
    private var isEditMode = false
    private var currentTasks: Tasks? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTasksBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupDateTimePickers()
        checkEditMode()

        binding.buttonSave.setOnClickListener {
            saveTasks()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarAdd)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun checkEditMode() {
        tasksId = intent.getLongExtra("tasks_id", -1L)
        if (tasksId != -1L) {
            isEditMode = true
            supportActionBar?.title = "Edit Jadwal"
            binding.buttonSave.text = "Update"
            loadTasksData()
        } else {
            supportActionBar?.title = "Tambah Jadwal"
        }
    }

    private fun loadTasksData() {
        viewModel.getTasksById(tasksId).observe(this) { tasks ->
            tasks?.let {
                currentTasks = it
                binding.editTitle.setText(it.title)
                binding.editDate.setText(it.date)
                binding.editTime.setText(it.time)
                binding.editLocation.setText(it.location)
                binding.editDescription.setText(it.description)

                viewModel.getTasksById(tasksId).removeObservers(this)
            }
        }
    }

    private fun saveTasks() {
        val title = binding.editTitle.text.toString().trim()
        val date = binding.editDate.text.toString().trim()
        val time = binding.editTime.text.toString().trim()
        val location = binding.editLocation.text.toString().trim()
        val description = binding.editDescription.text.toString().trim()

        if (title.isEmpty() || date.isEmpty() || time.isEmpty()) {
            Toast.makeText(this, "Lengkapkan semua yang bertanda *", Toast.LENGTH_SHORT).show()
            return
        }

        val tasksToSave = Tasks(
            id = if (isEditMode) tasksId else 0,
            title = title, date = date, time = time,
            location = location, description = description,
            completed = if (isEditMode) currentTasks?.completed ?: false else false
        )

        viewModel.insertOrUpdate(tasksToSave)

        val message = if (isEditMode) "Tugas berhasil di perbarui" else "Tugas berhasil ditambahkan"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

        finish()
    }

    private fun setupDateTimePickers() {
        binding.editDate.setOnClickListener { showDatePicker() }
        binding.editTime.setOnClickListener { showTimePicker() }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedDate = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                binding.editDate.setText(dateFormat.format(selectedDate.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePicker() {
        val calendar = Calendar.getInstance()
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                val selectedTime = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                }
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                binding.editTime.setText(timeFormat.format(selectedTime.time))
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}