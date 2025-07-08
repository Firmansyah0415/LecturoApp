package com.lecturo.lecturo.di

import android.content.Context
import com.lecturo.lecturo.data.UserRepository
import com.lecturo.lecturo.data.pref.UserPreference
import com.lecturo.lecturo.data.pref.dataStore

object Injection {
    fun provideRepository(context: Context): UserRepository {
        val pref = UserPreference.getInstance(context.dataStore)
        return UserRepository.getInstance(pref)
    }
}