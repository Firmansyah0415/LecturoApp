package com.lecturo.lecturo.viewmodel.task

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

    val pendingTasks: LiveData<List<Tasks>> = filteredTasks.map { tasks -> tasks.filter { !it.isCompleted } }
    val completedTasks: LiveData<List<Tasks>> = filteredTasks.map { tasks -> tasks.filter { it.isCompleted } }

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

    fun insertOrUpdate(tasks: Tasks) = viewModelScope.launch {
        // 1. Bersihkan notifikasi lama jika ada (untuk update)
        if (tasks.id != 0L) {
            val scheduler = NotificationScheduler(getApplication())
            val oldEntries = calendarRepository.getEntriesForSource("TASK", tasks.id)
            oldEntries.forEach { oldEntry ->
                scheduler.cancelNotification(oldEntry.notificationId)
            }
            calendarRepository.deleteEntriesForSource("TASK", tasks.id)
        }

        // 2. Simpan ke Repository (Ini akan handle Lokal + Cloud Sync)
        val taskId = tasksRepository.insertOrUpdate(tasks)
        val finalTask = tasks.copy(id = taskId)

        // 3. Buat ulang entri kalender & Notifikasi
        // Hanya buat notifikasi jika tugas BELUM selesai
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

    // --- PERBAIKAN PENTING DI SINI ---
    fun updateTasksCompletedStatus(id: Long, completed: Boolean) = viewModelScope.launch {
        // Logika Lama (Hanya Lokal):
        // tasksRepository.updateTasksCompletedStatus(id, completed)

        // Logika Baru (Lokal + Cloud):
        // 1. Ambil data task saat ini dari DB (menggunakan DAO suspend function yang kita bahas sebelumnya agar tidak blocking)
        val currentTask = tasksRepository.getLatestTask() // Atau buat fungsi getTaskById suspend di repo

        // Agar aman, kita pakai cara ini: Ambil dari list yang sudah ada di memori (sedikit hacky tapi cepat)
        // ATAU yang paling benar: Panggil insertOrUpdate dengan status baru.

        // Mari kita cari task dari list 'allTasks' (karena LiveData sudah pegang datanya)
        val taskToUpdate = allTasks.value?.find { it.id == id }

        if (taskToUpdate != null) {
            val updatedTask = taskToUpdate.copy(isCompleted = completed)
            // Panggil insertOrUpdate agar logic sync ke Backend berjalan otomatis
            insertOrUpdate(updatedTask)
        } else {
            // Fallback jika data null (jarang terjadi), update lokal saja
            tasksRepository.updateTasksCompletedStatus(id, completed)
        }

        // Kelola notifikasi (Hapus notifikasi jika tugas selesai)
        if (completed) {
            val scheduler = NotificationScheduler(getApplication())
            val entriesToCancel = calendarRepository.getEntriesForSource("TASK", id)
            entriesToCancel.forEach { entry ->
                scheduler.cancelNotification(entry.notificationId)
            }
        }
        // Jika tugas di-uncheck (belum selesai), insertOrUpdate di atas otomatis akan menjadwalkan ulang notifikasi.
    }
}