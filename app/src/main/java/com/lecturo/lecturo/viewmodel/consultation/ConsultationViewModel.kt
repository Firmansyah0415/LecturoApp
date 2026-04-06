package com.lecturo.lecturo.viewmodel.consultation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.model.CalendarEntry
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
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

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
        // 1. Simpan ke Tabel Asli (Consultation)
        val newId = repository.insertSchedule(schedule)
        val savedSchedule = schedule.copy(id = newId)

        // 2. Jadwalkan Notifikasi
        notificationScheduler.scheduleConsultation(savedSchedule)

        // 3. SYNC: Simpan ke CalendarEntry (Untuk Home Fragment)
        // Jika notifikasinya >= 0 atau memang ingin selalu tampil di kalender
        val dateForCalendar = convertDateToOldFormat(savedSchedule.date)

        val entry = CalendarEntry(
            title = savedSchedule.title,
            date = dateForCalendar,
            time = savedSchedule.startTime,
            category = "Konsultasi",
            sourceFeatureType = "CONSULTATION",
            sourceFeatureId = newId,
            notificationMinutesBefore = savedSchedule.notificationMinutesBefore
            // isCompleted dihapus karena tidak ada di Entity CalendarEntry
        )
        calendarRepository.insertEntry(entry)
    }

    fun updateSchedule(schedule: ConsultationSchedule) = viewModelScope.launch {
        // 1. Update Tabel Asli
        repository.updateSchedule(schedule)

        // 2. Reset Notifikasi
        notificationScheduler.cancelConsultation(schedule.id)
        if (schedule.status == "SCHEDULED") {
            notificationScheduler.scheduleConsultation(schedule)
        }

        // 3. SYNC: Update CalendarEntry
        // Hapus entri lama dulu (best practice di app ini untuk menghindari duplikat jika tanggal berubah)
        calendarRepository.deleteEntriesForSource("CONSULTATION", schedule.id)

        // Masukkan lagi ke CalendarEntry HANYA JIKA statusnya bukan CANCELLED
        // Jika COMPLETED, tetap masuk agar tercatat di agenda (tapi tidak dicoret di Home, karena keterbatasan CalendarEntry)
        if (schedule.status != "CANCELLED") {
            val dateForCalendar = convertDateToOldFormat(schedule.date)
            val entry = CalendarEntry(
                title = schedule.title,
                date = dateForCalendar,
                time = schedule.startTime,
                category = "Konsultasi",
                sourceFeatureType = "CONSULTATION",
                sourceFeatureId = schedule.id,
                notificationMinutesBefore = schedule.notificationMinutesBefore
                // isCompleted dihapus
            )
            calendarRepository.insertEntry(entry)
        }
    }

    fun deleteSchedule(schedule: ConsultationSchedule) = viewModelScope.launch {
        // 1. Hapus Tabel Asli
        repository.deleteSchedule(schedule)

        // 2. Hapus Notifikasi
        notificationScheduler.cancelConsultation(schedule.id)

        // 3. SYNC: Hapus dari CalendarEntry
        calendarRepository.deleteEntriesForSource("CONSULTATION", schedule.id)
    }

    // Helper untuk konversi yyyy-MM-dd -> dd/MM/yyyy
    private fun convertDateToOldFormat(isoDate: String): String {
        return try {
            val input = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val output = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val date = input.parse(isoDate)
            output.format(date!!)
        } catch (e: Exception) {
            isoDate // Fallback jika error
        }
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