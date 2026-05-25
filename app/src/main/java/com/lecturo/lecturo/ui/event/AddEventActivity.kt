package com.lecturo.lecturo.ui.event

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Build
import android.os.Bundle
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
import com.lecturo.lecturo.data.model.Event
import com.lecturo.lecturo.data.repository.EventRepository
import com.lecturo.lecturo.data.repository.CalendarRepository
import com.lecturo.lecturo.databinding.ActivityAddEventBinding
import com.lecturo.lecturo.ui.base.BaseActivity
import com.lecturo.lecturo.viewmodel.event.EventViewModel
import com.lecturo.lecturo.viewmodel.event.EventViewModelFactory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AddEventActivity : BaseActivity() {

    private lateinit var binding: ActivityAddEventBinding
    private var eventId: Long = -1
    private var isEditMode = false
    private var currentEvent: Event? = null

    // 🔴 VARIABEL PENYIMPAN SUMBER AI_SCAN
    private var sourceFromAi: String? = null

    private val viewModel: EventViewModel by viewModels {
        val database = AppDatabase.getDatabase(this)
        val eventRepository = EventRepository(database.eventDao(), applicationContext)
        val calendarRepository = CalendarRepository(database.calendarEntryDao())
        EventViewModelFactory(eventRepository, calendarRepository, application)
    }

    private val categories = arrayOf(
        "Rapat", "Seminar", "Webinar", "Workshop",
        "Lokakarya", "Penelitian", "Pengabdian Masyarakat", "Lainnya"
    )

    private val notificationOptions = arrayOf(
        "Tidak ada notifikasi", "Tepat waktu", "5 menit sebelumnya",
        "15 menit sebelumnya", "30 menit sebelumnya", "1 jam sebelumnya"
    )
    private val notificationValues = arrayOf(-1, 0, 5, 15, 30, 60)
    private val priorityOptions = arrayOf("Tinggi", "Sedang", "Rendah")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEventBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val isNightMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

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

        checkEditMode()
        setupToolbar()
        setupCategoryDropdown()
        setupPriorityDropdown()
        setupDateTimePickers()
        setupNotificationDropdown()
        setupSaveButton()

        if (isEditMode) {
            loadEventData()
        } else {
            populateFormFromAi()
        }
    }

    private fun populateFormFromAi() {
        val eventFromAi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("EXTRA_EVENT_AI", Event::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("EXTRA_EVENT_AI") as? Event
        }

        eventFromAi?.let {
            binding.editTextTitle.setText(it.title)
            binding.autoCompleteTextViewCategory.setText(it.category, false)
            binding.autoCompleteTextViewPriority.setText(it.priority ?: "Sedang", false)
            binding.editTextDate.setText(it.date)
            binding.editTextTime.setText(it.time)
            binding.editTextEndTime.setText(it.endTime)
            binding.editTextLocation.setText(it.location)
            binding.editTextDescription.setText(it.description)

            // 🔴 STEMPEL JIKA JADWAL BERASAL DARI FITUR OCR / ML KIT
            sourceFromAi = "AI_SCAN"
            Toast.makeText(this, "Data berhasil diisi oleh AI!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadEventData() {
        lifecycleScope.launch {
            val event = viewModel.getEventById(eventId)
            event?.let {
                currentEvent = it
                binding.apply {
                    editTextTitle.setText(it.title)
                    autoCompleteTextViewCategory.setText(it.category, false)
                    autoCompleteTextViewPriority.setText(it.priority, false)
                    editTextDate.setText(it.date)
                    editTextTime.setText(it.time)
                    editTextEndTime.setText(it.endTime)
                    editTextLocation.setText(it.location)
                    editTextDescription.setText(it.description)
                    val notificationText = getNotificationOptionText(it.notificationMinutesBefore)
                    autoCompleteNotification.setText(notificationText, false)
                }
            }
        }
    }

    private fun saveEvent() {
        val title = binding.editTextTitle.text.toString().trim()
        val category = binding.autoCompleteTextViewCategory.text.toString().trim()
        val priority = binding.autoCompleteTextViewPriority.text.toString().trim()
        val date = binding.editTextDate.text.toString().trim()
        val time = binding.editTextTime.text.toString().trim()
        val endTime = binding.editTextEndTime.text.toString().trim()
        val location = binding.editTextLocation.text.toString().trim()
        val description = binding.editTextDescription.text.toString().trim()
        val notificationMinutes = getSelectedNotificationValue()

        if (title.isEmpty() || category.isEmpty() || date.isEmpty() || time.isEmpty() || location.isEmpty()) {
            Toast.makeText(this, "Lengkapi semua field wajib", Toast.LENGTH_SHORT).show()
            return
        }

        // 🔴 LOGIKA FALLBACK 1 JAM
        val finalEndTime = if (endTime.isEmpty()) calculateEndTimeFallback(time) else endTime

        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val currentUid = currentUser?.uid

        if (currentUid == null) {
            Toast.makeText(this, "Gagal: Anda belum login.", Toast.LENGTH_SHORT).show()
            return
        }

        val event = Event(
            id = if (isEditMode) eventId else 0,
            firestoreId = currentEvent?.firestoreId,
            userId = currentUid,
            isSynced = false,
            isDeleted = false,
            title = title,
            category = category,
            priority = priority,
            date = date,
            time = time,
            endTime = finalEndTime,
            location = location,
            description = description.ifEmpty { null },
            notificationMinutesBefore = notificationMinutes,
            inputSource = currentEvent?.inputSource ?: sourceFromAi ?: "MANUAL"
        )

        viewModel.insertOrUpdate(event)

        val message = if (isEditMode) "Event berhasil diupdate" else "Event berhasil ditambahkan"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }

    // 🔴 HELPER FALLBACK 1 JAM
    private fun calculateEndTimeFallback(startTime: String): String {
        try {
            val parts = startTime.split(":")
            if (parts.size == 2) {
                var h = parts[0].toInt()
                val m = parts[1]
                h = (h + 1) % 24
                return String.format("%02d:%s", h, m)
            }
        } catch (e: Exception) { e.printStackTrace() }
        return ""
    }

    private fun setupNotificationDropdown() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, notificationOptions)
        binding.autoCompleteNotification.setAdapter(adapter)

        if (!isEditMode) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            val defaultValue = sharedPreferences.getInt("default_notification_event", 15)
            val defaultText = getNotificationOptionText(defaultValue)
            binding.autoCompleteNotification.setText(defaultText, false)
        }
    }

    private fun getNotificationOptionText(value: Int): String {
        val index = notificationValues.indexOf(value)
        return if (index != -1) notificationOptions[index] else notificationOptions[3]
    }

    private fun getSelectedNotificationValue(): Int {
        val selectedText = binding.autoCompleteNotification.text.toString()
        val index = notificationOptions.indexOf(selectedText)
        return if (index != -1) notificationValues[index] else 15
    }

    private fun checkEditMode() {
        eventId = intent.getLongExtra("event_id", -1)
        isEditMode = eventId != -1L
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.addEventToolbar)
        supportActionBar?.apply {
            title = if (isEditMode) "Edit Jadwal Acara" else "Tambah Jadwal Acara"
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun setupPriorityDropdown() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, priorityOptions)
        binding.autoCompleteTextViewPriority.setAdapter(adapter)
        if (!isEditMode && binding.autoCompleteTextViewPriority.text.isEmpty()) {
            binding.autoCompleteTextViewPriority.setText("Sedang", false)
        }
    }

    private fun setupCategoryDropdown() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        binding.autoCompleteTextViewCategory.setAdapter(adapter)
    }

    private fun setupDateTimePickers() {
        binding.editTextDate.setOnClickListener { showDatePicker() }
        binding.editTextTime.setOnClickListener { showTimePicker(true) }
        binding.editTextEndTime.setOnClickListener { showTimePicker(false) }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, dayOfMonth ->
            val selectedDate = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            binding.editTextDate.setText(dateFormat.format(selectedDate.time))
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showTimePicker(isStartTime: Boolean) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(this, { _, hourOfDay, minute ->
            val selectedTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hourOfDay)
                set(Calendar.MINUTE, minute)
            }
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val formattedTime = timeFormat.format(selectedTime.time)

            if (isStartTime) {
                binding.editTextTime.setText(formattedTime)
            } else {
                binding.editTextEndTime.setText(formattedTime)
            }
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
    }

    private fun setupSaveButton() {
        binding.buttonSave.setOnClickListener { saveEvent() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}