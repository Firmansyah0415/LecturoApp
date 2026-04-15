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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailConsultationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Status bar transparan
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = getColor(R.color.colorPrimary)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

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