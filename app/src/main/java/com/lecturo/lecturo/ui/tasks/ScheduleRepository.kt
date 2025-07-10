package com.lecturo.lecturo.ui.tasks

import androidx.lifecycle.LiveData
import com.lecturo.lecturo.Schedule
import com.lecturo.lecturo.db.ScheduleDao

class ScheduleRepository(private val scheduleDao: ScheduleDao) {

    fun getAllSchedules(): LiveData<List<Schedule>> {
        return scheduleDao.getAllSchedules()
    }

    fun getScheduleById(id: Long): LiveData<Schedule> {
        return scheduleDao.getScheduleById(id)
    }

    suspend fun insertOrUpdate(schedule: Schedule) {
        scheduleDao.insertOrUpdate(schedule)
    }

    suspend fun deleteSchedule(id: Long) {
        scheduleDao.deleteById(id)
    }

    suspend fun updateScheduleCompletedStatus(id: Long, completed: Boolean) {
        scheduleDao.updateCompletedStatus(id, completed)
    }
}
