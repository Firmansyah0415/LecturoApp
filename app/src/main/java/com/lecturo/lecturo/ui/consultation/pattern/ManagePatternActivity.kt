package com.lecturo.lecturo.ui.consultation.pattern

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.model.ConsultationPattern
import com.lecturo.lecturo.databinding.ActivityManagePatternBinding
import com.lecturo.lecturo.di.ViewModelFactory
import com.lecturo.lecturo.ui.base.BaseActivity
import com.lecturo.lecturo.viewmodel.consultation.ConsultationViewModel
import java.util.Calendar
import java.util.Locale

class ManagePatternActivity : BaseActivity() {

    private lateinit var binding: ActivityManagePatternBinding
    private val viewModel: ConsultationViewModel by viewModels {
        ViewModelFactory.getInstance(application)
    }

    private var patternToEdit: ConsultationPattern? = null // Jika null = Mode Tambah Baru

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManagePatternBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // bikin status bar transparan sekali untuk semua activity
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 1. Cek apakah aplikasi sedang di Mode Gelap
        val isNightMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

        // 2. atur warna status bar
        window.statusBarColor = getColor(R.color.colorPrimary)

        // 3. atur warna teks/icon status bar
        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = !isNightMode

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
        setupDropdownDay()
        setupTimePickers()

        // Cek apakah ada data yang dikirim (Mode Edit)
        if (intent.hasExtra("PATTERN_DATA")) {
            patternToEdit = intent.getSerializableExtra("PATTERN_DATA") as? ConsultationPattern
            setupEditMode()
        }

        binding.btnSave.setOnClickListener { savePattern() }

        binding.btnDelete.setOnClickListener { deletePattern() }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = if (patternToEdit == null) "Buat Template Baru" else "Edit Template"
    }

    private fun setupEditMode() {
        patternToEdit?.let { pattern ->
            binding.etTitle.setText(pattern.titleTemplate)
            binding.etStartTime.setText(pattern.startTime)
            binding.etEndTime.setText(pattern.endTime)
            binding.etLocation.setText(pattern.locationDefault)

            // Set Dropdown Hari
            val dayName = mapCalendarDayToString(pattern.dayOfWeek)
            binding.actDay.setText(dayName, false)

            // Tampilkan tombol hapus
            binding.btnDelete.visibility = View.VISIBLE
            binding.btnSave.text = "Perbarui Template"
        }
    }

    private fun setupDropdownDay() {
        val days = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, days)
        binding.actDay.setAdapter(adapter)
    }

    private fun setupTimePickers() {
        binding.etStartTime.setOnClickListener {
            showTimePicker { time -> binding.etStartTime.setText(time) }
        }
        binding.etEndTime.setOnClickListener {
            showTimePicker { time -> binding.etEndTime.setText(time) }
        }
    }

    private fun showTimePicker(onTimeSelected: (String) -> Unit) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setTitleText("Pilih Jam")
            .build()

        picker.addOnPositiveButtonClickListener {
            val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", picker.hour, picker.minute)
            onTimeSelected(formattedTime)
        }
        picker.show(supportFragmentManager, "TIME_PICKER")
    }

    private fun savePattern() {
        val title = binding.etTitle.text.toString()
        val dayString = binding.actDay.text.toString()
        val startTime = binding.etStartTime.text.toString()
        val endTime = binding.etEndTime.text.toString()
        val location = binding.etLocation.text.toString()

        // 1. AMBIL UID ASLI
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid == null) {
            Toast.makeText(this, "Sesi habis", Toast.LENGTH_SHORT).show()
            return
        }

        // PERBAIKAN VALIDASI: Tambahkan || location.isEmpty()
        if (title.isEmpty() || dayString.isEmpty() || startTime.isEmpty() || endTime.isEmpty() || location.isEmpty()) {
            Toast.makeText(this, "Mohon lengkapi semua data termasuk lokasi default!", Toast.LENGTH_SHORT).show()
            return
        }

        val dayInt = mapStringToCalendarDay(dayString)

        val newPattern = ConsultationPattern(
            id = patternToEdit?.id ?: 0, // 0 = Insert Baru
            firestoreId = patternToEdit?.firestoreId,
            titleTemplate = title,
            dayOfWeek = dayInt,
            startTime = startTime,
            endTime = endTime,
            locationDefault = location,
            isActive = patternToEdit?.isActive ?: true, // Default true
            userId = currentUid
        )

        viewModel.savePattern(newPattern)
        Toast.makeText(this, "Template berhasil disimpan", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun deletePattern() {
        patternToEdit?.let { pattern ->
            MaterialAlertDialogBuilder(this)
                .setTitle("Hapus Template?")
                .setMessage("Template ini akan dihapus permanen.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Hapus") { _, _ ->
                    viewModel.deletePattern(pattern)
                    finish()
                }
                .show()
        }
    }

    // Helper: Konversi String Indo -> Calendar Int
    private fun mapStringToCalendarDay(day: String): Int {
        return when (day) {
            "Minggu" -> Calendar.SUNDAY
            "Senin" -> Calendar.MONDAY
            "Selasa" -> Calendar.TUESDAY
            "Rabu" -> Calendar.WEDNESDAY
            "Kamis" -> Calendar.THURSDAY
            "Jumat" -> Calendar.FRIDAY
            "Sabtu" -> Calendar.SATURDAY
            else -> Calendar.MONDAY
        }
    }

    // Helper: Konversi Calendar Int -> String Indo
    private fun mapCalendarDayToString(day: Int): String {
        return when (day) {
            Calendar.SUNDAY -> "Minggu"
            Calendar.MONDAY -> "Senin"
            Calendar.TUESDAY -> "Selasa"
            Calendar.WEDNESDAY -> "Rabu"
            Calendar.THURSDAY -> "Kamis"
            Calendar.FRIDAY -> "Jumat"
            Calendar.SATURDAY -> "Sabtu"
            else -> "Senin"
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}