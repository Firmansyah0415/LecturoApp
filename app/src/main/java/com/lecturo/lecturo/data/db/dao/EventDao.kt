package com.lecturo.lecturo.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.lecturo.lecturo.data.model.Event

@Dao
interface EventDao {

    @Query("SELECT * FROM events ORDER BY date ASC, time ASC")
    fun getAllEvents(): LiveData<List<Event>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(event: Event): Long

    @Query("DELETE FROM events WHERE id = :eventId")
    suspend fun deleteById(eventId: Long)

    @Query("UPDATE events SET isCompleted = :isCompleted WHERE id = :eventId")
    suspend fun updateCompletedStatus(eventId: Long, isCompleted: Boolean)

    @Query("SELECT * FROM events WHERE id = :eventId")
    suspend fun getEventById(eventId: Long): Event?

    @Query("SELECT * FROM events WHERE category = :category ORDER BY date ASC, time ASC")
    fun getEventsByCategory(category: String): LiveData<List<Event>>

    @Query("SELECT DISTINCT category FROM events ORDER BY category ASC")
    fun getAllCategories(): LiveData<List<String>>
}
