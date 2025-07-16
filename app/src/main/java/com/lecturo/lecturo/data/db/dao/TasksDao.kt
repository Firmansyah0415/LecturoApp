package com.lecturo.lecturo.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lecturo.lecturo.data.model.Tasks

@Dao
interface TasksDao {

    @Query("SELECT * FROM tasks ORDER BY date, time ASC")
    fun getAllTasks(): LiveData<List<Tasks>>

    @Query("SELECT * FROM tasks WHERE id = :tasksId")
    fun getTasksById(tasksId: Long): LiveData<Tasks>

    // PERBAIKAN: Fungsi ini sekarang mengembalikan Long (ID dari item yang disimpan)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(tasks: Tasks): Long

    @Query("DELETE FROM tasks WHERE id = :tasksId")
    suspend fun deleteById(tasksId: Long)

    @Query("UPDATE tasks SET completed = :isCompleted WHERE id = :tasksId")
    suspend fun updateCompletedStatus(tasksId: Long, isCompleted: Boolean)

    // Fungsi baru untuk mendapatkan tugas terakhir (berguna untuk mendapatkan ID)
    @Query("SELECT * FROM tasks ORDER BY id DESC LIMIT 1")
    suspend fun getLatestTask(): Tasks?
}