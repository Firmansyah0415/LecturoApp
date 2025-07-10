package com.lecturo.lecturo

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.lecturo.lecturo.databinding.ActivityAddScheduleBinding
import com.lecturo.lecturo.db.AppDatabase
import com.lecturo.lecturo.ui.tasks.ScheduleRepository
import com.lecturo.lecturo.ui.tasks.TasksViewModel
import com.lecturo.lecturo.ui.tasks.TasksViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

class AddScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddScheduleBinding

    private val viewModel: TasksViewModel by viewModels {
        val dao = AppDatabase.getDatabase(applicationContext).scheduleDao()
        val repository = ScheduleRepository(dao)
        TasksViewModelFactory(repository)
    }

    private var scheduleId: Long = -1L
    private var isEditMode = false
    private var currentSchedule: Schedule? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupDateTimePickers()
        checkEditMode()

        binding.buttonSave.setOnClickListener {
            saveSchedule()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarAdd)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun checkEditMode() {
        scheduleId = intent.getLongExtra("schedule_id", -1L)
        if (scheduleId != -1L) {
            isEditMode = true
            supportActionBar?.title = "Edit Jadwal"
            binding.buttonSave.text = "Update"
            loadScheduleData()
        } else {
            supportActionBar?.title = "Tambah Jadwal"
        }
    }

    private fun loadScheduleData() {
        viewModel.getScheduleById(scheduleId).observe(this) { schedule ->
            schedule?.let {
                currentSchedule = it
                binding.editTitle.setText(it.title)
                binding.editDate.setText(it.date)
                binding.editTime.setText(it.time)
                binding.editLocation.setText(it.location)
                binding.editDescription.setText(it.description)

                viewModel.getScheduleById(scheduleId).removeObservers(this)
            }
        }
    }

    private fun saveSchedule() {
        val title = binding.editTitle.text.toString().trim()
        val date = binding.editDate.text.toString().trim()
        val time = binding.editTime.text.toString().trim()
        val location = binding.editLocation.text.toString().trim()
        val description = binding.editDescription.text.toString().trim()

        if (title.isEmpty() || date.isEmpty() || time.isEmpty()) {
            Toast.makeText(this, "Sila lengkapkan semua medan wajib", Toast.LENGTH_SHORT).show()
            return
        }

        val scheduleToSave = Schedule(
            id = if (isEditMode) scheduleId else 0,
            title = title, date = date, time = time,
            location = location, description = description,
            completed = if(isEditMode) currentSchedule?.completed ?: false else false
        )

        viewModel.insertOrUpdate(scheduleToSave)

        val message = if (isEditMode) "Jadwal berjaya dikemas kini" else "Jadwal berjaya ditambah"
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
