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
import java.util.*

class TasksViewModel(
    private val tasksRepository: TasksRepository,
    private val calendarRepository: CalendarRepository,
    application: Application
) : AndroidViewModel(application) {

    // Menarik data lengkap dengan statistiknya
    private val allTasksWithStats: LiveData<List<TaskWithFocusStats>> = tasksRepository.getAllTasksWithStats()
    private val searchQuery = MutableLiveData<String>("")

    // MediatorLiveData menangani TaskWithFocusStats
    val filteredTasks = MediatorLiveData<List<TaskWithFocusStats>>().apply {
        addSource(allTasksWithStats) { tasksWithStats -> value = applyFilters(tasksWithStats, searchQuery.value) }
        addSource(searchQuery) { query -> value = applyFilters(allTasksWithStats.value, query) }
    }

    // Pecah berdasarkan status isCompleted milik entitas task
    val pendingTasks: LiveData<List<TaskWithFocusStats>> = filteredTasks.map { list -> list.filter { !it.task.isCompleted } }
    val completedTasks: LiveData<List<TaskWithFocusStats>> = filteredTasks.map { list -> list.filter { it.task.isCompleted } }

    private fun applyFilters(tasksWithStats: List<TaskWithFocusStats>?, query: String?): List<TaskWithFocusStats> {
        if (tasksWithStats == null) return emptyList()
        if (query.isNullOrBlank()) return tasksWithStats
        val lowerCaseQuery = query.lowercase(Locale.getDefault())
        return tasksWithStats.filter { item ->
            item.task.title.lowercase(Locale.getDefault()).contains(lowerCaseQuery) ||
                    item.task.description?.lowercase(Locale.getDefault())?.contains(lowerCaseQuery) == true
        }
    }

    fun getTasksById(id: Long): LiveData<Tasks> = tasksRepository.getTasksById(id)

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    // ==============================================================================
    // PERBAIKAN 1: Fungsi insertOrUpdate sekarang sangat bersih!
    // Semua logika pembuatan CalendarEntry, Set Notifikasi, dan Prioritas
    // SUDAH ditangani dengan sempurna oleh TasksRepository.
    // Jadi di sini kita cukup memanggil Repository-nya saja.
    // ==============================================================================
    fun insertOrUpdate(tasks: Tasks) = viewModelScope.launch {
        tasksRepository.insertOrUpdate(tasks)
    }

    // ==============================================================================
    // PERBAIKAN 2: Fungsi Delete tetap butuh mematikan notifikasi dan menghapus kalender
    // Karena TasksRepository.deleteTasks() hanya menghapus Tugas & Sesi Pomodoro.
    // ==============================================================================
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

    // ==============================================================================
    // PERBAIKAN 3: Memanfaatkan insertOrUpdate yang baru
    // ==============================================================================
//    fun updateTasksCompletedStatus(id: Long, completed: Boolean) = viewModelScope.launch {
//        val currentTask = tasksRepository.getTaskByIdSuspend(id)
//        currentTask?.let { task ->
//            val updatedTask = task.copy(isCompleted = completed)
//            // Memanggil ini akan otomatis memicu Repository untuk mengurus sisa logikanya
//            insertOrUpdate(updatedTask)
//        }
//
//        // Jaring pengaman: Jika tugas dicentang selesai, matikan alarmnya
//        if (completed) {
//            val scheduler = NotificationScheduler(getApplication())
//            val entriesToCancel = calendarRepository.getEntriesForSource("TASK", id)
//            entriesToCancel.forEach { entry ->
//                scheduler.cancelNotification(entry.notificationId)
//            }
//        }
//    }

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