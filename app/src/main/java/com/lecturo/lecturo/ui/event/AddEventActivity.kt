package com.lecturo.lecturo.ui.event

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.model.Event
import com.lecturo.lecturo.data.repository.CalendarRepository
import com.lecturo.lecturo.data.repository.EventRepository
import com.lecturo.lecturo.databinding.ActivityAddEventBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AddEventActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEventBinding
    private var eventId: Long = -1
    private var isEditMode = false

    private val viewModel: EventViewModel by viewModels {
        val database = AppDatabase.getDatabase(this)
        val eventRepository = EventRepository(database.eventDao())
        val calendarRepository = CalendarRepository(database.calendarEntryDao())
        EventViewModelFactory(eventRepository, calendarRepository, application)
    }

    private val categories = arrayOf(
        "Rapat",
        "Seminar / Webinar",
        "Lokakarya / Workshop",
        "Penelitian",
        "Pengabdian Masyarakat",
        "Lainnya"
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
        binding = ActivityAddEventBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkEditMode()
        setupToolbar()
        setupCategoryDropdown()
        setupDateTimePickers()
        setupNotificationDropdown()
        setupSaveButton()

        if (isEditMode) {
            loadEventData()
        }
    }

    // --- FUNGSI loadEventData YANG DIPERBARUI ---
    private fun loadEventData() {
        lifecycleScope.launch {
            val event = viewModel.getEventById(eventId)
            event?.let {
                binding.apply {
                    editTextTitle.setText(it.title)
                    autoCompleteTextViewCategory.setText(it.category, false)
                    editTextDate.setText(it.date)
                    editTextTime.setText(it.time)
                    editTextLocation.setText(it.location)
                    editTextDescription.setText(it.description)

                    // PERBAIKAN: Muat pengaturan notifikasi yang sudah ada
                    val notificationText = getNotificationOptionText(it.notificationMinutesBefore)
                    autoCompleteNotification.setText(notificationText, false)
                }
            }
        }
    }

    // --- FUNGSI saveEvent YANG DIPERBARUI ---
    private fun saveEvent() {
        val title = binding.editTextTitle.text.toString().trim()
        val category = binding.autoCompleteTextViewCategory.text.toString().trim()
        val date = binding.editTextDate.text.toString().trim()
        val time = binding.editTextTime.text.toString().trim()
        val location = binding.editTextLocation.text.toString().trim()
        val description = binding.editTextDescription.text.toString().trim()

        // Ambil nilai notifikasi yang dipilih dari dropdown
        val notificationMinutes = getSelectedNotificationValue()

        if (title.isEmpty() || category.isEmpty() || date.isEmpty() || time.isEmpty() || location.isEmpty()) {
            Toast.makeText(this, "Lengkapi semua field wajib", Toast.LENGTH_SHORT).show()
            return
        }

        val event = Event(
            id = if (isEditMode) eventId else 0,
            title = title, category = category, date = date, time = time,
            location = location, description = description.ifEmpty { null },
            // Simpan nilai notifikasi ke dalam objek Event
            notificationMinutesBefore = notificationMinutes
        )

        // Panggil viewModel hanya dengan satu argumen
        viewModel.insertOrUpdate(event)

        val message = if (isEditMode) "Event berhasil diupdate" else "Event berhasil ditambahkan"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

        setResult(RESULT_OK)
        finish()
    }

    private fun setupNotificationDropdown() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, notificationOptions)
        binding.autoCompleteNotification.setAdapter(adapter)

        // Set nilai default hanya jika BUKAN mode edit
        if (!isEditMode) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            val defaultValue = sharedPreferences.getInt("default_notification_event", 15)
            val defaultText = getNotificationOptionText(defaultValue)
            binding.autoCompleteNotification.setText(defaultText, false)
        }
    }

    private fun getNotificationOptionText(value: Int): String {
        val index = notificationValues.indexOf(value)
        return if (index != -1) notificationOptions[index] else notificationOptions[2] // Default ke 15 menit
    }

    private fun getSelectedNotificationValue(): Int {
        val selectedText = binding.autoCompleteNotification.text.toString()
        val index = notificationOptions.indexOf(selectedText)
        return if (index != -1) notificationValues[index] else 15 // Default 15 menit
    }

    private fun checkEditMode() {
        eventId = intent.getLongExtra("event_id", -1)
        isEditMode = eventId != -1L
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.addEventToolbar)
        supportActionBar?.apply {
            title = if (isEditMode) "Edit Event" else "Tambah Event"
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun setupCategoryDropdown() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        binding.autoCompleteTextViewCategory.setAdapter(adapter)
    }

    private fun setupDateTimePickers() {
        binding.editTextDate.setOnClickListener { showDatePicker() }
        binding.editTextTime.setOnClickListener { showTimePicker() }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedDate = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                binding.editTextDate.setText(dateFormat.format(selectedDate.time))
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
                binding.editTextTime.setText(timeFormat.format(selectedTime.time))
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun setupSaveButton() {
        binding.buttonSave.setOnClickListener { saveEvent() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
