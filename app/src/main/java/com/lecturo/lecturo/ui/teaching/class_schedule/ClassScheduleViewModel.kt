package com.lecturo.lecturo.ui.teaching.class_schedule

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.lecturo.lecturo.data.model.CalendarEntry
import com.lecturo.lecturo.data.repository.CalendarRepository

class ClassScheduleViewModel(private val repository: CalendarRepository) : ViewModel() {

    val teachingEntries: LiveData<List<CalendarEntry>> = repository.getEntriesByCategory("Mengajar")
}
