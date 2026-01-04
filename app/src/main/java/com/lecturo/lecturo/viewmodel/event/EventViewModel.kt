package com.lecturo.lecturo.viewmodel.event

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
import android.net.Uri
import com.lecturo.lecturo.utils.AiExtractionHelper

class EventViewModel(
    private val eventRepository: EventRepository,
    private val calendarRepository: CalendarRepository,
    application: Application
) : AndroidViewModel(application) {

    private val allEvents = eventRepository.getAllEvents()
    private val _categoryFilter = MutableLiveData<String>("")
    private val _searchQuery = MutableLiveData<String>("")

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
                        (event.location?.lowercase(Locale.getDefault())?.contains(lowerCaseQuery) == true)
            }
        }
        return filtered
    }

    // --- TAMBAHAN BARU UNTUK AI ---
    private val aiHelper = AiExtractionHelper(application)

    private val _ocrLoading = MutableLiveData<Boolean>()
    val ocrLoading: LiveData<Boolean> get() = _ocrLoading

    private val _extractedEvent = MutableLiveData<Event?>()
    val extractedEvent: LiveData<Event?> get() = _extractedEvent

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> get() = _errorMessage

    fun extractEventFromImageOrPdf(uri: Uri, isPdf: Boolean) {
        _ocrLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                // Panggil Helper (OCR -> Repository -> Backend)
                val result = aiHelper.extractEventFromUri(uri, isPdf)

                result.onSuccess { event ->
                    _extractedEvent.value = event
                }
                result.onFailure { error ->
                    _errorMessage.value = "Gagal Scan: ${error.message}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error: ${e.message}"
            } finally {
                _ocrLoading.value = false
            }
        }
    }

    // Reset state setelah data dipakai di Activity
    fun onEventExtractedHandled() {
        _extractedEvent.value = null
    }

    fun insertOrUpdate(event: Event) = viewModelScope.launch {
        if (event.id != 0L) {
            val scheduler = NotificationScheduler(getApplication())
            val oldEntries = calendarRepository.getEntriesForSource("EVENT", event.id)
            oldEntries.forEach { oldEntry ->
                scheduler.cancelNotification(oldEntry.notificationId)
            }
            calendarRepository.deleteEntriesForSource("EVENT", event.id)
        }

        val eventId = eventRepository.insertOrUpdate(event)
        val finalEvent = event.copy(id = eventId)

        val calendarEntry = CalendarEntry(
            title = finalEvent.title,
            date = finalEvent.date,
            time = finalEvent.time,
            category = finalEvent.category,
            sourceFeatureType = "EVENT",
            sourceFeatureId = finalEvent.id,
            notificationMinutesBefore = finalEvent.notificationMinutesBefore
        )
        val calendarEntryId = calendarRepository.insertEntry(calendarEntry)
        val finalCalendarEntry = calendarEntry.copy(id = calendarEntryId)

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
        // CARA LAMA (Hanya Lokal):
        // eventRepository.updateCompletedStatus(eventId, isCompleted)

        // CARA BARU (Lokal + Cloud Sync):
        // 1. Ambil data event saat ini
        val currentEvent = eventRepository.getEventById(eventId)

        currentEvent?.let { event ->
            // 2. Ubah statusnya
            val updatedEvent = event.copy(isCompleted = isCompleted)

            // 3. Panggil insertOrUpdate agar otomatis trigger sync ke backend
            insertOrUpdate(updatedEvent)
        }

        // Logic Notifikasi (Tetap sama)
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

    fun clearFilters() {
        _categoryFilter.value = ""
        _searchQuery.value = ""
    }

    suspend fun getEventById(eventId: Long): Event? = eventRepository.getEventById(eventId)
}