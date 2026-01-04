package com.lecturo.lecturo.di

import android.content.Context
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.remote.RetrofitClient // <-- Pastikan import ini
import com.lecturo.lecturo.data.pref.UserPreference
import com.lecturo.lecturo.data.pref.dataStore
import com.lecturo.lecturo.data.repository.*

object Injection {
    fun provideViewModelFactory(context: Context): ViewModelFactory {
        val database = AppDatabase.getDatabase(context)
        val userPreference = UserPreference.getInstance(context.dataStore)

        // GUNAKAN RETROFIT CLIENT MILIK ANDA
        val apiService = RetrofitClient.instance

        val userRepository = UserRepository.getInstance(userPreference)

        // Masukkan apiService ke Constructor TeachingRepository
        val teachingRepository = TeachingRepository(
            database.teachingRuleDao(),
            database.calendarEntryDao(),
            apiService // <--- Parameter baru yang diminta
        )

        val tasksRepository = TasksRepository(
            database.tasksDao(),
            apiService

        )
        val eventRepository = EventRepository(
            database.eventDao(),
            apiService
        )

        val calendarRepository = CalendarRepository(database.calendarEntryDao())

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