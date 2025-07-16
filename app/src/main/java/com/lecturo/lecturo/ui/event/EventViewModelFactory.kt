package com.lecturo.lecturo.ui.event

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lecturo.lecturo.data.repository.CalendarRepository
import com.lecturo.lecturo.data.repository.EventRepository

class EventViewModelFactory(
    private val eventRepository: EventRepository,
    private val calendarRepository: CalendarRepository,
    private val application: Application
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EventViewModel::class.java)) {
            return EventViewModel(eventRepository, calendarRepository, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
