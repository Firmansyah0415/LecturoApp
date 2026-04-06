package com.lecturo.lecturo.viewmodel.main

import androidx.lifecycle.LiveData
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

// Kelas wrapper untuk event (Klik FAB)
open class Event<out T>(private val content: T) {
    var hasBeenHandled = false
        private set

    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }
}

class MainViewModel(
    private val userRepository: UserRepository,
    private val tasksRepository: TasksRepository,
    private val eventRepository: EventRepository,
    private val teachingRepository: TeachingRepository,
    private val calendarRepository: CalendarRepository,
    private val consultationRepository: ConsultationRepository // Tetap butuh ini untuk hitung angka statistik
) : ViewModel() {

    private val _isRefreshing = MutableLiveData<Boolean>()
    val isRefreshing: LiveData<Boolean> = _isRefreshing

    // --- HELPER DATE ---
    private val todayDate: String
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

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

    // --- STATISTIK (COUNT) ---

    // 1. Task: Hitung yang belum selesai
    val taskCount: LiveData<Int> = tasksRepository.getAllTasks().map { tasks ->
        tasks.count { !it.isCompleted }
    }

    // 2. Event: Hitung yang belum selesai
    val eventCount: LiveData<Int> = eventRepository.getAllEvents().map { events ->
        events.count { !it.isCompleted }
    }

    // 3. Teaching: Jumlah Aturan Mengajar
    val teachingRuleCount: LiveData<Int> = teachingRepository.getAllRules().map { it.size }

    // 4. Consultation: Hitung Jadwal 'SCHEDULED' (Menggunakan Repo Asli agar akurat)
    val consultationCount: LiveData<Int> = consultationRepository.getUpcomingSchedules(todayDate).map { schedules ->
        schedules.count { it.status == "SCHEDULED" }
    }

    // --- AGENDA (VERSI BERSIH / CLEAN) ---
    // Sekarang Agenda hanya membaca dari satu sumber: CalendarRepository
    // Karena ConsultationViewModel sudah otomatis Sync data ke sana saat Save/Update.

    private val _selectedDate = MutableLiveData<Date>().apply { value = Date() }
    val selectedDate: LiveData<Date> = _selectedDate

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
                // Tarik semua data dari Cloud (Paralel)
                tasksRepository.syncTasksFromCloud()
                eventRepository.syncEventsFromCloud()
                teachingRepository.syncTeachingFromCloud()
                consultationRepository.syncConsultationsFromCloud()

                // Trik untuk memicu ulang LiveData kalender
                _selectedDate.value = _selectedDate.value
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // Matikan animasi loading
                _isRefreshing.value = false
            }
        }
    }

    // --- FAB ---
    private val _fabClickEvent = MutableLiveData<Event<Unit>>()
    val fabClickEvent: LiveData<Event<Unit>> = _fabClickEvent

    fun onFabClicked() {
        _fabClickEvent.value = Event(Unit)
    }
}