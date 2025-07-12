package com.lecturo.lecturo.ui.event

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lecturo.lecturo.data.model.Event
import com.lecturo.lecturo.data.repository.EventRepository
import kotlinx.coroutines.launch

class EventViewModel(private val repository: EventRepository) : ViewModel() {

    private val allEvents = repository.getAllEvents()
    private val _categoryFilter = MutableLiveData<String>()
    private val _searchQuery = MutableLiveData<String>()

    val categoryFilter: LiveData<String> = _categoryFilter
    val searchQuery: LiveData<String> = _searchQuery

    // MediatorLiveData untuk menggabungkan filter
    val filteredEvents = MediatorLiveData<List<Event>>().apply {
        addSource(allEvents) { events ->
            value = applyFilters(events, _categoryFilter.value, _searchQuery.value)
        }
        addSource(_categoryFilter) { category ->
            value = applyFilters(allEvents.value, category, _searchQuery.value)
        }
        addSource(_searchQuery) { query ->
            value = applyFilters(allEvents.value, _categoryFilter.value, query)
        }
    }

    val categories = repository.getAllCategories()

    private fun applyFilters(
        events: List<Event>?,
        category: String?,
        query: String?
    ): List<Event> {
        if (events == null) return emptyList()

        var filtered = events

        // Filter by category
        if (!category.isNullOrEmpty() && category != "Semua") {
            filtered = filtered.filter { it.category == category }
        }

        // Filter by search query
        if (!query.isNullOrEmpty()) {
            filtered = filtered.filter { event ->
                event.title.contains(query, ignoreCase = true) ||
                        event.description?.contains(query, ignoreCase = true) == true ||
                        event.location.contains(query, ignoreCase = true)
            }
        }

        return filtered
    }

    fun insertOrUpdate(event: Event) {
        viewModelScope.launch {
            repository.insertOrUpdate(event)
        }
    }

    fun delete(eventId: Long) {
        viewModelScope.launch {
            repository.deleteById(eventId)
        }
    }

    fun updateCompletedStatus(eventId: Long, isCompleted: Boolean) {
        viewModelScope.launch {
            repository.updateCompletedStatus(eventId, isCompleted)
        }
    }

    fun setCategoryFilter(category: String) {
        _categoryFilter.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearFilters() {
        _categoryFilter.value = null
        _searchQuery.value = null
    }

    suspend fun getEventById(eventId: Long): Event? {
        return repository.getEventById(eventId)
    }
}