package com.lecturo.lecturo.ui.consultation

import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.android.material.textfield.TextInputEditText
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.model.ConsultationSchedule
import com.lecturo.lecturo.databinding.ActivityDetailConsultationBinding
import com.lecturo.lecturo.di.ViewModelFactory // Pastikan import ini ada
import com.lecturo.lecturo.viewmodel.consultation.ConsultationViewModel
import java.text.SimpleDateFormat
import java.util.*

class DetailConsultationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailConsultationBinding

    // PERBAIKAN PENTING: Gunakan ViewModelFactory agar Repository ter-inject dengan benar
    private val viewModel: ConsultationViewModel by viewModels {
        ViewModelFactory.getInstance(application)
    }

    private var scheduleId: Long = 0
    private var recurringIdFromTemplate: String? = null

    // TAMBAHKAN INI: Variabel untuk menyimpan ID Firestore saat mode Edit
    private var currentFirestoreId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailConsultationBinding.inflate(layoutInflater)
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

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Buat Konsultasi"

        setupNotificationDropdown()

        binding.btnSave.setOnClickListener {
            saveConsultation()
        }

        setupPickers()

        if (intent.hasExtra("SCHEDULE_ID")) {
            scheduleId = intent.getLongExtra("SCHEDULE_ID", 0)
            loadScheduleData(scheduleId)
        } else if (intent.getBooleanExtra("IS_FROM_TEMPLATE", false)) {
            setupFromTemplate()
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

                // TAMBAHAN DEBUGGING (Cek Logcat nanti)
                android.util.Log.d("DETAIL_CONS", "Loaded ID: ${schedule.id}, FirestoreID: ${schedule.firestoreId}")

                // Simpan ID Firestore agar tidak hilang
                currentFirestoreId = schedule.firestoreId

                // Jika null (misal hasil restore gagal simpan ID), coba log error
                if (currentFirestoreId == null) {
                    android.util.Log.e("DETAIL_CONS", "BAHAYA: Firestore ID Kosong! Edit mungkin gagal sync.")
                }

                when (schedule.priority) {
                    "High" -> binding.chipGroupPriority.check(binding.chipHigh.id)
                    "Medium" -> binding.chipGroupPriority.check(binding.chipMedium.id)
                    "Low" -> binding.chipGroupPriority.check(binding.chipLow.id)
                }
            }
        }
    }

    private fun setupNotificationDropdown() {
        val notificationOptions = listOf(
            "15 minutes before",
            "30 minutes before",
            "1 hour before",
            "1 day before"
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, notificationOptions)
        binding.actNotification.setAdapter(adapter)
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
                val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
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
        // 1. Ambil data dari Input
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

        // 2. Validasi Input Kosong
        if (title.isEmpty() || date.isEmpty() || startTime.isEmpty() || endTime.isEmpty()) {
            Toast.makeText(this, "Mohon lengkapi judul, tanggal, dan waktu", Toast.LENGTH_SHORT).show()
            return
        }

        // 3. AMBIL UID (Hanya Satu Kali Di Sini)
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val currentUid = currentUser?.uid

        if (currentUid == null) {
            Toast.makeText(this, "Sesi habis, silakan login ulang", Toast.LENGTH_SHORT).show()
            return
        }

        // 4. Buat Objek & Simpan
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
            userId = currentUid, // Gunakan UID asli
            firestoreId = currentFirestoreId,
            status = "SCHEDULED"
        )

        if (scheduleId != 0L) {
            viewModel.updateSchedule(schedule)
            Toast.makeText(this, "Jadwal diperbarui", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.insertSchedule(schedule)
            Toast.makeText(this, "Jadwal berhasil dibuat", Toast.LENGTH_SHORT).show()
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