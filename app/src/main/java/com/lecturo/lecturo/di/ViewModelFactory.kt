package com.lecturo.lecturo.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lecturo.lecturo.data.repository.*
import com.lecturo.lecturo.ui.event.EventViewModel // <-- IMPORT BARU
import com.lecturo.lecturo.ui.login.LoginViewModel
import com.lecturo.lecturo.ui.main.MainViewModel

class ViewModelFactory(
    private val userRepository: UserRepository,
    private val tasksRepository: TasksRepository,
    private val eventRepository: EventRepository,
    private val teachingRepository: TeachingRepository,
    private val calendarRepository: CalendarRepository,
    private val application: android.app.Application // <-- TAMBAHKAN APPLICATION
) : ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(MainViewModel::class.java) -> {
                MainViewModel(
                    userRepository,
                    tasksRepository,
                    eventRepository,
                    teachingRepository,
                    calendarRepository
                ) as T
            }
            modelClass.isAssignableFrom(LoginViewModel::class.java) -> {
                LoginViewModel(userRepository) as T
            }
            // DIUBAH: Tambahkan logika untuk membuat EventViewModel
            modelClass.isAssignableFrom(EventViewModel::class.java) -> {
                EventViewModel(eventRepository, calendarRepository, application) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ViewModelFactory? = null

        @JvmStatic
        fun getInstance(context: Context): ViewModelFactory {
            return INSTANCE ?: synchronized(this) {
                // Gunakan context.applicationContext untuk mencegah memory leak
                INSTANCE ?: Injection.provideViewModelFactory(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}