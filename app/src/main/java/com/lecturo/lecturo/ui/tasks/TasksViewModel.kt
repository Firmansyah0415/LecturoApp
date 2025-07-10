package com.lecturo.lecturo.ui.tasks

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.lecturo.lecturo.Schedule
import kotlinx.coroutines.launch
import java.util.Locale

class TasksViewModel(private val repository: ScheduleRepository) : ViewModel() {

    // Sumber data utama dari database
    private val allSchedules: LiveData<List<Schedule>> = repository.getAllSchedules()

    // LiveData untuk menampung query pencarian dari pengguna
    private val searchQuery = MutableLiveData<String>("")

    // MediatorLiveData menggabungkan `allSchedules` dan `searchQuery`.
    private val filteredSchedules = MediatorLiveData<List<Schedule>>().apply {

        fun filterSchedules() {
            val query = searchQuery.value.orEmpty()
            val schedules = allSchedules.value ?: emptyList()

            value = if (query.isBlank()) {
                schedules
            } else {
                val lowerCaseQuery = query.lowercase(Locale.getDefault())
                schedules.filter {
                    it.title.lowercase(Locale.getDefault()).contains(lowerCaseQuery) ||
                            it.description?.lowercase(Locale.getDefault())?.contains(lowerCaseQuery) == true
                }
            }
        }

        addSource(allSchedules) { filterSchedules() }
        addSource(searchQuery) { filterSchedules() }
    }

    // Jadwal 'Pending' dan 'Completed' sekarang berasal dari daftar yang sudah difilter
    val pendingSchedules: LiveData<List<Schedule>> = filteredSchedules.map { schedules ->
        schedules.filter { !it.completed }
    }

    // --- PERBAIKAN BUG #1 DI SINI ---
    // Mengubah kondisi dari !it.completed menjadi it.completed
    val completedSchedules: LiveData<List<Schedule>> = filteredSchedules.map { schedules ->
        schedules.filter { it.completed }
    }

    fun getScheduleById(id: Long): LiveData<Schedule> {
        return repository.getScheduleById(id)
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun insertOrUpdate(schedule: Schedule) = viewModelScope.launch {
        repository.insertOrUpdate(schedule)
    }

    fun deleteSchedule(id: Long) = viewModelScope.launch {
        repository.deleteSchedule(id)
    }

    fun updateScheduleCompletedStatus(id: Long, completed: Boolean) = viewModelScope.launch {
        repository.updateScheduleCompletedStatus(id, completed)
    }
}
