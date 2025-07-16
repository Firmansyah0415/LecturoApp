package com.lecturo.lecturo.data.repository

import androidx.lifecycle.LiveData
import com.lecturo.lecturo.data.db.dao.EventDao
import com.lecturo.lecturo.data.db.dao.TeachingRuleDao
import com.lecturo.lecturo.data.db.dao.CalendarEntryDao
import com.lecturo.lecturo.data.model.Event
import com.lecturo.lecturo.data.model.TeachingRule
import com.lecturo.lecturo.data.model.CalendarEntry

class TeachingRepository(
    private val teachingRuleDao: TeachingRuleDao,
    private val eventDao: EventDao,
    private val calendarEntryDao: CalendarEntryDao // Tambahkan ini
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

    // Fungsi untuk menyimpan event ke tabel events
    suspend fun insertEvent(event: Event): Long {
        return eventDao.insertOrUpdate(event)
    }

    // Fungsi untuk menyimpan calendar entries
    suspend fun insertCalendarEntry(entry: CalendarEntry) {
        calendarEntryDao.insertEntry(entry)
    }

    suspend fun insertCalendarEntries(entries: List<CalendarEntry>) {
        calendarEntryDao.insertEntries(entries)
    }

    suspend fun deleteCalendarEntriesForSource(type: String, id: Long) {
        calendarEntryDao.deleteEntriesForSource(type, id)
    }
}
