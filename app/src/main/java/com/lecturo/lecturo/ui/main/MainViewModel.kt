package com.lecturo.lecturo.ui.main

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
import com.lecturo.lecturo.data.repository.EventRepository
import com.lecturo.lecturo.data.repository.TasksRepository
import com.lecturo.lecturo.data.repository.TeachingRepository
import com.lecturo.lecturo.data.repository.UserRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// --- KELAS EVENT DITARUH DI SINI (DI LUAR MAINVIEWMODEL) ---
// Ini adalah kelas wrapper untuk event agar tidak diproses berulang kali
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
    private val calendarRepository: CalendarRepository
) : ViewModel() {

    private val _isRefreshing = MutableLiveData<Boolean>()
    val isRefreshing: LiveData<Boolean> = _isRefreshing

    // Sesi Pengguna
    fun getSession(): LiveData<UserModel> {
        return userRepository.getSession().asLiveData()
    }

    fun logout() {
        viewModelScope.launch {
            userRepository.logout()
        }
    }

    val userName: LiveData<String> = getSession().map { user ->
        user.email.split('@').firstOrNull()?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } ?: "Pengguna"
    }

    val greetingText: LiveData<String> = getSession().map {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good Morning,"
            in 12..14 -> "Good Afternoon,"
            in 15..17 -> "Good Afternoon,"
            else -> "Good Evening,"
        }
    }

    // LiveData untuk Statistik di Kartu (Reaktif)
    val taskCount: LiveData<Int> = tasksRepository.getAllTasks().map { tasks ->
        tasks.count { !it.completed }
    }

    val eventCount: LiveData<Int> = eventRepository.getAllEvents().map { events ->
        events.count { !it.isCompleted }
    }

    val teachingRuleCount: LiveData<Int> = teachingRepository.getAllRules().map { it.size }

    val consultationCount: LiveData<Int> = calendarRepository.getEntriesByCategory("Konsultasi").map { it.size }

    // LiveData untuk Agenda (Reaktif terhadap tanggal yang dipilih)
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
        _selectedDate.value = _selectedDate.value
        _isRefreshing.value = false
    }

    // --- KODE UNTUK FAB DITARUH DI SINI (DI DALAM MAINVIEWMODEL) ---
    // LiveData untuk event klik FAB
    private val _fabClickEvent = MutableLiveData<Event<Unit>>()
    val fabClickEvent: LiveData<Event<Unit>> = _fabClickEvent

    // Fungsi ini akan dipanggil dari MainActivity saat FAB diklik
    fun onFabClicked() {
        _fabClickEvent.value = Event(Unit)
    }
}
