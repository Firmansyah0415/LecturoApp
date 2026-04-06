package com.lecturo.lecturo.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lecturo.lecturo.data.repository.*
import com.lecturo.lecturo.viewmodel.auth.LoginViewModel
import com.lecturo.lecturo.viewmodel.event.EventViewModel
import com.lecturo.lecturo.viewmodel.main.MainViewModel
import com.lecturo.lecturo.viewmodel.auth.CompleteProfileViewModel
import com.lecturo.lecturo.viewmodel.profile.ProfileViewModel
import com.lecturo.lecturo.viewmodel.consultation.ConsultationViewModel

class ViewModelFactory(
    private val userRepository: UserRepository,
    private val tasksRepository: TasksRepository,
    private val eventRepository: EventRepository,
    private val teachingRepository: TeachingRepository,
    private val calendarRepository: CalendarRepository,
    private val consultationRepository: ConsultationRepository,
    private val application: android.app.Application
) : ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            // 1. MAIN VIEW MODEL
            modelClass.isAssignableFrom(MainViewModel::class.java) -> {
                MainViewModel(
                    userRepository,
                    tasksRepository,
                    eventRepository,
                    teachingRepository,
                    calendarRepository,
                    consultationRepository
                ) as T
            }

            // 2. LOGIN VIEW MODEL
            modelClass.isAssignableFrom(LoginViewModel::class.java) -> {
                LoginViewModel(userRepository) as T
            }

            // 3. EVENT VIEW MODEL
            modelClass.isAssignableFrom(EventViewModel::class.java) -> {
                EventViewModel(eventRepository, calendarRepository, application) as T
            }

            // 4. COMPLETE PROFILE VIEW MODEL
            modelClass.isAssignableFrom(CompleteProfileViewModel::class.java) -> {
                CompleteProfileViewModel(userRepository) as T
            }

            // 5. PROFILE VIEW MODEL
            modelClass.isAssignableFrom(ProfileViewModel::class.java) -> {
                ProfileViewModel(userRepository) as T
            }

            // 6. KONSULTASI VIEW MODEL (INI YANG DITAMBAHKAN)
            // Karena ConsultationViewModel hanya butuh 'application' di constructor-nya
            modelClass.isAssignableFrom(ConsultationViewModel::class.java) -> {
                ConsultationViewModel(application) as T
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
                INSTANCE ?: Injection.provideViewModelFactory(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}