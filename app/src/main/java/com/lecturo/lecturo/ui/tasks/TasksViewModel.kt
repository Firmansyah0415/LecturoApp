package com.lecturo.lecturo.ui.tasks

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lecturo.lecturo.Schedule

class TasksViewModel(private val repository: ScheduleRepository) : ViewModel() {

    private val _schedules = MutableLiveData<List<Schedule>>()
    val schedules: LiveData<List<Schedule>> = _schedules

    fun loadSchedules() {
        _schedules.value = repository.getAllSchedules()
    }

    fun deleteSchedule(scheduleId: Long) {
        repository.deleteSchedule(scheduleId)
        loadSchedules() // Refresh after delete
    }
}
