package com.lecturo.lecturo.ui.consultation

import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.android.material.textfield.TextInputEditText
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.model.ConsultationSchedule
import com.lecturo.lecturo.databinding.ActivityDetailConsultationBinding
import com.lecturo.lecturo.di.ViewModelFactory
import com.lecturo.lecturo.viewmodel.consultation.ConsultationViewModel
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

class DetailConsultationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailConsultationBinding

    private val viewModel: ConsultationViewModel by viewModels {
        ViewModelFactory.getInstance(application)
    }

    private var scheduleId: Long = 0
    private var recurringIdFromTemplate: String? = null
    private var currentFirestoreId: String? = null

    // 1. Variabel penampung nilai menit notifikasi
    // Tidak lagi di-hardcode ke 15!
    private var selectedNotifMinutes: Int = 0

    // [TAMBAHAN BARU] STATE UNTUK COMPOSE
    private var repeatMode by mutableStateOf("NONE") // NONE, COUNT, DATE
    private var repeatValue by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailConsultationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Status bar transparan
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

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Buat Konsultasi"

        // --- [PERBAIKAN FITUR: Terapkan Default Settings] ---
        applyDefaultSettings()

        setupNotificationChips()

        // [TAMBAHAN BARU] BINDING COMPOSE VIEW
        binding.cvRepetition.setContent {
            MaterialTheme {
                RepetitionSelector(
                    mode = repeatMode,
                    value = repeatValue,
                    onModeChange = { repeatMode = it },
                    onValueChange = { repeatValue = it },
                    onDatePickerClick = { showEndDateDatePicker() }
                )
            }
        }

        binding.btnSave.setOnClickListener {
            saveConsultation()
        }

        setupPickers()

        if (intent.hasExtra("SCHEDULE_ID")) {
            scheduleId = intent.getLongExtra("SCHEDULE_ID", 0)
            loadScheduleData(scheduleId)

            // [LOGIKA PENTING] Sembunyikan perulangan jika sedang Mode Edit jadwal tunggal
            binding.cvRepetition.visibility = android.view.View.GONE
        } else if (intent.getBooleanExtra("IS_FROM_TEMPLATE", false)) {
            setupFromTemplate()
        }
    }

    // [TAMBAHAN BARU] FUNGSI DATEPICKER UNTUK TANGGAL AKHIR
    private fun showEndDateDatePicker() {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Pilih Tanggal Berakhir")
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            calendar.timeInMillis = selection
            val format = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            repeatValue = format.format(calendar.time) // Update state Compose
        }
        datePicker.show(supportFragmentManager, "END_DATE_PICKER")
    }

    // --- FUNGSI BARU: Membaca Default dari Settings ---
    private fun applyDefaultSettings() {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        // Baca nilai pengaturan untuk konsultasi. Jika belum pernah diatur, default-nya 30 (sesuai Setting Activity-mu)
        selectedNotifMinutes = sharedPrefs.getInt("default_notification_consultation", 30)

        // Panggil fungsi untuk menyalakan/menyorot Chip yang sesuai dengan nilai default ini
        checkNotificationChipByValue(selectedNotifMinutes)
    }

    // --- FUNGSI BARU: Mencari dan Menyorot Chip berdasarkan nilai (Menit) ---
    private fun checkNotificationChipByValue(minutes: Int) {
        val chipId = when (minutes) {
            -1 -> R.id.chipNotifNone
            0 -> R.id.chipNotifOnTime
            5 -> R.id.chipNotif5m
            15 -> R.id.chipNotif15m
            30 -> R.id.chipNotif30m
            60 -> R.id.chipNotif1h
            else -> R.id.chipNotifOnTime // Fallback ke Tepat Waktu jika aneh
        }
        binding.chipGroupNotification.check(chipId)
    }

    private fun setupNotificationChips() {
        binding.chipGroupNotification.setOnCheckedStateChangeListener { group, checkedIds ->
            selectedNotifMinutes = when (checkedIds.firstOrNull()) {
                R.id.chipNotifNone -> -1
                R.id.chipNotifOnTime -> 0
                R.id.chipNotif5m -> 5
                R.id.chipNotif15m -> 15
                R.id.chipNotif30m -> 30
                R.id.chipNotif1h -> 60
                // Fallback default jika di-uncheck paksa (meski selectionRequired=true mencegah ini)
                else -> PreferenceManager.getDefaultSharedPreferences(this).getInt("default_notification_consultation", 30)
            }
        }
    }

    private fun setupFromTemplate() {
        val title = intent.getStringExtra("PREFILL_TITLE")
        val date = intent.getStringExtra("PREFILL_DATE")
        val start = intent.getStringExtra("PREFILL_START")
        val end = intent.getStringExtra("PREFILL_END")
        val loc = intent.getStringExtra("PREFILL_LOCATION")

        recurringIdFromTemplate = intent.getStringExtra("TEMPLATE_ID")

        binding.etTitle.setText(title)
        binding.etDate.setText(date)
        binding.etStartTime.setText(start)
        binding.etEndTime.setText(end)
        binding.etLocation.setText(loc)
    }

    private fun loadScheduleData(id: Long) {
        viewModel.getScheduleById(id) { schedule ->
            if (schedule != null) {
                binding.etTitle.setText(schedule.title)
                binding.etDate.setText(schedule.date)
                binding.etStartTime.setText(schedule.startTime)
                binding.etEndTime.setText(schedule.endTime)
                binding.etLocation.setText(schedule.location)
                binding.etDescription.setText(schedule.description)

                android.util.Log.d("DETAIL_CONS", "Loaded ID: ${schedule.id}, FirestoreID: ${schedule.firestoreId}")
                currentFirestoreId = schedule.firestoreId

                if (currentFirestoreId == null) {
                    android.util.Log.e("DETAIL_CONS", "BAHAYA: Firestore ID Kosong! Edit mungkin gagal sync.")
                }

                // Set Chip Notifikasi sesuai menit dari database
                selectedNotifMinutes = schedule.notificationMinutesBefore
                checkNotificationChipByValue(selectedNotifMinutes)

                // Set Chip Prioritas
                when (schedule.priority) {
                    "High" -> binding.chipGroupPriority.check(binding.chipHigh.id)
                    "Low" -> binding.chipGroupPriority.check(binding.chipLow.id)
                    else -> binding.chipGroupPriority.check(binding.chipMedium.id)
                }
            }
        }
    }

    private fun setupPickers() {
        binding.etDate.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Pilih Tanggal Konsultasi")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build()

            datePicker.addOnPositiveButtonClickListener { selection ->
                val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                calendar.timeInMillis = selection
                val format = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                binding.etDate.setText(format.format(calendar.time))
            }
            datePicker.show(supportFragmentManager, "DATE_PICKER")
        }

        fun showTimePicker(targetEditText: TextInputEditText, title: String) {
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(8)
                .setMinute(0)
                .setTitleText(title)
                .build()

            picker.addOnPositiveButtonClickListener {
                val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", picker.hour, picker.minute)
                targetEditText.setText(formattedTime)
            }
            picker.show(supportFragmentManager, "TIME_PICKER")
        }

        binding.etStartTime.setOnClickListener { showTimePicker(binding.etStartTime, "Jam Mulai") }
        binding.etEndTime.setOnClickListener { showTimePicker(binding.etEndTime, "Jam Selesai") }
    }

    private fun saveConsultation() {
        val title = binding.etTitle.text.toString()
        val date = binding.etDate.text.toString()
        val startTime = binding.etStartTime.text.toString()
        val endTime = binding.etEndTime.text.toString()
        val location = binding.etLocation.text.toString()
        val desc = binding.etDescription.text.toString()

        val priority = when (binding.chipGroupPriority.checkedChipId) {
            binding.chipHigh.id -> "High"
            binding.chipLow.id -> "Low"
            else -> "Medium"
        }

        if (title.isEmpty() || date.isEmpty() || startTime.isEmpty() || endTime.isEmpty()) {
            Toast.makeText(this, "Mohon lengkapi judul, tanggal, dan waktu", Toast.LENGTH_SHORT).show()
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

        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val currentUid = currentUser?.uid

        if (currentUid == null) {
            Toast.makeText(this, "Sesi habis, silakan login ulang", Toast.LENGTH_SHORT).show()
            return
        }

        val schedule = ConsultationSchedule(
            id = if (scheduleId != 0L) scheduleId else 0,
            recurringId = recurringIdFromTemplate,
            title = title,
            date = date,
            startTime = startTime,
            endTime = endTime,
            location = location,
            description = desc,
            priority = priority,
            userId = currentUid,
            notificationMinutesBefore = selectedNotifMinutes, // Variabel pintar
            firestoreId = currentFirestoreId,
            status = "SCHEDULED"
        )

        if (scheduleId != 0L) {
            viewModel.updateSchedule(schedule)
            Toast.makeText(this, "Jadwal diperbarui", Toast.LENGTH_SHORT).show()
        } else {
            // [PERUBAHAN PENTING] Kirim data ke ViewModel bersama mode perulangannya!
            viewModel.insertSchedule(schedule, repeatMode, repeatValue)

            val msg = if (repeatMode == "NONE") "Jadwal berhasil dibuat" else "Jadwal perulangan berhasil dicetak!"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        finish()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}

// [TAMBAHAN BARU] KOMPONEN JETPACK COMPOSE
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun RepetitionSelector(
//    mode: String,
//    value: String,
//    onModeChange: (String) -> Unit,
//    onValueChange: (String) -> Unit,
//    onDatePickerClick: () -> Unit
//) {
//    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
//        Text("Perulangan (Opsional)", fontWeight = FontWeight.Bold, color = colorResource(R.color.text_primary))
//        Spacer(modifier = Modifier.height(8.dp))
//
//        // Baris Tombol Filter M3
//        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
//            FilterChip(
//                selected = mode == "NONE",
//                onClick = { onModeChange("NONE"); onValueChange("") },
//                label = { Text("Tidak") }
//            )
//            FilterChip(
//                selected = mode == "COUNT",
//                onClick = { onModeChange("COUNT"); onValueChange("") },
//                label = { Text("Jumlah") }
//            )
//            FilterChip(
//                selected = mode == "DATE",
//                onClick = { onModeChange("DATE"); onValueChange("") },
//                label = { Text("Tgl Akhir") }
//            )
//        }
//
//        // Form Input Dinamis
//        if (mode == "COUNT") {
//            OutlinedTextField(
//                value = value,
//                onValueChange = { if (it.all { char -> char.isDigit() }) onValueChange(it) },
//                label = { Text("Jumlah Pertemuan") },
//                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
//                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
//                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
//                colors = OutlinedTextFieldDefaults.colors(
//                    focusedBorderColor = colorResource(R.color.consultation_color),
//                    focusedLabelColor = colorResource(R.color.consultation_color)
//                )
//            )
//        } else if (mode == "DATE") {
//            OutlinedTextField(
//                value = value,
//                onValueChange = {},
//                label = { Text("Berakhir pada Tanggal") },
//                readOnly = true,
//                enabled = false, // Disable agar tidak bisa diketik manual
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(top = 8.dp)
//                    .clickable { onDatePickerClick() }, // Klik untuk buka kalender
//                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
//                colors = OutlinedTextFieldDefaults.colors(
//                    disabledTextColor = colorResource(R.color.text_primary),
//                    disabledBorderColor = colorResource(R.color.consultation_color),
//                    disabledLabelColor = colorResource(R.color.consultation_color)
//                )
//            )
//        }
//    }
//}

// [TAMBAHAN BARU] KOMPONEN JETPACK COMPOSE
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepetitionSelector(
    mode: String,
    value: String,
    onModeChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onDatePickerClick: () -> Unit
) {
    // --- 1. DEKLARASI WARNA ---
    val primaryColor = colorResource(R.color.consultation_color)
    val textColor = colorResource(R.color.text_primary)
    val whiteColor = colorResource(android.R.color.white)

    // --- 2. KONFIGURASI WARNA TOMBOL (CHIP) ---
    val customChipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = primaryColor,
        selectedLabelColor = whiteColor,
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        labelColor = textColor
    )

    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text("Perulangan (Opsional)", fontWeight = FontWeight.Bold, color = textColor)
        Spacer(modifier = Modifier.height(8.dp))

        // Baris Tombol Filter M3
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // TOMBOL 1: TIDAK BERULANG
            FilterChip(
                selected = mode == "NONE",
                onClick = { onModeChange("NONE"); onValueChange("") },
                label = { Text("Tidak") },
                colors = customChipColors,
                // PERBAIKAN: Masukkan border langsung di sini!
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = mode == "NONE", // <-- Beritahu statusnya
                    borderColor = primaryColor,
                    selectedBorderColor = primaryColor,
                    borderWidth = 1.dp,
                    selectedBorderWidth = 1.dp
                )
            )

            // TOMBOL 2: JUMLAH
            FilterChip(
                selected = mode == "COUNT",
                onClick = { onModeChange("COUNT"); onValueChange("") },
                label = { Text("Jumlah") },
                colors = customChipColors,
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = mode == "COUNT", // <-- Beritahu statusnya
                    borderColor = primaryColor,
                    selectedBorderColor = primaryColor,
                    borderWidth = 1.dp,
                    selectedBorderWidth = 1.dp
                )
            )

            // TOMBOL 3: TANGGAL AKHIR
            FilterChip(
                selected = mode == "DATE",
                onClick = { onModeChange("DATE"); onValueChange("") },
                label = { Text("Tgl Akhir") },
                colors = customChipColors,
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = mode == "DATE", // <-- Beritahu statusnya
                    borderColor = primaryColor,
                    selectedBorderColor = primaryColor,
                    borderWidth = 1.dp,
                    selectedBorderWidth = 1.dp
                )
            )
        }

        // Form Input Dinamis
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