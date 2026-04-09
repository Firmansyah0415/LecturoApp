package com.lecturo.lecturo.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.lecturo.lecturo.data.model.ConsultationPattern
import com.lecturo.lecturo.data.model.ConsultationSchedule

@Dao
interface ConsultationDao {

    // ================== BAGIAN 1: SCHEDULES ==================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: ConsultationSchedule): Long

    @Query("UPDATE consultation_schedules SET is_deleted = 1, is_synced = 0 WHERE id = :id")
    suspend fun softDeleteSchedule(id: Long)

    @Query("DELETE FROM consultation_schedules WHERE id = :id")
    suspend fun hardDeleteSchedule(id: Long)

    @Query("UPDATE consultation_schedules SET is_synced = 1, firestoreId = :firestoreId WHERE id = :localId")
    suspend fun updateScheduleSyncStatus(localId: Long, firestoreId: String)

    @Query("SELECT * FROM consultation_schedules WHERE is_synced = 0")
    suspend fun getUnsyncedSchedules(): List<ConsultationSchedule>

    // --- [PERBAIKAN BUG] SIHIR SQLITE SUBSTR (dd/MM/yyyy -> yyyyMMdd) ---
    @Query("SELECT * FROM consultation_schedules WHERE substr(date, 7, 4) || substr(date, 4, 2) || substr(date, 1, 2) >= substr(:currentDate, 7, 4) || substr(:currentDate, 4, 2) || substr(:currentDate, 1, 2) AND status != 'CANCELLED' AND is_deleted = 0 ORDER BY substr(date, 7, 4) || substr(date, 4, 2) || substr(date, 1, 2) ASC, start_time ASC")
    fun getUpcomingSchedules(currentDate: String): LiveData<List<ConsultationSchedule>>

    @Query("SELECT * FROM consultation_schedules WHERE date = :date AND is_deleted = 0 ORDER BY start_time ASC")
    fun getSchedulesByDate(date: String): LiveData<List<ConsultationSchedule>>

    @Query("SELECT * FROM consultation_schedules WHERE (substr(date, 7, 4) || substr(date, 4, 2) || substr(date, 1, 2) < substr(:currentDate, 7, 4) || substr(:currentDate, 4, 2) || substr(:currentDate, 1, 2) OR status = 'COMPLETED') AND is_deleted = 0 ORDER BY substr(date, 7, 4) || substr(date, 4, 2) || substr(date, 1, 2) DESC, start_time DESC")
    fun getHistorySchedules(currentDate: String): LiveData<List<ConsultationSchedule>>

    @Query("SELECT * FROM consultation_schedules WHERE title LIKE '%' || :query || '%' AND is_deleted = 0 ORDER BY substr(date, 7, 4) || substr(date, 4, 2) || substr(date, 1, 2) ASC")
    fun searchSchedules(query: String): LiveData<List<ConsultationSchedule>>

    @Query("SELECT * FROM consultation_schedules WHERE id = :id")
    suspend fun getScheduleById(id: Long): ConsultationSchedule?

    @Update
    suspend fun updateSchedule(schedule: ConsultationSchedule)

    // ================== BAGIAN 2: PATTERNS ==================

    @Query("SELECT * FROM consultation_patterns WHERE is_synced = 0")
    suspend fun getUnsyncedPatterns(): List<ConsultationPattern>

    @Query("UPDATE consultation_patterns SET is_deleted = 1, is_synced = 0 WHERE id = :id")
    suspend fun softDeletePattern(id: Long)

    @Query("DELETE FROM consultation_patterns WHERE id = :id")
    suspend fun hardDeletePattern(id: Long)

    @Query("UPDATE consultation_patterns SET is_synced = 1, firestoreId = :firestoreId WHERE id = :localId")
    suspend fun updatePatternSyncStatus(localId: Long, firestoreId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPattern(pattern: ConsultationPattern): Long

    @Update
    suspend fun updatePattern(pattern: ConsultationPattern)

    @Query("SELECT * FROM consultation_patterns WHERE is_active = 1 AND is_deleted = 0 ORDER BY day_of_week ASC")
    fun getActivePatterns(): LiveData<List<ConsultationPattern>>

    @Query("SELECT * FROM consultation_patterns WHERE is_deleted = 0 ORDER BY day_of_week ASC")
    fun getAllPatterns(): LiveData<List<ConsultationPattern>>

    @Query("DELETE FROM consultation_schedules")
    suspend fun deleteAllSchedules()

    @Query("DELETE FROM consultation_patterns")
    suspend fun deleteAllPatterns()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(schedules: List<ConsultationSchedule>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatterns(patterns: List<ConsultationPattern>)

    @Query("SELECT * FROM consultation_schedules WHERE status = 'SCHEDULED' AND is_deleted = 0")
    suspend fun getActiveSchedulesForReboot(): List<ConsultationSchedule>

    @Query("SELECT * FROM consultation_schedules WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getScheduleByFirestoreId(firestoreId: String): ConsultationSchedule?

    @Update
    suspend fun updateScheduleRaw(schedule: ConsultationSchedule)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduleRaw(schedule: ConsultationSchedule): Long
}