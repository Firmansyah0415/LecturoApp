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
import com.lecturo.lecturo.data.model.TeachingRule
import com.lecturo.lecturo.data.remote.RetrofitClient
import com.lecturo.lecturo.data.repository.TeachingRepository
import com.lecturo.lecturo.databinding.ActivityAddTeachingBinding
import com.lecturo.lecturo.viewmodel.teaching.TeachingViewModel
import com.lecturo.lecturo.viewmodel.teaching.TeachingViewModelFactory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AddTeachingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddTeachingBinding
    private var ruleId: Long = -1
    private var isEditMode = false

    // Variabel baru untuk menyimpan data saat ini (agar firestoreId tidak hilang saat edit)
    private var currentRule: TeachingRule? = null

    private val viewModel: TeachingViewModel by viewModels {
        val database = AppDatabase.getDatabase(this)
        val apiService = RetrofitClient.instance // Panggil Retrofit

        val repository = TeachingRepository(
            database.teachingRuleDao(),
            database.calendarEntryDao(),
            applicationContext
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
        "1 jam sebelumnya"
    )
    private val notificationValues = arrayOf(-1, 0, 5, 15, 30, 60)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTeachingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup UI System Bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // 1. Cek apakah aplikasi sedang di Mode Gelap
        val isNightMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

        // 2. atur warna status bar
        window.statusBarColor = getColor(R.color.colorPrimary)

        // 3. atur warna teks/icon status bar
        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = !isNightMode
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(view.paddingLeft, statusBarInsets.top, view.paddingRight, view.paddingBottom)
            insets
        }

        // [SOLUSI PRO: Mendorong btnSave ke atas Navigasi Sistem]
        ViewCompat.setOnApplyWindowInsetsListener(binding.buttonSave) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Konversi margin dasar 16dp dari XML ke satuan Pixel
            val baseMarginPx = (16 * resources.displayMetrics.density).toInt()

            // Update HANYA margin bawahnya, margin lain biarkan sesuai XML
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = systemBars.bottom + baseMarginPx
            }

            insets
        }

        checkEditMode()
        setupToolbar()
        setupDayOfWeekDropdown()
        setupTimePickers()
        setupDatePickers()
        setupNotificationDropdown()
        setupRepetitionLogic()
        setupSaveButton()

        if (isEditMode) {
            loadTeachingRuleData()
        }
    }

    private fun saveTeachingRule() {
        val courseName = binding.editTextCourseName.text.toString().trim()
        val classCode = binding.editTextClassName.text.toString().trim()
        val dayOfWeek = binding.autoCompleteTextViewDayOfWeek.text.toString().trim()
        val startTime = binding.editTextStartTime.text.toString().trim()
        val endTime = binding.editTextEndTime.text.toString().trim()
        val classroom = binding.editTextLocation.text.toString().trim()
        val studentCountText = binding.editTextStudentCount.text.toString().trim()
        val startDate = binding.editTextSemesterStartDate.text.toString().trim()

        val notificationMinutes = getSelectedNotificationValue()

        val repetitionType: String
        val repetitionValue: String

        if (binding.radioButtonDate.isChecked) {
            repetitionType = "DATE"
            repetitionValue = binding.editTextSemesterEndDate.text.toString().trim()
            if (repetitionValue.isEmpty()) { Toast.makeText(this, "Tanggal selesai harus diisi", Toast.LENGTH_SHORT).show(); return }
        } else {
            repetitionType = "COUNT"
            repetitionValue = binding.editTextMeetingCount.text.toString().trim()
            if (repetitionValue.isEmpty() || repetitionValue.toIntOrNull() == null || repetitionValue.toInt() <= 0) { binding.editTextMeetingCount.error = "Jumlah tidak valid"; return }
        }

        if (courseName.isEmpty() || classCode.isEmpty() || dayOfWeek.isEmpty() || startTime.isEmpty() || endTime.isEmpty() || classroom.isEmpty() || studentCountText.isEmpty() || startDate.isEmpty()) {
            Toast.makeText(this, "Semua field wajib harus diisi", Toast.LENGTH_SHORT).show()
            return
        }

        val studentCount = studentCountText.toIntOrNull()
        if (studentCount == null) { binding.editTextStudentCount.error = "Harus berupa angka"; return }

        // 1. AMBIL USER ID
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val currentUid = currentUser?.uid

        if (currentUid == null) {
            Toast.makeText(this, "Gagal: Anda belum login.", Toast.LENGTH_SHORT).show()
            return
        }

        // --- PERBAIKAN PENTING DI SINI ---
        val teachingRule = TeachingRule(
            // 1. localId: Gunakan ID dari Intent jika edit, 0 jika baru
            localId = if (isEditMode) ruleId else 0,

            // 2. firestoreId: Ambil dari currentRule agar ID Cloud tidak hilang saat update
            firestoreId = currentRule?.firestoreId,

            // 2. MASUKKAN USER ID & SYNC FLAG
            userId = currentUid,
            isSynced = false, // Tandai belum sync

            courseName = courseName,
            classCode = classCode,
            dayOfWeek = dayOfWeek,
            startTime = startTime,
            endTime = endTime,
            classroom = classroom,
            studentCount = studentCount,
            startDate = startDate,
            repetitionType = repetitionType,
            repetitionValue = repetitionValue,
            notificationMinutes = notificationMinutes
        )

        viewModel.saveNewTeachingRule(teachingRule)

        val message = if (isEditMode) "Aturan jadwal berhasil diupdate" else "Aturan jadwal berhasil ditambahkan"
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        setResult(RESULT_OK)
        finish()
    }

    private fun setupRepetitionLogic() {
        binding.radioGroupRepetition.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                binding.radioButtonDate.id -> {
                    binding.layoutSemesterEndDate.visibility = View.VISIBLE
                    binding.layoutMeetingCount.visibility = View.GONE
                }
                binding.radioButtonCount.id -> {
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
        binding.editTextStartTime.setOnClickListener {
            showTimePicker { time -> binding.editTextStartTime.setText(time) }
        }
        binding.editTextEndTime.setOnClickListener {
            showTimePicker { time -> binding.editTextEndTime.setText(time) }
        }
    }

    private fun setupDatePickers() {
        binding.editTextSemesterStartDate.setOnClickListener {
            showDatePicker { date -> binding.editTextSemesterStartDate.setText(date) }
        }
        binding.editTextSemesterEndDate.setOnClickListener {
            showDatePicker { date -> binding.editTextSemesterEndDate.setText(date) }
        }
    }

    private fun showTimePicker(onTimeSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(this, { _, hourOfDay, minute ->
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val selectedTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hourOfDay)
                set(Calendar.MINUTE, minute)
            }
            onTimeSelected(timeFormat.format(selectedTime.time))
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
    }

    private fun showDatePicker(onDateSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, dayOfMonth ->
            val selectedDate = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            onDateSelected(dateFormat.format(selectedDate.time))
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
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
                // --- SIMPAN DATA ASLI KE VARIABEL ---
                currentRule = it

                binding.apply {
                    editTextCourseName.setText(it.courseName)
                    editTextClassName.setText(it.classCode)
                    autoCompleteTextViewDayOfWeek.setText(it.dayOfWeek, false)
                    editTextStartTime.setText(it.startTime)
                    editTextEndTime.setText(it.endTime)
                    editTextLocation.setText(it.classroom) // Pastikan variabel model sesuai
                    editTextStudentCount.setText(it.studentCount.toString())
                    editTextSemesterStartDate.setText(it.startDate)

                    if (it.repetitionType == "COUNT") {
                        radioButtonCount.isChecked = true
                        editTextMeetingCount.setText(it.repetitionValue)
                    } else {
                        radioButtonDate.isChecked = true
                        editTextSemesterEndDate.setText(it.repetitionValue)
                    }
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