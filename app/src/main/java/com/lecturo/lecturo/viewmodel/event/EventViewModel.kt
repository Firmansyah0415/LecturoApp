package com.lecturo.lecturo.viewmodel.event

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.lecturo.lecturo.data.model.Event
import com.lecturo.lecturo.data.repository.CalendarRepository
import com.lecturo.lecturo.data.repository.EventRepository
import com.lecturo.lecturo.utils.AiExtractionHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EventViewModel(
    private val eventRepository: EventRepository,
    private val calendarRepository: CalendarRepository,
    application: Application
) : AndroidViewModel(application) {

    private val allEvents = eventRepository.getAllEvents()
    private val _categoryFilter = MutableLiveData<String>("")
    private val _searchQuery = MutableLiveData<String>("")

    // 🔴 [TAMBAHAN] Filter Status & Sortir
    private val _statusFilter = MutableLiveData<String>("Semua")
    private val _isSortNewest = MutableLiveData<Boolean>(true)

    val categoryFilter: LiveData<String> = _categoryFilter
    val searchQuery: LiveData<String> = _searchQuery
    val statusFilter: LiveData<String> = _statusFilter
    val isSortNewest: LiveData<Boolean> = _isSortNewest
    val categories = eventRepository.getAllCategories()

    val filteredEvents = MediatorLiveData<List<Event>>().apply {
        addSource(allEvents) { value = applyAllFilters(it, _categoryFilter.value, _searchQuery.value, _statusFilter.value, _isSortNewest.value) }
        addSource(_categoryFilter) { value = applyAllFilters(allEvents.value, it, _searchQuery.value, _statusFilter.value, _isSortNewest.value) }
        addSource(_searchQuery) { value = applyAllFilters(allEvents.value, _categoryFilter.value, it, _statusFilter.value, _isSortNewest.value) }
        addSource(_statusFilter) { value = applyAllFilters(allEvents.value, _categoryFilter.value, _searchQuery.value, it, _isSortNewest.value) }
        addSource(_isSortNewest) { value = applyAllFilters(allEvents.value, _categoryFilter.value, _searchQuery.value, _statusFilter.value, it) }
    }

    private fun applyAllFilters(events: List<Event>?, category: String?, query: String?, status: String?, sortNewest: Boolean?): List<Event> {
        if (events == null) return emptyList()
        var filtered = events

        // 1. Kategori
        if (!category.isNullOrBlank() && category != "Semua") {
            filtered = filtered.filter { it.category == category }
        }

        // 2. Search
        if (!query.isNullOrBlank()) {
            val lowerCaseQuery = query.lowercase(Locale.getDefault())
            filtered = filtered.filter { event ->
                event.title.lowercase(Locale.getDefault()).contains(lowerCaseQuery) ||
                        event.description?.lowercase(Locale.getDefault())?.contains(lowerCaseQuery) == true ||
                        (event.location?.lowercase(Locale.getDefault())?.contains(lowerCaseQuery) == true)
            }
        }

        // 3. Status (Selesai/Belum)
        if (status == "Selesai") filtered = filtered.filter { it.isCompleted }
        if (status == "Belum") filtered = filtered.filter { !it.isCompleted }

        // 4. Sortir
        val inFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        filtered = filtered.sortedWith { e1, e2 ->
            val d1 = try { inFormat.parse(e1.date) } catch (e: Exception) { Date(0) }
            val d2 = try { inFormat.parse(e2.date) } catch (e: Exception) { Date(0) }
            if (sortNewest == true) d2.compareTo(d1) else d1.compareTo(d2)
        }

        return filtered
    }

    fun toggleSort() { _isSortNewest.value = !(_isSortNewest.value ?: true) }
    fun setStatusFilter(status: String) { _statusFilter.value = status }
    fun setCategoryFilter(category: String) { _categoryFilter.value = category }
    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun clearFilters() {
        _categoryFilter.value = ""
        _searchQuery.value = ""
        _statusFilter.value = "Semua"
    }

    // --- AI Helper ---
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
                val result = aiHelper.extractEventFromUri(uri, isPdf)
                result.onSuccess { event -> _extractedEvent.value = event }
                result.onFailure { error -> _errorMessage.value = "Gagal Scan: ${error.message}" }
            } catch (e: Exception) {
                _errorMessage.value = "Error: ${e.message}"
            } finally {
                _ocrLoading.value = false
            }
        }
    }
    fun onEventExtractedHandled() { _extractedEvent.value = null }

    fun insertOrUpdate(event: Event) = viewModelScope.launch { eventRepository.insertOrUpdate(event) }
    fun delete(eventId: Long) = viewModelScope.launch { eventRepository.deleteById(eventId) }
    fun updateCompletedStatus(eventId: Long, isCompleted: Boolean) = viewModelScope.launch { eventRepository.updateCompletedStatus(eventId, isCompleted) }
    suspend fun getEventById(eventId: Long): Event? = eventRepository.getEventById(eventId)
}