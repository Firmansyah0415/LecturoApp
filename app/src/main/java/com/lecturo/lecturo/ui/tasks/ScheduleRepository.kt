package com.lecturo.lecturo.ui.tasks

import android.content.Context
import com.lecturo.lecturo.DatabaseHelper
import com.lecturo.lecturo.Schedule

class ScheduleRepository(context: Context) {
    private val dbHelper = DatabaseHelper(context)

    fun getAllSchedules(): List<Schedule> {
        return dbHelper.getAllSchedules()
    }

    fun deleteSchedule(id: Long) {
        dbHelper.deleteSchedule(id)
    }
}
