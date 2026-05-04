package com.lecturo.lecturo.viewmodel.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.lecturo.lecturo.data.model.CalendarEntry
import com.lecturo.lecturo.data.pref.UserModel
import com.lecturo.lecturo.data.repository.CalendarRepository
import com.lecturo.lecturo.data.repository.ConsultationRepository
import com.lecturo.lecturo.data.repository.EventRepository
import com.lecturo.lecturo.data.repository.TasksRepository
import com.lecturo.lecturo.data.repository.TeachingRepository
import com.lecturo.lecturo.data.repository.UserRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class DateItem(
    val date: Date,
    val isSelected: Boolean = false,
    val isToday: Boolean = false,
    val categories: Set<String> = emptySet() // Untuk indikator 4 warna
)

class MainViewModel(
    private val userRepository: UserRepository,
    private val tasksRepository: TasksRepository,
    private val eventRepository: EventRepository,
    private val teachingRepository: TeachingRepository,
    private val calendarRepository: CalendarRepository,
    private val consultationRepository: ConsultationRepository
) : ViewModel() {

    private val _isRefreshing = MutableLiveData<Boolean>()
    val isRefreshing: LiveData<Boolean> = _isRefreshing

    private val todayDate: String
        get() = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

    // 1. PINDAHKAN _selectedDate KE ATAS SINI
    private val _selectedDate = MutableLiveData<Date>().apply { value = Date() }
    val selectedDate: LiveData<Date> = _selectedDate

    private val allEntries = calendarRepository.getAllEntries()

    private val _calendarDates = MediatorLiveData<List<DateItem>>()
    val calendarDates: LiveData<List<DateItem>> = _calendarDates

    // 2. SEKARANG BLOK INIT BISA MEMBACA _selectedDate KARENA SUDAH DIBUAT DI ATASNYA
    init {
        _calendarDates.addSource(_selectedDate) { generateCalendarDates() }
        _calendarDates.addSource(allEntries) { generateCalendarDates() }
    }

    private fun generateCalendarDates() {
        val calendar = Calendar.getInstance()
        val today = Date()
        val dates = mutableListOf<DateItem>()
        val selected = _selectedDate.value ?: today

        // Ambil data entry yang saat ini ada
        val entries = allEntries.value ?: emptyList()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        calendar.add(Calendar.DAY_OF_MONTH, -7)
        repeat(15) {
            val date = calendar.time
            val dateString = dateFormat.format(date)

            // PERBAIKAN: Normalisasi kategori agar indikator warna di kalender tidak dobel
            val categoriesForDay = entries
                .filter { it.date == dateString }
                .map { entry ->
                    when (entry.sourceFeatureType) {
                        "TASK" -> "tugas"
                        "TEACHING_RULE" -> "mengajar"
                        "CONSULTATION", "Konsultasi" -> "konsultasi"
                        "EVENT" -> "rapat" // Cukup gunakan 1 kata kunci perwakilan Event
                        else -> entry.category
                    }
                }
                .toSet()

            dates.add(
                DateItem(
                    date = date,
                    isSelected = isSameDay(date, selected),
                    isToday = isSameDay(date, today),
                    categories = categoriesForDay
                )
            )
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        _calendarDates.postValue(dates)
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    // --- SESI PENGGUNA ---
    fun getSession(): LiveData<UserModel> {
        return userRepository.getSession().asLiveData()
    }

    fun logout(onSuccess: () -> Unit) {
        viewModelScope.launch {
            userRepository.logout()
            onSuccess()
        }
    }

    val userName: LiveData<String> = getSession().map { user ->
        user.email.split('@').firstOrNull()?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } ?: "Pengguna"
    }

    val greetingText: LiveData<String> = getSession().map {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Selamat Pagi,"
            in 12..14 -> "Selamat Siang,"
            in 15..17 -> "Selamat Sore,"
            else -> "Selamat Malam,"
        }
    }

    // --- STATISTIK (PROGRESS & COUNT) ---
    // Buat data class kecil untuk menampung progress
    data class StatProgress(val completed: Int, val total: Int)

    val taskStats: LiveData<StatProgress> = tasksRepository.getAllTasks().map { tasks ->
        StatProgress(tasks.count { it.isCompleted }, tasks.size)
    }

    val eventStats: LiveData<StatProgress> = eventRepository.getAllEvents().map { events ->
        StatProgress(events.count { it.isCompleted }, events.size)
    }

    val consultationStats: LiveData<StatProgress> = consultationRepository.getAllSchedules().map { schedules ->
        // Anggap status selain "SCHEDULED" (misal "COMPLETED" atau "CANCELLED") sebagai selesai dari daftar tunggu
        val total = schedules.size
        // PERBAIKAN: Gunakan ignoreCase = true agar tahan terhadap perbedaan huruf besar/kecil
        val pending = schedules.count { it.status.equals("SCHEDULED", ignoreCase = true) }
        StatProgress(total - pending, total)
    }

    // Mengajar: Hanya hitung kelas yang hari harinya sama dengan HARI INI
    val todayTeachingCount: LiveData<Int> = teachingRepository.getAllRules().map { rules ->
        val todayName = SimpleDateFormat("EEEE", Locale("id", "ID")).format(Date())
        rules.count { it.dayOfWeek.equals(todayName, ignoreCase = true) }
    }

    // --- AGENDA HARIAN BAWAH ---
    val todaysAgenda: LiveData<List<CalendarEntry>> = _selectedDate.switchMap { date ->
        val dateString = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)
        calendarRepository.getEntriesForDate(dateString)
    }

    fun loadAgendaForDate(date: Date) {
        _selectedDate.value = date
    }

    fun refreshData() {
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                tasksRepository.syncTasksFromCloud()
                eventRepository.syncEventsFromCloud()
                teachingRepository.syncTeachingFromCloud()
                consultationRepository.syncConsultationsFromCloud()
                _selectedDate.value = _selectedDate.value
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}