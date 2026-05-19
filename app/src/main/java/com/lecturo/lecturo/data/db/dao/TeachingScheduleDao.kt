package com.lecturo.lecturo.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.lecturo.lecturo.data.model.TeachingSchedule

@Dao
interface TeachingScheduleDao {

    @Query("SELECT * FROM teaching_schedules WHERE is_deleted = 0 ORDER BY substr(date, 7, 4) || substr(date, 4, 2) || substr(date, 1, 2) DESC, startTime DESC")
    fun getAllSchedules(): LiveData<List<TeachingSchedule>>

    @Query("SELECT * FROM teaching_schedules WHERE is_synced = 0")
    suspend fun getUnsyncedSchedules(): List<TeachingSchedule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSchedule(schedule: TeachingSchedule): Long

    @Query("UPDATE teaching_schedules SET is_deleted = 1, is_synced = 0 WHERE localId = :scheduleId")
    suspend fun softDeleteSchedule(scheduleId: Long)

    @Query("DELETE FROM teaching_schedules WHERE localId = :scheduleId")
    suspend fun deleteSchedulePermanently(scheduleId: Long)

    @Query("UPDATE teaching_schedules SET is_synced = 1, firestoreId = :firestoreId WHERE localId = :localId")
    suspend fun updateSyncStatus(localId: Long, firestoreId: String)

    @Query("SELECT * FROM teaching_schedules WHERE localId = :scheduleId")
    suspend fun getScheduleById(scheduleId: Long): TeachingSchedule?

    @Query("SELECT * FROM teaching_schedules WHERE dayOfWeek = :dayOfWeek AND is_deleted = 0 ORDER BY substr(date, 7, 4) || substr(date, 4, 2) || substr(date, 1, 2) ASC, startTime ASC")
    fun getSchedulesByDay(dayOfWeek: String): LiveData<List<TeachingSchedule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(schedules: List<TeachingSchedule>)

    @Query("DELETE FROM teaching_schedules")
    suspend fun deleteAll()

    @Query("SELECT * FROM teaching_schedules WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getScheduleByFirestoreId(firestoreId: String): TeachingSchedule?

    @Update
    suspend fun updateScheduleRaw(schedule: TeachingSchedule)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduleRaw(schedule: TeachingSchedule): Long

    @Query("SELECT * FROM teaching_schedules WHERE is_synced = 1 AND firestoreId IS NOT NULL")
    suspend fun getSyncedSchedulesList(): List<TeachingSchedule>
}