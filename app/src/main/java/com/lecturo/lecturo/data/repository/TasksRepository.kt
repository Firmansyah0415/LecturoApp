package com.lecturo.lecturo.data.repository

import androidx.lifecycle.LiveData
import com.lecturo.lecturo.data.db.dao.TasksDao
import com.lecturo.lecturo.data.model.Tasks

class TasksRepository(private val tasksDao: TasksDao) {

    fun getAllTasks(): LiveData<List<Tasks>> {
        return tasksDao.getAllTasks()
    }

    fun getTasksById(id: Long): LiveData<Tasks> {
        return tasksDao.getTasksById(id)
    }

    // PERBAIKAN: Fungsi ini sekarang mengembalikan Long
    suspend fun insertOrUpdate(tasks: Tasks): Long {
        return tasksDao.insertOrUpdate(tasks)
    }

    suspend fun deleteTasks(id: Long) {
        tasksDao.deleteById(id)
    }

    suspend fun updateTasksCompletedStatus(id: Long, completed: Boolean) {
        tasksDao.updateCompletedStatus(id, completed)
    }

    // Fungsi baru untuk dipanggil oleh ViewModel
    suspend fun getLatestTask(): Tasks? {
        return tasksDao.getLatestTask()
    }
}