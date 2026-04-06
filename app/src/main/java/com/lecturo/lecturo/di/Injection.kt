package com.lecturo.lecturo.di

import android.content.Context
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.remote.RetrofitClient // <-- Pastikan import ini
import com.lecturo.lecturo.data.pref.UserPreference
import com.lecturo.lecturo.data.pref.dataStore
import com.lecturo.lecturo.data.repository.*
import okhttp3.internal.platform.PlatformRegistry.applicationContext

object Injection {
    fun provideViewModelFactory(context: Context): ViewModelFactory {
        val database = AppDatabase.getDatabase(context)
        val userPreference = UserPreference.getInstance(context.dataStore)

        // GUNAKAN RETROFIT CLIENT MILIK ANDA
        val apiService = RetrofitClient.instance

        val userRepository = UserRepository.getInstance(userPreference)

        val teachingRepository = TeachingRepository(
            database.teachingRuleDao(),
            database.calendarEntryDao(),
            context.applicationContext
        )

        val tasksRepository = TasksRepository(
            database.tasksDao(),
            database.focusSessionDao(),
            context.applicationContext

        )

        val eventRepository = EventRepository(
            database.eventDao(),
            context.applicationContext
        )

        val calendarRepository = CalendarRepository(database.calendarEntryDao())
        val consultationRepository = ConsultationRepository(
            database.consultationDao(),
            context.applicationContext
            )

        return ViewModelFactory(
            userRepository,
            tasksRepository,
            eventRepository,
            teachingRepository,
            calendarRepository,
            consultationRepository,
            context.applicationContext as android.app.Application
        )
    }
}