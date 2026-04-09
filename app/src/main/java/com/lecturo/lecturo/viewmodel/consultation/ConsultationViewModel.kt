package com.lecturo.lecturo.viewmodel.consultation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.model.ConsultationPattern
import com.lecturo.lecturo.data.model.ConsultationSchedule
import com.lecturo.lecturo.data.repository.CalendarRepository
import com.lecturo.lecturo.data.repository.ConsultationRepository
import com.lecturo.lecturo.notifications.NotificationScheduler
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConsultationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ConsultationRepository
    private val calendarRepository: CalendarRepository
    private val notificationScheduler = NotificationScheduler(application)

    // Filter Trigger
    private val _filterType = MutableLiveData<FilterType>(FilterType.UPCOMING)
    private val _searchQuery = MutableLiveData<String>("")

    init {
        val db = AppDatabase.getDatabase(application)
        val consultationDao = db.consultationDao()
        val calendarDao = db.calendarEntryDao() // <-- Ambil DAO lama

        repository = ConsultationRepository(consultationDao, application.applicationContext)
        calendarRepository = CalendarRepository(calendarDao) // <-- Init Repo lama
    }

    // ================= DATA STREAMS =================

    private val todayDate: String
        get() = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

    val schedules: LiveData<List<ConsultationSchedule>> = _filterType.switchMap { filter ->
        when (filter) {
            FilterType.UPCOMING -> repository.getUpcomingSchedules(todayDate)
            FilterType.TODAY -> repository.getSchedulesByDate(todayDate)
            FilterType.HISTORY -> repository.getHistorySchedules(todayDate)
            FilterType.SEARCH -> repository.searchSchedules(_searchQuery.value ?: "")
        }
    }

    val activePatterns: LiveData<List<ConsultationPattern>> = repository.getActivePatterns()

    // ================= ACTIONS (YANG SUDAH DIPERBAIKI) =================

    fun setFilter(type: FilterType) {
        _filterType.value = type
    }

    fun search(query: String) {
        _searchQuery.value = query
        if (query.isNotEmpty()) {
            _filterType.value = FilterType.SEARCH
        } else {
            _filterType.value = FilterType.UPCOMING
        }
    }

// --- FUNGSI CRUD + NOTIFIKASI + SYNC ---
    fun insertSchedule(schedule: ConsultationSchedule) = viewModelScope.launch {
        repository.insertSchedule(schedule)
    }

    fun updateSchedule(schedule: ConsultationSchedule) = viewModelScope.launch {
        repository.updateSchedule(schedule)
    }

    fun deleteSchedule(schedule: ConsultationSchedule) = viewModelScope.launch {
        repository.deleteSchedule(schedule)
    }

    // Helper untuk mengambil satu data
    fun getScheduleById(id: Long, onResult: (ConsultationSchedule?) -> Unit) = viewModelScope.launch {
        val schedule = repository.getScheduleById(id)
        onResult(schedule)
    }

    // ================= LOGIC MANAJEMEN POLA (PATTERN) =================

    // LiveData untuk list di halaman pengaturan
    val allPatterns: LiveData<List<ConsultationPattern>> = repository.getAllPatterns()

    fun savePattern(pattern: ConsultationPattern) = viewModelScope.launch {
        if (pattern.id == 0L) {
            repository.insertPattern(pattern)
        } else {
            repository.updatePattern(pattern)
        }
    }

    fun deletePattern(pattern: ConsultationPattern) = viewModelScope.launch {
        repository.deletePattern(pattern)
    }

    // Update status aktif/nonaktif via Switch
    fun togglePatternStatus(pattern: ConsultationPattern, isActive: Boolean) = viewModelScope.launch {
        val updatedPattern = pattern.copy(isActive = isActive)
        repository.updatePattern(updatedPattern)
    }
}

enum class FilterType {
    UPCOMING, TODAY, HISTORY, SEARCH
}