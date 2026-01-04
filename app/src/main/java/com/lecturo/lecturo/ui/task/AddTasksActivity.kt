package com.lecturo.lecturo.ui.task

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.model.Tasks
import com.lecturo.lecturo.data.remote.RetrofitClient
import com.lecturo.lecturo.data.repository.CalendarRepository
import com.lecturo.lecturo.data.repository.TasksRepository
import com.lecturo.lecturo.databinding.ActivityAddTasksBinding
import com.lecturo.lecturo.viewmodel.task.TasksViewModel
import com.lecturo.lecturo.viewmodel.task.TasksViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

class AddTasksActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddTasksBinding

    private val viewModel: TasksViewModel by viewModels {
        val database = AppDatabase.getDatabase(applicationContext)
        // Panggil RetrofitClient.instance untuk mendapatkan apiService
        val apiService = RetrofitClient.instance
        val tasksRepository = TasksRepository(database.tasksDao(), apiService)
        val calendarRepository = CalendarRepository(database.calendarEntryDao())
        TasksViewModelFactory(tasksRepository, calendarRepository, application)
    }

    // Opsi Prioritas
    private val priorityOptions = arrayOf("Tinggi", "Sedang", "Rendah")

    private var tasksId: Long = -1L
    private var isEditMode = false
    private var currentTasks: Tasks? = null

    private val notificationOptions = arrayOf(
        "Tidak ada notifikasi",
        "Tepat waktu",
        "5 menit sebelumnya",
        "15 menit sebelumnya",
        "30 menit sebelumnya",
        "1 jam sebelumnya"
    )
    private val notificationValues = arrayOf(-1, 0, 5, 15, 30, 60)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTasksBinding.inflate(layoutInflater)
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

        setupToolbar()
        setupDateTimePickers()
        checkEditMode()
        setupPriorityDropdown()
        setupNotificationDropdown()

        binding.buttonSave.setOnClickListener {
            saveTasks()
        }
    }

    // 1. SETUP DROPDOWN PRIORITAS
    private fun setupPriorityDropdown() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, priorityOptions)
        binding.autoCompletePriority.setAdapter(adapter)

        // Default value jika bukan edit mode
        if (!isEditMode) {
            binding.autoCompletePriority.setText("Sedang", false)
        }
    }

    private fun setupNotificationDropdown() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, notificationOptions)
        binding.autoCompleteNotification.setAdapter(adapter)

        if (!isEditMode) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            val defaultValue = sharedPreferences.getInt("default_notification_task", 15)
            val defaultText = getNotificationOptionText(defaultValue)
            binding.autoCompleteNotification.setText(defaultText, false)
        }
    }

    private fun getNotificationOptionText(value: Int): String {
        val index = notificationValues.indexOf(value)
        return if (index != -1) notificationOptions[index] else notificationOptions[2]
    }

    private fun getSelectedNotificationValue(): Int {
        val selectedText = binding.autoCompleteNotification.text.toString()
        val index = notificationOptions.indexOf(selectedText)
        return if (index != -1) notificationValues[index] else 15
    }

    private fun saveTasks() {
        val title = binding.editTitle.text.toString().trim()
        val date = binding.editDate.text.toString().trim()
        val time = binding.editTime.text.toString().trim()
        val location = binding.editLocation.text.toString().trim()
        val description = binding.editDescription.text.toString().trim()

        // Ambil nilai prioritas yang dipilih dari dropdown
        val priority = binding.autoCompletePriority.text.toString()

        // Ambil nilai notifikasi yang dipilih dari dropdown
        val notificationMinutes = getSelectedNotificationValue()

        if (title.isEmpty() || date.isEmpty() || time.isEmpty()) {
            Toast.makeText(this, "Lengkapi semua field wajib", Toast.LENGTH_SHORT).show()
            return
        }

        val tasksToSave = Tasks(
            id = if (isEditMode) tasksId else 0,
            title = title,
            date = date,
            time = time,
            location = location,
            description = description,
            priority = priority, // <--- GUNAKAN VALUE BARU
            inputSource = currentTasks?.inputSource ?: "MANUAL", // Pertahankan source asli
            isCompleted = if (isEditMode) currentTasks?.isCompleted ?: false else false,
            notificationMinutesBefore = notificationMinutes
        )

        // Panggil viewModel hanya dengan satu argumen
        viewModel.insertOrUpdate(tasksToSave)

        val message = if (isEditMode) "Tugas berhasil diperbarui" else "Tugas berhasil ditambahkan"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

        finish()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarAdd)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = if (isEditMode) "Edit Tugas" else "Tambah Tugas"
    }

    private fun checkEditMode() {
        tasksId = intent.getLongExtra("tasks_id", -1L)
        isEditMode = tasksId != -1L
        if(isEditMode) {
            viewModel.getTasksById(tasksId).observe(this) { task ->
                task?.let {
                    currentTasks = it
                    binding.editTitle.setText(it.title)
                    binding.editDate.setText(it.date)
                    binding.editTime.setText(it.time)
                    binding.editLocation.setText(it.location)
                    binding.editDescription.setText(it.description)

                    // Load Prioritas
                    binding.autoCompletePriority.setText(it.priority, false)

                    // Muat pengaturan notifikasi yang sudah ada
                    val notificationText = getNotificationOptionText(it.notificationMinutesBefore)
                    binding.autoCompleteNotification.setText(notificationText, false)
                }
            }
        }
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
