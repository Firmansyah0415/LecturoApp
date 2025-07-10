package com.lecturo.lecturo.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lecturo.lecturo.Schedule

@Dao
interface ScheduleDao {

    @Query("SELECT * FROM schedules ORDER BY date, time ASC")
    fun getAllSchedules(): LiveData<List<Schedule>>

    @Query("SELECT * FROM schedules WHERE id = :scheduleId")
    fun getScheduleById(scheduleId: Long): LiveData<Schedule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(schedule: Schedule)

    @Query("DELETE FROM schedules WHERE id = :scheduleId")
    suspend fun deleteById(scheduleId: Long)

    @Query("UPDATE schedules SET completed = :isCompleted WHERE id = :scheduleId")
    suspend fun updateCompletedStatus(scheduleId: Long, isCompleted: Boolean)
}