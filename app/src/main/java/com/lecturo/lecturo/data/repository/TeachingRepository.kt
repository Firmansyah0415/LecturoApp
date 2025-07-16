package com.lecturo.lecturo.data.repository

import androidx.lifecycle.LiveData
import com.lecturo.lecturo.data.db.dao.TeachingRuleDao
import com.lecturo.lecturo.data.db.dao.CalendarEntryDao
import com.lecturo.lecturo.data.model.TeachingRule
import com.lecturo.lecturo.data.model.CalendarEntry

class TeachingRepository(
    private val teachingRuleDao: TeachingRuleDao,
    private val calendarEntryDao: CalendarEntryDao
) {

    fun getAllRules(): LiveData<List<TeachingRule>> {
        return teachingRuleDao.getAllRules()
    }

    suspend fun insertOrUpdateRule(rule: TeachingRule): Long {
        return teachingRuleDao.insertOrUpdateRule(rule)
    }

    suspend fun deleteRuleById(ruleId: Long) {
        teachingRuleDao.deleteRuleById(ruleId)
    }

    suspend fun getRuleById(ruleId: Long): TeachingRule? {
        return teachingRuleDao.getRuleById(ruleId)
    }

    fun getRulesByDay(dayOfWeek: String): LiveData<List<TeachingRule>> {
        return teachingRuleDao.getRulesByDay(dayOfWeek)
    }

    // --- FUNGSI BARU YANG DIPERLUKAN ---
    // Menambahkan fungsi ini untuk mengatasi error "Unresolved reference" di ViewModel
    suspend fun insertCalendarEntry(entry: CalendarEntry): Long {
        return calendarEntryDao.insertEntry(entry)
    }

    suspend fun insertCalendarEntries(entries: List<CalendarEntry>) {
        calendarEntryDao.insertEntries(entries)
    }

    suspend fun deleteCalendarEntriesForSource(type: String, id: Long) {
        calendarEntryDao.deleteEntriesForSource(type, id)
    }

    suspend fun getCalendarEntriesForSource(type: String, id: Long): List<CalendarEntry> {
        return calendarEntryDao.getEntriesForSource(type, id)
    }
}
