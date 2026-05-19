package com.lecturo.lecturo.viewmodel.task

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.lecturo.lecturo.data.model.TaskWithFocusStats
import com.lecturo.lecturo.data.model.Tasks
import com.lecturo.lecturo.data.repository.CalendarRepository
import com.lecturo.lecturo.data.repository.TasksRepository
import com.lecturo.lecturo.notifications.NotificationScheduler
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TasksViewModel(
    private val tasksRepository: TasksRepository,
    private val calendarRepository: CalendarRepository,
    application: Application
) : AndroidViewModel(application) {

    // Menarik data lengkap dengan statistiknya
    private val allTasksWithStats: LiveData<List<TaskWithFocusStats>> = tasksRepository.getAllTasksWithStats()
    private val searchQuery = MutableLiveData<String>("")

    // 🔴 TAMBAHAN BARU: State untuk urutan (Default: true / Terbaru)
    val isSortNewest = MutableLiveData<Boolean>(true)

    // MediatorLiveData menangani TaskWithFocusStats
    // MediatorLiveData mendengarkan 3 hal: Data asli, Pencarian, dan Urutan
    val filteredTasks = MediatorLiveData<List<TaskWithFocusStats>>().apply {
        addSource(allTasksWithStats) { value = applyFiltersAndSort(it, searchQuery.value, isSortNewest.value) }
        addSource(searchQuery) { value = applyFiltersAndSort(allTasksWithStats.value, it, isSortNewest.value) }
        addSource(isSortNewest) { value = applyFiltersAndSort(allTasksWithStats.value, searchQuery.value, it) }
    }

    // Pecah berdasarkan status isCompleted milik entitas task
    val pendingTasks: LiveData<List<TaskWithFocusStats>> = filteredTasks.map { list -> list.filter { !it.task.isCompleted } }
    val completedTasks: LiveData<List<TaskWithFocusStats>> = filteredTasks.map { list -> list.filter { it.task.isCompleted } }

    // 🔴 PERBAIKAN LOGIKA: Filter + Sorting
    private fun applyFiltersAndSort(tasksWithStats: List<TaskWithFocusStats>?, query: String?, sortNewest: Boolean?): List<TaskWithFocusStats> {
        if (tasksWithStats == null) return emptyList()

        // 1. Filter Pencarian
        var processedList = if (query.isNullOrBlank()) {
            tasksWithStats
        } else {
            val lowerCaseQuery = query.lowercase(Locale.getDefault())
            tasksWithStats.filter { item ->
                item.task.title.lowercase(Locale.getDefault()).contains(lowerCaseQuery) ||
                        item.task.description?.lowercase(Locale.getDefault())?.contains(lowerCaseQuery) == true
            }
        }

        // 2. Sorting (Berdasarkan Tanggal)
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        processedList = processedList.sortedWith { t1, t2 ->
            val d1 = try { dateFormat.parse(t1.task.date) } catch (e: Exception) { Date(0) }
            val d2 = try { dateFormat.parse(t2.task.date) } catch (e: Exception) { Date(0) }

            if (sortNewest == true) {
                d2.compareTo(d1) // Terbaru di atas
            } else {
                d1.compareTo(d2) // Terlama di atas
            }
        }

        return processedList
    }

    fun toggleSort() {
        isSortNewest.value = !(isSortNewest.value ?: true)
    }

    fun getTasksById(id: Long): LiveData<Tasks> = tasksRepository.getTasksById(id)

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun insertOrUpdate(tasks: Tasks) = viewModelScope.launch {
        tasksRepository.insertOrUpdate(tasks)
    }

    fun deleteTasks(id: Long) = viewModelScope.launch {
        val scheduler = NotificationScheduler(getApplication())
        val entriesToDelete = calendarRepository.getEntriesForSource("TASK", id)
        entriesToDelete.forEach { entry ->
            scheduler.cancelNotification(entry.notificationId)
        }

        // Hapus dari repo tugas dan hapus dari repo kalender
        tasksRepository.deleteTasks(id)
        calendarRepository.deleteEntriesForSource("TASK", id)
    }

    fun updateTasksCompletedStatus(id: Long, completed: Boolean) = viewModelScope.launch {
        val currentTask = tasksRepository.getTaskByIdSuspend(id)
        currentTask?.let { task ->
            val updatedTask = task.copy(isCompleted = completed)
            insertOrUpdate(updatedTask)
        }

        if (completed) {
            // [SOLUSI ERROR KOTLIN] Deklarasikan tipe Application secara eksplisit
            val app = getApplication<Application>()

            // 1. Matikan Notifikasi Kalender
            val scheduler = NotificationScheduler(app)
            val entriesToCancel = calendarRepository.getEntriesForSource("TASK", id)
            entriesToCancel.forEach { entry ->
                scheduler.cancelNotification(entry.notificationId)
            }

            // =======================================================
            // [PERBAIKAN BUG POMODORO] - Pembunuhan Service Jarak Jauh
            // =======================================================
            val prefs = com.lecturo.lecturo.utils.FocusPreferences(app)

            // Cek apakah Tugas yang dicentang ini adalah tugas yang sedang berjalan di Pomodoro
            if (prefs.getActiveTaskId() == id) {
                // Tembak mati TimerService menggunakan variabel 'app'
                val stopIntent = android.content.Intent(app, com.lecturo.lecturo.service.TimerService::class.java)
                stopIntent.action = com.lecturo.lecturo.service.TimerService.ACTION_STOP
                app.startService(stopIntent)

                // Bersihkan jejaknya agar tidak stuck
                prefs.clearActiveTaskId()
            }
            // =======================================================
        }
    }
}