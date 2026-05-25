package com.lecturo.lecturo.ui.teaching

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.model.TeachingSchedule
import com.lecturo.lecturo.data.remote.RetrofitClient
import com.lecturo.lecturo.data.repository.TeachingRepository
import com.lecturo.lecturo.databinding.ActivityAddTeachingBinding
import com.lecturo.lecturo.viewmodel.teaching.TeachingViewModel
import com.lecturo.lecturo.viewmodel.teaching.TeachingViewModelFactory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lecturo.lecturo.ui.base.BaseActivity

class AddTeachingActivity : BaseActivity() {

    private lateinit var binding: ActivityAddTeachingBinding
    private var scheduleId: Long = -1
    private var isEditMode = false
    private var currentSchedule: TeachingSchedule? = null

    // State untuk Compose Repetition
    private var repeatMode by mutableStateOf("NONE")
    private var repeatValue by mutableStateOf("")

    private val viewModel: TeachingViewModel by viewModels {
        val database = AppDatabase.getDatabase(this)
        val repository = TeachingRepository(
            database.teachingScheduleDao(),
            database.calendarEntryDao(),
            applicationContext
        )
        TeachingViewModelFactory(repository, application)
    }
    private val notificationOptions = arrayOf("Tidak ada notifikasi", "Tepat waktu", "5 menit sebelumnya", "15 menit sebelumnya", "30 menit sebelumnya", "1 jam sebelumnya")
    private val notificationValues = arrayOf(-1, 0, 5, 15, 30, 60)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTeachingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val isNightMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        window.statusBarColor = getColor(R.color.colorPrimary)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isNightMode

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(view.paddingLeft, statusBarInsets.top, view.paddingRight, view.paddingBottom)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.buttonSave) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val baseMarginPx = (16 * resources.displayMetrics.density).toInt()
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = systemBars.bottom + baseMarginPx
            }
            insets
        }

        scheduleId = intent.getLongExtra("schedule_id", -1)
        isEditMode = scheduleId != -1L

        setupToolbar()
        setupTimePickers()
        setupDatePickers()
        setupNotificationDropdown()
        setupSaveButton()

        // BINDING COMPOSE VIEW
        binding.cvRepetition.setContent {
            MaterialTheme {
                RepetitionSelectorTeaching(
                    mode = repeatMode,
                    value = repeatValue,
                    onModeChange = { repeatMode = it },
                    onValueChange = { repeatValue = it },
                    onDatePickerClick = { showEndDateDatePicker() }
                )
            }
        }

        if (isEditMode) {
            binding.cvRepetition.visibility = View.GONE // Sembunyikan perulangan saat edit
            loadTeachingScheduleData()
        }
    }

    private fun saveTeachingSchedule() {
        val courseName = binding.editTextCourseName.text.toString().trim()
        val classCode = binding.editTextClassName.text.toString().trim()
        val dayOfWeek = binding.autoCompleteTextViewDayOfWeek.text.toString().trim()
        val startTime = binding.editTextStartTime.text.toString().trim()
        val endTime = binding.editTextEndTime.text.toString().trim()
        val classroom = binding.editTextLocation.text.toString().trim()
        val studentCountText = binding.editTextStudentCount.text.toString().trim()
        val date = binding.editTextDate.text.toString().trim()

        val notificationMinutes = getSelectedNotificationValue()

        if (courseName.isEmpty() || classCode.isEmpty() || dayOfWeek.isEmpty() || startTime.isEmpty() || endTime.isEmpty() || classroom.isEmpty() || studentCountText.isEmpty() || date.isEmpty()) {
            Toast.makeText(this, "Semua field wajib diisi", Toast.LENGTH_SHORT).show()
            return
        }

        // Validasi Perulangan
        if (repeatMode == "COUNT" && repeatValue.isEmpty()) {
            Toast.makeText(this, "Masukkan jumlah pertemuan", Toast.LENGTH_SHORT).show()
            return
        }
        if (repeatMode == "DATE" && repeatValue.isEmpty()) {
            Toast.makeText(this, "Pilih tanggal berakhir", Toast.LENGTH_SHORT).show()
            return
        }

        val studentCount = studentCountText.toIntOrNull()
        if (studentCount == null) { binding.editTextStudentCount.error = "Harus berupa angka"; return }

        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val currentUid = currentUser?.uid

        if (currentUid == null) {
            Toast.makeText(this, "Gagal: Anda belum login.", Toast.LENGTH_SHORT).show()
            return
        }

        val teachingSchedule = TeachingSchedule(
            localId = if (isEditMode) scheduleId else 0,
            firestoreId = currentSchedule?.firestoreId,
            userId = currentUid,
            isSynced = false,
            courseName = courseName,
            classCode = classCode,
            dayOfWeek = dayOfWeek,
            date = date,
            startTime = startTime,
            endTime = endTime,
            classroom = classroom,
            studentCount = studentCount,
            meetingNumber = currentSchedule?.meetingNumber ?: 1,
            isCompleted = currentSchedule?.isCompleted ?: false,
            notificationMinutes = notificationMinutes,
            inputSource = currentSchedule?.inputSource ?: "MANUAL"
        )

        if (isEditMode) {
            viewModel.updateTeachingSchedule(teachingSchedule)
            Toast.makeText(this, "Jadwal berhasil diupdate", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.saveNewTeachingSchedule(teachingSchedule, repeatMode, repeatValue)
            Toast.makeText(this, if (repeatMode == "NONE") "Jadwal berhasil dibuat" else "Jadwal perulangan dicetak", Toast.LENGTH_SHORT).show()
        }

        setResult(RESULT_OK)
        finish()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.addTeachingToolbar)
        supportActionBar?.apply {
            title = if (isEditMode) "Edit Jadwal Mengajar" else "Tambah Jadwal Mengajar"
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun setupNotificationDropdown() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, notificationOptions)
        binding.autoCompleteNotification.setAdapter(adapter)

        if (!isEditMode) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            val defaultValue = sharedPreferences.getInt("default_notification_teaching", 15)
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

    private fun setupTimePickers() {
        binding.editTextStartTime.setOnClickListener { showTimePicker { time -> binding.editTextStartTime.setText(time) } }
        binding.editTextEndTime.setOnClickListener { showTimePicker { time -> binding.editTextEndTime.setText(time) } }
    }

    private fun setupDatePickers() {
        binding.editTextDate.setOnClickListener {
            showDatePicker { date, day ->
                binding.editTextDate.setText(date)
                // [PERBAIKAN BUG] Otomatis isi kolom Hari berdasarkan Tanggal!
                binding.autoCompleteTextViewDayOfWeek.setText(day)
            }
        }
    }
    private fun showTimePicker(onTimeSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(this, { _, hourOfDay, minute ->
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val selectedTime = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hourOfDay); set(Calendar.MINUTE, minute) }
            onTimeSelected(timeFormat.format(selectedTime.time))
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
    }

    // [PERBAIKAN BUG] Fungsi ini sekarang mengembalikan 2 nilai: Tanggal dan Nama Hari
    private fun showDatePicker(onDateSelected: (String, String) -> Unit) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, dayOfMonth ->
            val selectedDate = Calendar.getInstance().apply { set(year, month, dayOfMonth) }

            // Format 1: dd/MM/yyyy (Untuk disimpan ke database)
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val dateStr = dateFormat.format(selectedDate.time)

            // Format 2: Nama Hari dalam Bahasa Indonesia (Untuk UI)
            val dayFormat = SimpleDateFormat("EEEE", Locale("id", "ID"))
            var dayStr = dayFormat.format(selectedDate.time)

            // Kadang Locale mengembalikan "Jum'at", kita standarkan ke "Jumat" agar sama dengan Tab
            if (dayStr.equals("Jum'at", ignoreCase = true)) dayStr = "Jumat"

            onDateSelected(dateStr, dayStr)

        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showEndDateDatePicker() {
        val datePicker = com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
            .setTitleText("Pilih Tanggal Berakhir")
            .setSelection(com.google.android.material.datepicker.MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            calendar.timeInMillis = selection
            val format = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            repeatValue = format.format(calendar.time)
        }
        datePicker.show(supportFragmentManager, "END_DATE_PICKER")
    }

    private fun setupSaveButton() {
        binding.buttonSave.setOnClickListener { saveTeachingSchedule() }
    }

    private fun loadTeachingScheduleData() {
        lifecycleScope.launch {
            val schedule = viewModel.getTeachingScheduleById(scheduleId)
            schedule?.let {
                currentSchedule = it
                binding.apply {
                    editTextCourseName.setText(it.courseName)
                    editTextClassName.setText(it.classCode)
                    autoCompleteTextViewDayOfWeek.setText(it.dayOfWeek)
                    editTextStartTime.setText(it.startTime)
                    editTextEndTime.setText(it.endTime)
                    editTextLocation.setText(it.classroom)
                    editTextStudentCount.setText(it.studentCount.toString())
                    editTextDate.setText(it.date)

                    val notificationText = getNotificationOptionText(it.notificationMinutes)
                    autoCompleteNotification.setText(notificationText, false)
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

// ==========================================================
// KOMPONEN JETPACK COMPOSE UI
// ==========================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepetitionSelectorTeaching(
    mode: String,
    value: String,
    onModeChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onDatePickerClick: () -> Unit
) {
    val primaryColor = colorResource(R.color.teaching_color)
    val textColor = colorResource(R.color.text_primary)
    val whiteColor = colorResource(android.R.color.white)

    val customChipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = primaryColor,
        selectedLabelColor = whiteColor,
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        labelColor = textColor
    )

    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text("Perulangan (Opsional)", fontWeight = FontWeight.Bold, color = textColor)
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == "NONE",
                onClick = { onModeChange("NONE"); onValueChange("") },
                label = { Text("Tidak") },
                colors = customChipColors,
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = mode == "NONE",
                    borderColor = primaryColor, selectedBorderColor = primaryColor,
                    borderWidth = 1.dp, selectedBorderWidth = 1.dp
                )
            )
            FilterChip(
                selected = mode == "COUNT",
                onClick = { onModeChange("COUNT"); onValueChange("") },
                label = { Text("Jumlah") },
                colors = customChipColors,
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = mode == "COUNT",
                    borderColor = primaryColor, selectedBorderColor = primaryColor,
                    borderWidth = 1.dp, selectedBorderWidth = 1.dp
                )
            )
            FilterChip(
                selected = mode == "DATE",
                onClick = { onModeChange("DATE"); onValueChange("") },
                label = { Text("Tgl Akhir") },
                colors = customChipColors,
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = mode == "DATE",
                    borderColor = primaryColor, selectedBorderColor = primaryColor,
                    borderWidth = 1.dp, selectedBorderWidth = 1.dp
                )
            )
        }

        if (mode == "COUNT") {
            OutlinedTextField(
                value = value,
                onValueChange = { if (it.all { char -> char.isDigit() }) onValueChange(it) },
                label = { Text("Jumlah Pertemuan") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryColor,
                    focusedLabelColor = primaryColor,
                    cursorColor = primaryColor,
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor
                )
            )
        } else if (mode == "DATE") {
            OutlinedTextField(
                value = value,
                onValueChange = {},
                label = { Text("Berakhir pada Tanggal") },
                readOnly = true,
                enabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clickable { onDatePickerClick() },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = textColor,
                    disabledBorderColor = primaryColor,
                    disabledLabelColor = primaryColor
                )
            )
        }
    }
}