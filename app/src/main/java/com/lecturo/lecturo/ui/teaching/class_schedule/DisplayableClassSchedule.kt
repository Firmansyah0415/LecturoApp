package com.lecturo.lecturo.ui.teaching.class_schedule

import com.lecturo.lecturo.data.model.CalendarEntry


data class DisplayableClassSchedule(
    val entry: CalendarEntry,
    val meetingNumber: Int
)
