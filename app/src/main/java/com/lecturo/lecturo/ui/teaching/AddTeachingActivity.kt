package com.lecturo.lecturo.ui.teaching

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.model.TeachingRule
import com.lecturo.lecturo.data.repository.TeachingRepository
import com.lecturo.lecturo.databinding.ActivityAddTeachingBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AddTeachingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddTeachingBinding
    private var ruleId: Long = -1
    private var isEditMode = false

    private val viewModel: TeachingViewModel by viewModels {
        val database = AppDatabase.getDatabase(this)
        val repository = TeachingRepository(
            database.teachingRuleDao(),
            database.eventDao(),
            database.calendarEntryDao()
        )
        TeachingViewModelFactory(repository, application)
    }

    private val daysOfWeek = arrayOf(
        "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu"
    )

    private val notificationOptions = arrayOf(
        "Tidak ada notifikasi",
        "Tepat waktu",
        "5 menit sebelumnya",
        "15 menit sebelumnya",
        "30 menit sebelumnya",
        "1 jam sebelumnya",
        "2 jam sebelumnya"
    )

    private val notificationValues = arrayOf(-1, 0, 5, 15, 30, 60, 120)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTeachingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkEditMode()
        setupToolbar()
        setupDayOfWeekDropdown()
        setupTimePickers()
        setupDatePickers()
        setupNotificationDropdown()
        setupRepetitionLogic() // Panggil fungsi baru
        setupSaveButton()

        if (isEditMode) {
            loadTeachingRuleData()
        }
    }

    // --- FUNGSI BARU UNTUK MENGATUR LOGIKA PENGULANGAN ---
    private fun setupRepetitionLogic() {
        binding.radioGroupRepetition.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                binding.radioButtonDate.id -> {
                    // Tampilkan field tanggal selesai, sembunyikan jumlah pertemuan
                    binding.layoutSemesterEndDate.visibility = View.VISIBLE
                    binding.layoutMeetingCount.visibility = View.GONE
                }
                binding.radioButtonCount.id -> {
                    // Sembunyikan field tanggal selesai, tampilkan jumlah pertemuan
                    binding.layoutSemesterEndDate.visibility = View.GONE
                    binding.layoutMeetingCount.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun checkEditMode() {
        ruleId = intent.getLongExtra("rule_id", -1)
        isEditMode = ruleId != -1L
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.addTeachingToolbar)
        supportActionBar?.apply {
            title = if (isEditMode) "Edit Jadwal Mengajar" else "Tambah Jadwal Mengajar"
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun setupDayOfWeekDropdown() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, daysOfWeek)
        binding.autoCompleteTextViewDayOfWeek.setAdapter(adapter)
    }

    private fun setupNotificationDropdown() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, notificationOptions)
        binding.autoCompleteNotification.setAdapter(adapter)

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val defaultValue = sharedPreferences.getInt("default_notification_teaching", 15)
        val defaultText = getNotificationOptionText(defaultValue)
        binding.autoCompleteNotification.setText(defaultText, false)
    }

    private fun getNotificationOptionText(value: Int): String {
        val index = notificationValues.indexOf(value)
        return if (index >= 0) notificationOptions[index] else notificationOptions[3]
    }

    private fun getNotificationValue(text: String): Int {
        val index = notificationOptions.indexOf(text)
        return if (index >= 0) notificationValues[index] else 15
    }

    private fun setupTimePickers() {
        binding.editTextStartTime.setOnClickListener {
            showTimePicker { time ->
                binding.editTextStartTime.setText(time)
            }
        }

        binding.editTextEndTime.setOnClickListener {
            showTimePicker { time ->
                binding.editTextEndTime.setText(time)
            }
        }
    }

    private fun setupDatePickers() {
        binding.editTextSemesterStartDate.setOnClickListener {
            showDatePicker { date ->
                binding.editTextSemesterStartDate.setText(date)
            }
        }

        binding.editTextSemesterEndDate.setOnClickListener {
            showDatePicker { date ->
                binding.editTextSemesterEndDate.setText(date)
            }
        }
    }

    private fun showTimePicker(onTimeSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val selectedTime = Calendar.getInstance()
                selectedTime.set(Calendar.HOUR_OF_DAY, hourOfDay)
                selectedTime.set(Calendar.MINUTE, minute)
                onTimeSelected(timeFormat.format(selectedTime.time))
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun showDatePicker(onDateSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedDate = Calendar.getInstance()
                selectedDate.set(year, month, dayOfMonth)
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                onDateSelected(dateFormat.format(selectedDate.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun setupSaveButton() {
        binding.buttonSave.setOnClickListener {
            saveTeachingRule()
        }
    }

    private fun loadTeachingRuleData() {
        lifecycleScope.launch {
            val rule = viewModel.getTeachingRuleById(ruleId)
            rule?.let {
                binding.apply {
                    editTextCourseName.setText(it.courseName)
                    editTextClassName.setText(it.className)
                    autoCompleteTextViewDayOfWeek.setText(it.dayOfWeek, false)
                    editTextStartTime.setText(it.startTime)
                    editTextEndTime.setText(it.endTime)
                    editTextLocation.setText(it.location)
                    editTextStudentCount.setText(it.studentCount.toString())
                    editTextSemesterStartDate.setText(it.semesterStartDate)

                    // Logika untuk memuat ulang pilihan pengulangan
                    if (it.repetitionType == "COUNT") {
                        radioButtonCount.isChecked = true
                        editTextMeetingCount.setText(it.repetitionValue)
                    } else {
                        radioButtonDate.isChecked = true
                        editTextSemesterEndDate.setText(it.repetitionValue)
                    }
                }
            }
        }
    }

    private fun saveTeachingRule() {
        val courseName = binding.editTextCourseName.text.toString().trim()
        val className = binding.editTextClassName.text.toString().trim()
        val dayOfWeek = binding.autoCompleteTextViewDayOfWeek.text.toString().trim()
        val startTime = binding.editTextStartTime.text.toString().trim()
        val endTime = binding.editTextEndTime.text.toString().trim()
        val location = binding.editTextLocation.text.toString().trim()
        val studentCountText = binding.editTextStudentCount.text.toString().trim()
        val semesterStartDate = binding.editTextSemesterStartDate.text.toString().trim()

        val notificationText = binding.autoCompleteNotification.text.toString().trim()
        val notificationValue = getNotificationValue(notificationText)

        // --- VALIDASI DAN PENGAMBILAN DATA REPETITION ---
        val repetitionType: String
        val repetitionValue: String

        if (binding.radioButtonDate.isChecked) {
            repetitionType = "DATE"
            repetitionValue = binding.editTextSemesterEndDate.text.toString().trim()
            if (repetitionValue.isEmpty()) {
                Toast.makeText(this, "Tanggal selesai semester harus diisi", Toast.LENGTH_SHORT).show()
                return
            }
        } else {
            repetitionType = "COUNT"
            repetitionValue = binding.editTextMeetingCount.text.toString().trim()
            if (repetitionValue.isEmpty() || repetitionValue.toIntOrNull() == null || repetitionValue.toInt() <= 0) {
                binding.editTextMeetingCount.error = "Jumlah pertemuan tidak valid"
                return
            }
        }

        // Validasi field lain
        if (courseName.isEmpty() || className.isEmpty() || dayOfWeek.isEmpty() || startTime.isEmpty() || endTime.isEmpty() || location.isEmpty() || studentCountText.isEmpty() || semesterStartDate.isEmpty()) {
            Toast.makeText(this, "Semua field wajib harus diisi", Toast.LENGTH_SHORT).show()
            return
        }

        val studentCount = studentCountText.toIntOrNull()
        if (studentCount == null) {
            binding.editTextStudentCount.error = "Jumlah mahasiswa harus berupa angka"
            return
        }

        // Validasi lainnya...
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        try {
            val startDate = dateFormat.parse(semesterStartDate)
            if (repetitionType == "DATE") {
                val endDate = dateFormat.parse(repetitionValue)
                if (startDate != null && endDate != null && startDate >= endDate) {
                    Toast.makeText(this, "Tanggal mulai harus lebih awal dari tanggal selesai", Toast.LENGTH_SHORT).show()
                    return
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Format tanggal tidak valid", Toast.LENGTH_SHORT).show()
            return
        }

        // --- MEMBUAT OBJEK TeachingRule DENGAN DATA YANG BENAR ---
        val teachingRule = TeachingRule(
            id = if (isEditMode) ruleId else 0,
            courseName = courseName,
            className = className,
            dayOfWeek = dayOfWeek,
            startTime = startTime,
            endTime = endTime,
            location = location,
            studentCount = studentCount,
            semesterStartDate = semesterStartDate,
            repetitionType = repetitionType,
            repetitionValue = repetitionValue
        )

        viewModel.saveNewTeachingRule(teachingRule, notificationValue)

        val message = if (isEditMode) {
            "Jadwal mengajar berhasil diupdate"
        } else {
            "Jadwal mengajar berhasil ditambahkan dan entri kalender telah dibuat"
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        setResult(RESULT_OK)
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
