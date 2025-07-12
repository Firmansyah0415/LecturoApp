package com.lecturo.lecturo.data.repository

import androidx.lifecycle.LiveData
import com.lecturo.lecturo.data.db.dao.EventDao
import com.lecturo.lecturo.data.model.Event

class EventRepository(private val eventDao: EventDao) {

    fun getAllEvents(): LiveData<List<Event>> {
        return eventDao.getAllEvents()
    }

    suspend fun insertOrUpdate(event: Event): Long {
        return eventDao.insertOrUpdate(event)
    }

    suspend fun deleteById(eventId: Long) {
        eventDao.deleteById(eventId)
    }

    suspend fun updateCompletedStatus(eventId: Long, isCompleted: Boolean) {
        eventDao.updateCompletedStatus(eventId, isCompleted)
    }

    suspend fun getEventById(eventId: Long): Event? {
        return eventDao.getEventById(eventId)
    }

    fun getEventsByCategory(category: String): LiveData<List<Event>> {
        return eventDao.getEventsByCategory(category)
    }

    fun getAllCategories(): LiveData<List<String>> {
        return eventDao.getAllCategories()
    }
}