package com.lecturo.lecturo.ui.task

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.lecturo.lecturo.data.model.CalendarEntry
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

    private val allTasks: LiveData<List<Tasks>> = tasksRepository.getAllTasks()
    private val searchQuery = MutableLiveData<String>("")

    val filteredTasks = MediatorLiveData<List<Tasks>>().apply {
        addSource(allTasks) { tasks -> value = applyFilters(tasks, searchQuery.value) }
        addSource(searchQuery) { query -> value = applyFilters(allTasks.value, query) }
    }

    val pendingTasks: LiveData<List<Tasks>> = filteredTasks.map { tasks -> tasks.filter { !it.completed } }
    val completedTasks: LiveData<List<Tasks>> = filteredTasks.map { tasks -> tasks.filter { it.completed } }

    private fun applyFilters(tasks: List<Tasks>?, query: String?): List<Tasks> {
        if (tasks == null) return emptyList()
        if (query.isNullOrBlank()) return tasks
        val lowerCaseQuery = query.lowercase(Locale.getDefault())
        return tasks.filter {
            it.title.lowercase(Locale.getDefault()).contains(lowerCaseQuery) ||
                    it.description?.lowercase(Locale.getDefault())?.contains(lowerCaseQuery) == true
        }
    }

    fun getTasksById(id: Long): LiveData<Tasks> = tasksRepository.getTasksById(id)

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    // --- FUNGSI YANG DIPERBARUI ---
    // Sekarang hanya menerima satu parameter: objek Tasks yang sudah lengkap
    fun insertOrUpdate(tasks: Tasks) = viewModelScope.launch {
        // 1. Batalkan alarm lama jika ada (untuk mode edit)
        if (tasks.id != 0L) {
            val scheduler = NotificationScheduler(getApplication())
            val oldEntries = calendarRepository.getEntriesForSource("TASK", tasks.id)
            oldEntries.forEach { oldEntry ->
                scheduler.cancelNotification(oldEntry.notificationId)
            }
            calendarRepository.deleteEntriesForSource("TASK", tasks.id)
        }

        // 2. Simpan tugas dan dapatkan ID-nya
        val taskId = tasksRepository.insertOrUpdate(tasks)
        val finalTask = tasks.copy(id = taskId)

        // 3. Buat entri kalender
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

        // 4. Jadwalkan notifikasi baru
        if (finalTask.notificationMinutesBefore >= 0) {
            val scheduler = NotificationScheduler(getApplication())
            scheduler.scheduleNotification(finalCalendarEntry)
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
        tasksRepository.updateTasksCompletedStatus(id, completed)
        if (completed) {
            val scheduler = NotificationScheduler(getApplication())
            val entriesToCancel = calendarRepository.getEntriesForSource("TASK", id)
            entriesToCancel.forEach { entry ->
                scheduler.cancelNotification(entry.notificationId)
            }
        }
    }
}
