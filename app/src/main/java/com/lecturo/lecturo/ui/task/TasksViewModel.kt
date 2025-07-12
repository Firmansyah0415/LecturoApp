package com.lecturo.lecturo.ui.task

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.lecturo.lecturo.data.model.Tasks
import com.lecturo.lecturo.data.repository.TasksRepository
import kotlinx.coroutines.launch
import java.util.Locale

class TasksViewModel(private val repository: TasksRepository) : ViewModel() {

    // Sumber data utama dari database
    private val allTasks: LiveData<List<Tasks>> = repository.getAllTasks()

    // LiveData untuk menampung query pencarian dari pengguna
    private val searchQuery = MutableLiveData<String>("")

    // MediatorLiveData menggabungkan `allTasks` dan `searchQuery`.
    private val filteredTasks = MediatorLiveData<List<Tasks>>().apply {

        fun filterTasks() {
            val query = searchQuery.value.orEmpty()
            val tasks = allTasks.value ?: emptyList()

            value = if (query.isBlank()) {
                tasks
            } else {
                val lowerCaseQuery = query.lowercase(Locale.getDefault())
                tasks.filter {
                    it.title.lowercase(Locale.getDefault()).contains(lowerCaseQuery) ||
                            it.description?.lowercase(Locale.getDefault())?.contains(lowerCaseQuery) == true
                }
            }
        }

        addSource(allTasks) { filterTasks() }
        addSource(searchQuery) { filterTasks() }
    }

    // Jadwal 'Pending' dan 'Completed' sekarang berasal dari daftar yang sudah difilter
    val pendingTasks: LiveData<List<Tasks>> = filteredTasks.map { tasks ->
        tasks.filter { !it.completed }
    }

    // --- PERBAIKAN BUG #1 DI SINI ---
    // Mengubah kondisi dari !it.completed menjadi it.completed
    val completedTasks: LiveData<List<Tasks>> = filteredTasks.map { tasks ->
        tasks.filter { it.completed }
    }

    fun getTasksById(id: Long): LiveData<Tasks> {
        return repository.getTasksById(id)
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun insertOrUpdate(tasks: Tasks) = viewModelScope.launch {
        repository.insertOrUpdate(tasks)
    }

    fun deleteTasks(id: Long) = viewModelScope.launch {
        repository.deleteTasks(id)
    }

    fun updateTasksCompletedStatus(id: Long, completed: Boolean) = viewModelScope.launch {
        repository.updateTasksCompletedStatus(id, completed)
    }
}
