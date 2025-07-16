package com.lecturo.lecturo.data.repository

import androidx.lifecycle.LiveData
import com.lecturo.lecturo.data.db.dao.CalendarEntryDao
import com.lecturo.lecturo.data.model.CalendarEntry

class CalendarRepository(private val calendarEntryDao: CalendarEntryDao) {

    fun getAllEntries(): LiveData<List<CalendarEntry>> {
        return calendarEntryDao.getAllEntries()
    }

    fun getEntriesForDate(date: String): LiveData<List<CalendarEntry>> {
        return calendarEntryDao.getEntriesForDate(date)
    }

    fun getEntriesByCategory(category: String): LiveData<List<CalendarEntry>> {
        return calendarEntryDao.getEntriesByCategory(category)
    }

    // --- PERBAIKAN DI SINI ---
    // Fungsi ini sekarang akan mengembalikan ID dari entri yang baru disimpan.
    suspend fun insertEntry(entry: CalendarEntry): Long {
        return calendarEntryDao.insertEntry(entry)
    }

    suspend fun insertEntries(entries: List<CalendarEntry>) {
        calendarEntryDao.insertEntries(entries)
    }

    suspend fun deleteEntry(entryId: Long) {
        calendarEntryDao.deleteEntry(entryId)
    }

    suspend fun deleteEntriesForSource(type: String, id: Long) {
        calendarEntryDao.deleteEntriesForSource(type, id)
    }

    // Fungsi ini dibutuhkan oleh ViewModel untuk membatalkan notifikasi
    suspend fun getEntriesForSource(type: String, id: Long): List<CalendarEntry> {
        return calendarEntryDao.getEntriesForSource(type, id)
    }
}
