package com.lecturo.lecturo.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.lecturo.lecturo.data.model.CalendarEntry

@Dao
interface CalendarEntryDao {

    // --- PERBAIKAN DI SINI ---
    // Fungsi ini sekarang akan mengembalikan ID dari entri yang baru disimpan.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: CalendarEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<CalendarEntry>)

    @Query("SELECT * FROM calendar_entries WHERE date = :specificDate ORDER BY time ASC")
    fun getEntriesForDate(specificDate: String): LiveData<List<CalendarEntry>>

    @Query("SELECT * FROM calendar_entries ORDER BY date ASC, time ASC")
    fun getAllEntries(): LiveData<List<CalendarEntry>>

    @Query("SELECT * FROM calendar_entries WHERE category = :category ORDER BY date ASC, time ASC")
    fun getEntriesByCategory(category: String): LiveData<List<CalendarEntry>>

    @Query("DELETE FROM calendar_entries WHERE sourceFeatureType = :type AND sourceFeatureId = :id")
    suspend fun deleteEntriesForSource(type: String, id: Long)

    @Query("DELETE FROM calendar_entries WHERE id = :entryId")
    suspend fun deleteEntry(entryId: Long)

    @Query("SELECT * FROM calendar_entries WHERE sourceFeatureType = :type AND sourceFeatureId = :id")
    suspend fun getEntriesForSource(type: String, id: Long): List<CalendarEntry>

    @Query("SELECT * FROM calendar_entries WHERE id = :entryId")
    suspend fun getEntryById(entryId: Long): CalendarEntry?

    @Update
    suspend fun updateEntry(entry: CalendarEntry)

    @Query("SELECT * FROM calendar_entries WHERE notificationMinutesBefore >= 0")
    suspend fun getActiveNotificationEntries(): List<CalendarEntry>
}
