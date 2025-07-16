package com.lecturo.lecturo.ui.task

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lecturo.lecturo.data.repository.CalendarRepository
import com.lecturo.lecturo.data.repository.TasksRepository

// PERBAIKAN: Tambahkan CalendarRepository dan Application sebagai parameter
class TasksViewModelFactory(
    private val tasksRepository: TasksRepository,
    private val calendarRepository: CalendarRepository,
    private val application: Application
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TasksViewModel::class.java)) {
            return TasksViewModel(tasksRepository, calendarRepository, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
