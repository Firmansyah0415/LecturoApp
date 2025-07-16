package com.lecturo.lecturo.ui.teaching.class_schedule

import com.lecturo.lecturo.data.model.CalendarEntry

/**
 * Data class ini berfungsi sebagai "pembungkus" untuk UI.
 * Ia berisi data asli dari CalendarEntry dan nomor pertemuan yang sudah dihitung.
 */
data class DisplayableClassSchedule(
    val entry: CalendarEntry,
    val meetingNumber: Int
)
