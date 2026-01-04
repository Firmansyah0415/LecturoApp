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

    // Ini yang lama (Untuk UI/ViewModel) -> Mengembalikan LiveData
    @Query("SELECT * FROM tasks WHERE id = :tasksId")
    fun getTasksById(tasksId: Long): LiveData<Tasks>

    // --- [TAMBAHAN BARU] ---
    // Fungsi ini KHUSUS untuk Repository agar bisa "mengintip" data sebelum dihapus
    // Mengembalikan Tasks? (Bisa null jika tidak ketemu), bukan LiveData
    @Query("SELECT * FROM tasks WHERE id = :tasksId")
    suspend fun getTaskById(tasksId: Long): Tasks?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(tasks: Tasks): Long

    @Query("DELETE FROM tasks WHERE id = :tasksId")
    suspend fun deleteById(tasksId: Long)

    // TAMBAHKAN INI:
    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    @Query("UPDATE tasks SET isCompleted = :isCompleted WHERE id = :tasksId")
    suspend fun updateCompletedStatus(tasksId: Long, isCompleted: Boolean)

    @Query("SELECT * FROM tasks ORDER BY id DESC LIMIT 1")
    suspend fun getLatestTask(): Tasks?
}