package com.lecturo.lecturo.viewmodel.task

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.lecturo.lecturo.data.model.CalendarEntry
import com.lecturo.lecturo.data.model.TaskWithFocusStats // <--- Import baru
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

    // [UBAH] Sekarang kita menarik data lengkap dengan statistiknya
    private val allTasksWithStats: LiveData<List<TaskWithFocusStats>> = tasksRepository.getAllTasksWithStats()
    private val searchQuery = MutableLiveData<String>("")

    // [UBAH] MediatorLiveData sekarang menangani TaskWithFocusStats
    val filteredTasks = MediatorLiveData<List<TaskWithFocusStats>>().apply {
        addSource(allTasksWithStats) { tasksWithStats -> value = applyFilters(tasksWithStats, searchQuery.value) }
        addSource(searchQuery) { query -> value = applyFilters(allTasksWithStats.value, query) }
    }

    // [UBAH] Pecah berdasarkan status isCompleted milik entitas task
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

    // Fungsi get, set, delete, dan update biarkan sama saja
    // karena mereka hanya butuh ID dan model 'Tasks' murni.

    fun getTasksById(id: Long): LiveData<Tasks> = tasksRepository.getTasksById(id)

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    // ... sisa fungsi insertOrUpdate, deleteTasks, dan updateTasksCompletedStatus tetap sama ...
    fun insertOrUpdate(tasks: Tasks) = viewModelScope.launch {
        if (tasks.id != 0L) {
            val scheduler = NotificationScheduler(getApplication())
            val oldEntries = calendarRepository.getEntriesForSource("TASK", tasks.id)
            oldEntries.forEach { oldEntry ->
                scheduler.cancelNotification(oldEntry.notificationId)
            }
            calendarRepository.deleteEntriesForSource("TASK", tasks.id)
        }

        val taskId = tasksRepository.insertOrUpdate(tasks)
        val finalTask = tasks.copy(id = taskId)

        if (!finalTask.isCompleted) {
            val calendarEntry = CalendarEntry(
                title = finalTask.title,
                date = finalTask.date,
                time = finalTask.time,
                category = "Tugas",
                sourceFeatureType = "TASK",
                sourceFeatureId = finalTask.id,
                notificationMinutesBefore = finalTask.notificationMinutesBefore
            )
            val calendarEntryId = calendarRepository.insertEntry(calendarEntry)
            val finalCalendarEntry = calendarEntry.copy(id = calendarEntryId)

            if (finalTask.notificationMinutesBefore >= 0) {
                val scheduler = NotificationScheduler(getApplication())
                scheduler.scheduleNotification(finalCalendarEntry)
            }
        }
    }

    fun deleteTasks(id: Long) = viewModelScope.launch {
        val scheduler = NotificationScheduler(getApplication())
        val entriesToDelete = calendarRepository.getEntriesForSource("TASK", id)
        entriesToDelete.forEach { entry ->
            scheduler.cancelNotification(entry.notificationId)
        }
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
            val scheduler = NotificationScheduler(getApplication())
            val entriesToCancel = calendarRepository.getEntriesForSource("TASK", id)
            entriesToCancel.forEach { entry ->
                scheduler.cancelNotification(entry.notificationId)
            }
        }
    }
}