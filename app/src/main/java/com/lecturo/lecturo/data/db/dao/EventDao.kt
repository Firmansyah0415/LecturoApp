package com.lecturo.lecturo.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.lecturo.lecturo.data.model.Event

@Dao
interface EventDao {

    // 1. READ: Hanya ambil yang BELUM DIHAPUS
    @Query("SELECT * FROM events WHERE is_deleted = 0 ORDER BY date ASC, time ASC")
    fun getAllEvents(): LiveData<List<Event>>

    // 2. READ SYNC: Ambil semua data kotor (untuk Worker)
    @Query("SELECT * FROM events WHERE is_synced = 0")
    suspend fun getUnsyncedEvents(): List<Event>

    // 3. INSERT/UPDATE
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(event: Event): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<Event>)

    // 4. SOFT DELETE (UI)
    @Query("UPDATE events SET is_deleted = 1, is_synced = 0 WHERE id = :eventId")
    suspend fun softDelete(eventId: Long)

    // 5. HARD DELETE (Worker)
    @Query("DELETE FROM events WHERE id = :eventId")
    suspend fun hardDelete(eventId: Long)

    // 6. UPDATE SYNC STATUS
    @Query("UPDATE events SET is_synced = 1, firestoreId = :firestoreId WHERE id = :localId")
    suspend fun updateSyncStatus(localId: Long, firestoreId: String)

    // 7. HELPER LAINNYA
    // Pastikan filter is_deleted = 0 juga diterapkan di sini
    @Query("SELECT * FROM events WHERE category = :category AND is_deleted = 0 ORDER BY date ASC, time ASC")
    fun getEventsByCategory(category: String): LiveData<List<Event>>

    @Query("SELECT DISTINCT category FROM events WHERE is_deleted = 0 ORDER BY category ASC")
    fun getAllCategories(): LiveData<List<String>>

    @Query("SELECT * FROM events WHERE id = :eventId")
    suspend fun getEventById(eventId: Long): Event?

    @Query("UPDATE events SET isCompleted = :isCompleted, is_synced = 0 WHERE id = :eventId")
    suspend fun updateCompletedStatus(eventId: Long, isCompleted: Boolean)

    @Query("DELETE FROM events")
    suspend fun deleteAllEvents()

    // Delete by ID lama (opsional, sudah diganti soft/hard delete)
    @Query("DELETE FROM events WHERE id = :eventId")
    suspend fun deleteById(eventId: Long)

    @Query("SELECT * FROM events WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getEventByFirestoreId(firestoreId: String): Event?

    @Update
    suspend fun updateEventRaw(event: Event)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEventRaw(event: Event): Long
}