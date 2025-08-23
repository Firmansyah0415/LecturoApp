package com.lecturo.lecturo.di

import android.content.Context
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.pref.UserPreference
import com.lecturo.lecturo.data.pref.dataStore
import com.lecturo.lecturo.data.repository.*

object Injection {
    fun provideViewModelFactory(context: Context): ViewModelFactory {
        val database = AppDatabase.getDatabase(context)
        val userPreference = UserPreference.getInstance(context.dataStore)

        val userRepository = UserRepository.getInstance(userPreference)
        val tasksRepository = TasksRepository(database.tasksDao())
        val eventRepository = EventRepository(database.eventDao())
        val teachingRepository = TeachingRepository(database.teachingRuleDao(), database.calendarEntryDao())
        val calendarRepository = CalendarRepository(database.calendarEntryDao())

        // DIUBAH: Kirim 'context' sebagai 'application'
        return ViewModelFactory(
            userRepository,
            tasksRepository,
            eventRepository,
            teachingRepository,
            calendarRepository,
            context.applicationContext as android.app.Application
        )
    }
}