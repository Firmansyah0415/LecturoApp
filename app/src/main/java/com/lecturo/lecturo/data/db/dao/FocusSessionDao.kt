package com.lecturo.lecturo.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lecturo.lecturo.data.model.FocusSession

@Dao
interface FocusSessionDao {

    // 1. INSERT
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: FocusSession): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<FocusSession>)

    // 2. SOFT DELETE (UI)
    @Query("UPDATE focus_sessions SET is_deleted = 1, is_synced = 0 WHERE id = :id")
    suspend fun softDeleteSession(id: Long)

    // 3. HARD DELETE (Worker)
    @Query("DELETE FROM focus_sessions WHERE id = :id")
    suspend fun hardDeleteSession(id: Long)

    // 4. UPDATE SYNC STATUS (Worker)
    @Query("UPDATE focus_sessions SET is_synced = 1, firestoreId = :firestoreId WHERE id = :localId")
    suspend fun updateSyncStatus(localId: Long, firestoreId: String)

    // 5. GET UNSYNCED (Worker)
    @Query("SELECT * FROM focus_sessions WHERE is_synced = 0")
    suspend fun getUnsyncedSessions(): List<FocusSession>

    // 6. UPDATE BIASA
    @Update
    suspend fun updateSession(session: FocusSession)

    // 7. READERS (WAJIB FILTER is_deleted = 0)

    @Query("SELECT * FROM focus_sessions WHERE task_id = :taskId AND is_deleted = 0 ORDER BY start_time DESC")
    fun getHistoryByTask(taskId: Long): LiveData<List<FocusSession>>

    @Query("SELECT SUM(duration_minutes) FROM focus_sessions WHERE task_id = :taskId AND status = 'COMPLETED' AND is_deleted = 0")
    fun getTotalFocusTimeByTask(taskId: Long): LiveData<Int?>

    @Query("SELECT * FROM focus_sessions WHERE is_deleted = 0 ORDER BY start_time DESC")
    suspend fun getAllSessions(): List<FocusSession>

    @Query("DELETE FROM focus_sessions")
    suspend fun deleteAllSessions()

    // Delete lama (opsional)
    @Query("DELETE FROM focus_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Long)

    // Tambahkan di dalam FocusSessionDao.kt
    @Query("UPDATE focus_sessions SET is_deleted = 1, is_synced = 0 WHERE task_id = :taskId")
    suspend fun softDeleteSessionsByTaskId(taskId: Long)
}