package com.lecturo.lecturo.data.model

import java.util.*

data class CalendarDay(
    val date: Calendar,
    val dayNumber: String,
    val isToday: Boolean,
    val isSelected: Boolean = false,
    val scheduleCategories: Set<String> = emptySet(),
    val isCurrentMonth: Boolean = true
) {
    fun hasSchedules(): Boolean = scheduleCategories.isNotEmpty()

    fun hasCategory(category: String): Boolean = scheduleCategories.contains(category)

    fun getFormattedDate(): String {
        val day = date.get(Calendar.DAY_OF_MONTH)
        val month = date.get(Calendar.MONTH) + 1
        val year = date.get(Calendar.YEAR)
        return String.format("%02d/%02d/%04d", day, month, year)
    }
}