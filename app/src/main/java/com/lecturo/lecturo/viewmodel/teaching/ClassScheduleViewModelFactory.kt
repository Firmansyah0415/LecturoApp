package com.lecturo.lecturo.viewmodel.teaching

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lecturo.lecturo.data.repository.CalendarRepository

class ClassScheduleViewModelFactory(
    private val repository: CalendarRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ClassScheduleViewModel::class.java)) {
            return ClassScheduleViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
