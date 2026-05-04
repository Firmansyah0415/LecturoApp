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
import com.google.firebase.auth.FirebaseAuth
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.model.Tasks
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
        val tasksRepository = TasksRepository(database.tasksDao(), database.focusSessionDao(), applicationContext)
        val calendarRepository = CalendarRepository(database.calendarEntryDao())
        TasksViewModelFactory(tasksRepository, calendarRepository, application)
    }

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

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val isNightMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

        window.statusBarColor = getColor(R.color.colorPrimary)

        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = !isNightMode

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(view.paddingLeft, statusBarInsets.top, view.paddingRight, view.paddingBottom)
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

    private fun setupPriorityDropdown() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, priorityOptions)
        binding.autoCompletePriority.setAdapter(adapter)
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

    // --- PERBAIKAN: Menambahkan endTime ke Database ---
    private fun saveTasks() {
        val title = binding.editTitle.text.toString().trim()
        val date = binding.editDate.text.toString().trim()
        val time = binding.editTime.text.toString().trim()
        val endTime = binding.editEndTime.text.toString().trim() // Ambil nilai End Time
        val location = binding.editLocation.text.toString().trim()
        val description = binding.editDescription.text.toString().trim()
        val priority = binding.autoCompletePriority.text.toString()
        val notificationMinutes = getSelectedNotificationValue()

        if (title.isEmpty() || date.isEmpty() || time.isEmpty()) {
            Toast.makeText(this, "Lengkapi semua field wajib", Toast.LENGTH_SHORT).show()
            return
        }

        // Logika Fallback (Jika end_time dibiarkan kosong oleh dosen)
        val finalEndTime = if (endTime.isEmpty()) {
            calculateEndTimeFallback(time)
        } else {
            endTime
        }

        val currentUser = FirebaseAuth.getInstance().currentUser
        val currentUid = currentUser?.uid

        if (currentUid == null) {
            Toast.makeText(this, "Gagal: Anda belum login.", Toast.LENGTH_SHORT).show()
            return
        }

        val tasksToSave = Tasks(
            id = if (isEditMode) tasksId else 0,
            userId = currentUid,
            firestoreId = currentTasks?.firestoreId,
            isSynced = false,
            title = title,
            date = date,
            time = time,
            endTime = finalEndTime, // Simpan End Time
            location = location,
            description = description,
            priority = priority,
            inputSource = currentTasks?.inputSource ?: "MANUAL",
            isCompleted = if (isEditMode) currentTasks?.isCompleted ?: false else false,
            notificationMinutesBefore = notificationMinutes
        )

        viewModel.insertOrUpdate(tasksToSave)

        val message = if (isEditMode) "Tugas berhasil diperbarui" else "Tugas berhasil ditambahkan"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

        finish()
    }

    // Helper: Tambah 1 Jam jika End Time kosong
    private fun calculateEndTimeFallback(startTime: String): String {
        try {
            val parts = startTime.split(":")
            if (parts.size == 2) {
                var h = parts[0].toInt()
                val m = parts[1]
                h = (h + 1) % 24
                return String.format("%02d:%s", h, m)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
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
                    binding.editEndTime.setText(it.endTime) // Tampilkan End Time saat edit
                    binding.editLocation.setText(it.location)
                    binding.editDescription.setText(it.description)
                    binding.autoCompletePriority.setText(it.priority, false)
                    val notificationText = getNotificationOptionText(it.notificationMinutesBefore)
                    binding.autoCompleteNotification.setText(notificationText, false)
                }
            }
        }
    }

    private fun setupDateTimePickers() {
        binding.editDate.setOnClickListener { showDatePicker() }
        binding.editTime.setOnClickListener { showTimePicker(true) }
        binding.editEndTime.setOnClickListener { showTimePicker(false) } // Listener untuk End Time
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

    private fun showTimePicker(isStartTime: Boolean) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                val selectedTime = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                }
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val formattedTime = timeFormat.format(selectedTime.time)

                if (isStartTime) {
                    binding.editTime.setText(formattedTime)
                } else {
                    binding.editEndTime.setText(formattedTime)
                }
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