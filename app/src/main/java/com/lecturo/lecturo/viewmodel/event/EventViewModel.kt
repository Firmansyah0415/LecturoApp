package com.lecturo.lecturo.viewmodel.event

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.lecturo.lecturo.data.model.Event
import com.lecturo.lecturo.data.repository.CalendarRepository
import com.lecturo.lecturo.data.repository.EventRepository
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
        // Sepenuhnya diserahkan ke Repository
        eventRepository.insertOrUpdate(event)
    }

    fun delete(eventId: Long) = viewModelScope.launch {
        // Sepenuhnya diserahkan ke Repository
        eventRepository.deleteById(eventId)
    }

    fun updateCompletedStatus(eventId: Long, isCompleted: Boolean) = viewModelScope.launch {
        // Sepenuhnya diserahkan ke Repository
        eventRepository.updateCompletedStatus(eventId, isCompleted)
    }

    fun setCategoryFilter(category: String) { _categoryFilter.value = category }
    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun clearFilters() {
        _categoryFilter.value = ""
        _searchQuery.value = ""
    }

    suspend fun getEventById(eventId: Long): Event? = eventRepository.getEventById(eventId)
}