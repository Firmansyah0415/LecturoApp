package com.lecturo.lecturo

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class AddScheduleActivity : AppCompatActivity() {

    private lateinit var editTitle: EditText
    private lateinit var editDate: EditText
    private lateinit var editTime: EditText
    private lateinit var editLocation: EditText
    private lateinit var editDescription: EditText
    private lateinit var buttonSave: Button
    private lateinit var dbHelper: DatabaseHelper

    private var scheduleId: Long = -1
    private var isEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_schedule)

        setupToolbar()
        initializeViews()
        setupDateTimePickers()
        checkEditMode()
        loadExtractedData() // Tambahkan ini untuk memuat data OCR
    }

    private fun setupToolbar() {
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun initializeViews() {
        editTitle = findViewById(R.id.editTitle)
        editDate = findViewById(R.id.editDate)
        editTime = findViewById(R.id.editTime)
        editLocation = findViewById(R.id.editLocation)
        editDescription = findViewById(R.id.editDescription)
        buttonSave = findViewById(R.id.buttonSave)

        dbHelper = DatabaseHelper(this)

        buttonSave.setOnClickListener {
            saveSchedule()
        }
    }

    private fun setupDateTimePickers() {
        editDate.setOnClickListener {
            showDatePicker()
        }

        editTime.setOnClickListener {
            showTimePicker()
        }
    }

    private fun checkEditMode() {
        scheduleId = intent.getLongExtra("schedule_id", -1)
        if (scheduleId != -1L) {
            isEditMode = true
            supportActionBar?.title = "Edit Jadwal"
            buttonSave.text = "Update"
            loadScheduleData()
        } else {
            supportActionBar?.title = "Tambah Jadwal"
        }
    }

    // Fungsi baru untuk memuat data hasil ekstraksi OCR
    private fun loadExtractedData() {
        val fromOCR = intent.getBooleanExtra("from_ocr", false)
        if (fromOCR) {
            val extractedTitle = intent.getStringExtra("extracted_title") ?: ""
            val extractedDate = intent.getStringExtra("extracted_date") ?: ""
            val extractedTime = intent.getStringExtra("extracted_time") ?: ""
            val extractedLocation = intent.getStringExtra("extracted_location") ?: ""

            // Debug log
            android.util.Log.d("ADD_SCHEDULE", "Loading OCR data:")
            android.util.Log.d("ADD_SCHEDULE", "Title: $extractedTitle")
            android.util.Log.d("ADD_SCHEDULE", "Date: $extractedDate")
            android.util.Log.d("ADD_SCHEDULE", "Time: $extractedTime")
            android.util.Log.d("ADD_SCHEDULE", "Location: $extractedLocation")

            // Set data ke form
            if (extractedTitle.isNotEmpty()) {
                editTitle.setText(extractedTitle)
            }
            if (extractedDate.isNotEmpty()) {
                editDate.setText(formatDate(extractedDate))
            }
            if (extractedTime.isNotEmpty()) {
                editTime.setText(formatTime(extractedTime))
            }
            if (extractedLocation.isNotEmpty()) {
                editLocation.setText(extractedLocation)
            }

            // Tampilkan pesan bahwa data telah diisi otomatis
            Toast.makeText(this, "Data jadwal berhasil diekstrak dari gambar", Toast.LENGTH_LONG).show()
        }
    }

    // Fungsi untuk memformat tanggal ke format yang konsisten
    private fun formatDate(dateString: String): String {
        return try {
            // Coba berbagai format tanggal
            val formats = arrayOf(
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
                SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()),
                SimpleDateFormat("dd MM yyyy", Locale.getDefault()),
                SimpleDateFormat("d/M/yyyy", Locale.getDefault()),
                SimpleDateFormat("d-M-yyyy", Locale.getDefault())
            )

            for (format in formats) {
                try {
                    val date = format.parse(dateString)
                    return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date!!)
                } catch (e: Exception) {
                    continue
                }
            }
            dateString // Return original jika tidak bisa diformat
        } catch (e: Exception) {
            dateString
        }
    }

    // Fungsi untuk memformat waktu ke format yang konsisten
    private fun formatTime(timeString: String): String {
        return try {
            // Bersihkan string waktu dari suffix seperti WIB, AM, PM
            val cleanTime = timeString.replace(Regex("\\s*(WIB|WITA|WIT|AM|PM)\\s*", RegexOption.IGNORE_CASE), "").trim()

            // Coba berbagai format waktu
            val formats = arrayOf(
                SimpleDateFormat("HH:mm", Locale.getDefault()),
                SimpleDateFormat("H:mm", Locale.getDefault()),
                SimpleDateFormat("HH.mm", Locale.getDefault()),
                SimpleDateFormat("H.mm", Locale.getDefault()),
                SimpleDateFormat("HH-mm", Locale.getDefault()),
                SimpleDateFormat("H-mm", Locale.getDefault())
            )

            for (format in formats) {
                try {
                    val time = format.parse(cleanTime)
                    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(time!!)
                } catch (e: Exception) {
                    continue
                }
            }
            cleanTime // Return original jika tidak bisa diformat
        } catch (e: Exception) {
            timeString
        }
    }

    private fun loadScheduleData() {
        val schedule = dbHelper.getScheduleById(scheduleId)
        schedule?.let {
            editTitle.setText(it.title)
            editDate.setText(it.date)
            editTime.setText(it.time)
            editLocation.setText(it.location)
            editDescription.setText(it.description)
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedDate = Calendar.getInstance()
                selectedDate.set(year, month, dayOfMonth)
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                editDate.setText(dateFormat.format(selectedDate.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun showTimePicker() {
        val calendar = Calendar.getInstance()
        val timePickerDialog = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val selectedTime = Calendar.getInstance()
                selectedTime.set(Calendar.HOUR_OF_DAY, hourOfDay)
                selectedTime.set(Calendar.MINUTE, minute)
                editTime.setText(timeFormat.format(selectedTime.time))
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        )
        timePickerDialog.show()
    }

    private fun saveSchedule() {
        val title = editTitle.text.toString().trim()
        val date = editDate.text.toString().trim()
        val time = editTime.text.toString().trim()
        val location = editLocation.text.toString().trim()
        val description = editDescription.text.toString().trim()

        if (title.isEmpty() || date.isEmpty() || time.isEmpty() || location.isEmpty()) {
            Toast.makeText(this, "Mohon lengkapi semua field yang wajib", Toast.LENGTH_SHORT).show()
            return
        }

        val schedule = Schedule(
            id = if (isEditMode) scheduleId else 0,
            title = title,
            date = date,
            time = time,
            location = location,
            description = description
        )

        val result: Long = if (isEditMode) {
            dbHelper.updateSchedule(schedule).toLong()
        } else {
            dbHelper.insertSchedule(schedule)
        }

        if (result > 0) {
            val message = if (isEditMode) "Jadwal berhasil diupdate" else "Jadwal berhasil ditambahkan"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK) // Tambahkan ini
            finish()
        } else {
            Toast.makeText(this, "Gagal menyimpan jadwal", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}