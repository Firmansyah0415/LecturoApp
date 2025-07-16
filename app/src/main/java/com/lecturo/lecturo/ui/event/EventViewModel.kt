package com.lecturo.lecturo.ui.event

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.lecturo.lecturo.data.model.CalendarEntry
import com.lecturo.lecturo.data.model.Event
import com.lecturo.lecturo.data.repository.CalendarRepository
import com.lecturo.lecturo.data.repository.EventRepository
import com.lecturo.lecturo.notifications.NotificationScheduler
import kotlinx.coroutines.launch
import java.util.Locale

class EventViewModel(
    private val eventRepository: EventRepository,
    private val calendarRepository: CalendarRepository,
    application: Application
) : AndroidViewModel(application) {

    private val allEvents = eventRepository.getAllEvents()
    private val _categoryFilter = MutableLiveData<String>("") // Default ke string kosong
    private val _searchQuery = MutableLiveData<String>("")   // Default ke string kosong

    val categoryFilter: LiveData<String> = _categoryFilter
    val searchQuery: LiveData<String> = _searchQuery

    val filteredEvents = MediatorLiveData<List<Event>>().apply {
        addSource(allEvents) { events -> value = applyFilters(events, _categoryFilter.value, _searchQuery.value) }
        addSource(_categoryFilter) { category -> value = applyFilters(allEvents.value, category, _searchQuery.value) }
        addSource(_searchQuery) { query -> value = applyFilters(allEvents.value, _categoryFilter.value, query) }
    }

    val categories = eventRepository.getAllCategories()

    private fun applyFilters(events: List<Event>?, category: String?, query: String?): List<Event> {
        if (events == null) return emptyList()
        var filtered = events
        if (!category.isNullOrBlank() && category != "Semua") {
            filtered = filtered.filter { it.category == category }
        }
        if (!query.isNullOrBlank()) {
            val lowerCaseQuery = query.lowercase(Locale.getDefault())
            filtered = filtered.filter { event ->
                event.title.lowercase(Locale.getDefault()).contains(lowerCaseQuery) ||
                        event.description?.lowercase(Locale.getDefault())?.contains(lowerCaseQuery) == true ||
                        event.location.lowercase(Locale.getDefault()).contains(lowerCaseQuery)
            }
        }
        return filtered
    }

    // --- FUNGSI YANG DIPERBARUI ---
    // Sekarang hanya menerima satu parameter: objek Event yang sudah lengkap
    fun insertOrUpdate(event: Event) = viewModelScope.launch {
        // 1. Batalkan alarm lama jika ada (untuk mode edit)
        if (event.id != 0L) {
            val scheduler = NotificationScheduler(getApplication())
            val oldEntries = calendarRepository.getEntriesForSource("EVENT", event.id)
            oldEntries.forEach { oldEntry ->
                scheduler.cancelNotification(oldEntry.notificationId)
            }
            calendarRepository.deleteEntriesForSource("EVENT", event.id)
        }

        // 2. Simpan event asli dan dapatkan ID-nya
        val eventId = eventRepository.insertOrUpdate(event)
        val finalEvent = event.copy(id = eventId)

        // 3. Buat entri kalender
        val calendarEntry = CalendarEntry(
            title = finalEvent.title,
            date = finalEvent.date,
            time = finalEvent.time,
            category = finalEvent.category,
            sourceFeatureType = "EVENT",
            sourceFeatureId = finalEvent.id,
            // Ambil nilai notifikasi langsung dari objek Event
            notificationMinutesBefore = finalEvent.notificationMinutesBefore
        )
        val calendarEntryId = calendarRepository.insertEntry(calendarEntry)
        val finalCalendarEntry = calendarEntry.copy(id = calendarEntryId)

        // 4. Jadwalkan notifikasi baru
        if (finalEvent.notificationMinutesBefore >= 0) {
            val scheduler = NotificationScheduler(getApplication())
            scheduler.scheduleNotification(finalCalendarEntry)
        }
    }

    fun delete(eventId: Long) = viewModelScope.launch {
        val scheduler = NotificationScheduler(getApplication())
        val entriesToDelete = calendarRepository.getEntriesForSource("EVENT", eventId)
        entriesToDelete.forEach { entry ->
            scheduler.cancelNotification(entry.notificationId)
        }
        eventRepository.deleteById(eventId)
        calendarRepository.deleteEntriesForSource("EVENT", eventId)
    }

    fun updateCompletedStatus(eventId: Long, isCompleted: Boolean) = viewModelScope.launch {
        eventRepository.updateCompletedStatus(eventId, isCompleted)
        if (isCompleted) {
            val scheduler = NotificationScheduler(getApplication())
            val entriesToCancel = calendarRepository.getEntriesForSource("EVENT", eventId)
            entriesToCancel.forEach { entry ->
                scheduler.cancelNotification(entry.notificationId)
            }
        }
    }

    fun setCategoryFilter(category: String) { _categoryFilter.value = category }
    fun setSearchQuery(query: String) { _searchQuery.value = query }

    // PERBAIKAN: Menggunakan string kosong agar tidak nullable
    fun clearFilters() {
        _categoryFilter.value = ""
        _searchQuery.value = ""
    }

    suspend fun getEventById(eventId: Long): Event? = eventRepository.getEventById(eventId)
}
