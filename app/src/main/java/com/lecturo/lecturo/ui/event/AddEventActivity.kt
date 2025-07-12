package com.lecturo.lecturo.ui.event

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.model.Event
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
        val repository = EventRepository(database.eventDao())
        EventViewModelFactory(repository)
    }

    private val categories = arrayOf(
        "Rapat",
        "Seminar / Webinar",
        "Lokakarya / Workshop",
        "Penelitian",
        "Pengabdian Masyarakat",
        "Lainnya"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEventBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkEditMode()
        setupToolbar()
        setupCategoryDropdown()
        setupDateTimePickers()
        setupSaveButton()

        if (isEditMode) {
            loadEventData()
        }
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
        binding.editTextDate.setOnClickListener {
            showDatePicker()
        }

        binding.editTextTime.setOnClickListener {
            showTimePicker()
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
                binding.editTextDate.setText(dateFormat.format(selectedDate.time))
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
                binding.editTextTime.setText(timeFormat.format(selectedTime.time))
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        )
        timePickerDialog.show()
    }

    private fun setupSaveButton() {
        binding.buttonSave.setOnClickListener {
            saveEvent()
        }
    }

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
                }
            }
        }
    }

    private fun saveEvent() {
        val title = binding.editTextTitle.text.toString().trim()
        val category = binding.autoCompleteTextViewCategory.text.toString().trim()
        val date = binding.editTextDate.text.toString().trim()
        val time = binding.editTextTime.text.toString().trim()
        val location = binding.editTextLocation.text.toString().trim()
        val description = binding.editTextDescription.text.toString().trim()

        // Validation
        if (title.isEmpty()) {
            binding.editTextTitle.error = "Judul harus diisi"
            return
        }

        if (category.isEmpty()) {
            Toast.makeText(this, "Kategori harus dipilih", Toast.LENGTH_SHORT).show()
            return
        }

        if (date.isEmpty()) {
            Toast.makeText(this, "Tanggal harus diisi", Toast.LENGTH_SHORT).show()
            return
        }

        if (time.isEmpty()) {
            Toast.makeText(this, "Waktu harus diisi", Toast.LENGTH_SHORT).show()
            return
        }

        if (location.isEmpty()) {
            binding.editTextLocation.error = "Lokasi harus diisi"
            return
        }

        val event = Event(
            id = if (isEditMode) eventId else 0,
            title = title,
            category = category,
            date = date,
            time = time,
            location = location,
            description = description.ifEmpty { null }
        )

        viewModel.insertOrUpdate(event)

        val message = if (isEditMode) "Event berhasil diupdate" else "Event berhasil ditambahkan"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

        setResult(RESULT_OK)
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
