package com.lecturo.lecturo.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lecturo.lecturo.data.model.TaskWithFocusStats
import com.lecturo.lecturo.data.model.Tasks

@Dao
interface TasksDao {

    // 1. READ: Hanya ambil yang BELUM DIHAPUS
    @Query("SELECT * FROM tasks WHERE is_deleted = 0 ORDER BY date, time ASC")
    fun getAllTasks(): LiveData<List<Tasks>>

    // 2. SYNC: Ambil semua data kotor (termasuk yang soft deleted)
    @Query("SELECT * FROM tasks WHERE is_synced = 0")
    suspend fun getUnsyncedTasks(): List<Tasks>

    // 3. INSERT/UPDATE
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(tasks: Tasks): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<Tasks>)

    // 4. SOFT DELETE (UI)
    @Query("UPDATE tasks SET is_deleted = 1, is_synced = 0 WHERE id = :tasksId")
    suspend fun softDelete(tasksId: Long)

    // 5. HARD DELETE (Worker)
    @Query("DELETE FROM tasks WHERE id = :tasksId")
    suspend fun hardDelete(tasksId: Long)

    // 6. SYNC SUCCESS
    @Query("UPDATE tasks SET is_synced = 1, firestoreId = :firestoreId WHERE id = :localId")
    suspend fun updateSyncStatus(localId: Long, firestoreId: String)

    // 7. COMPLETED STATUS (Tetap tandai kotor agar sync statusnya)
    @Query("UPDATE tasks SET isCompleted = :isCompleted, is_synced = 0 WHERE id = :tasksId")
    suspend fun updateCompletedStatus(tasksId: Long, isCompleted: Boolean)

    // Helper Lama
    @Query("SELECT * FROM tasks WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getTaskByFirestoreId(firestoreId: String): Tasks?

    @Query("SELECT * FROM tasks WHERE id = :tasksId")
    fun getTasksById(tasksId: Long): LiveData<Tasks>

    @Query("SELECT * FROM tasks WHERE id = :tasksId")
    suspend fun getTaskById(tasksId: Long): Tasks?

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    @Query("SELECT * FROM tasks ORDER BY id DESC LIMIT 1")
    suspend fun getLatestTask(): Tasks?

    // Fungsi deleteById lama tidak dipakai lagi, diganti softDelete/hardDelete
    @Query("DELETE FROM tasks WHERE id = :tasksId")
    suspend fun deleteById(tasksId: Long)

    // --- [BARU] QUERY UNTUK LIST UI (DENGAN STATISTIK POMODORO) ---
    @Query("""
        SELECT t.*, 
               COALESCE(COUNT(f.id), 0) AS completedSessionsCount, 
               COALESCE(SUM(f.duration_minutes), 0) AS totalFocusMinutes
        FROM tasks t
        LEFT JOIN focus_sessions f ON t.id = f.task_id AND f.status = 'COMPLETED' AND f.is_deleted = 0
        WHERE t.is_deleted = 0 
        GROUP BY t.id 
        ORDER BY t.date ASC, t.time ASC
    """)
    fun getAllTasksWithStats(): LiveData<List<TaskWithFocusStats>>

    @Update
    suspend fun updateTaskRaw(task: Tasks)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskRaw(task: Tasks): Long
}