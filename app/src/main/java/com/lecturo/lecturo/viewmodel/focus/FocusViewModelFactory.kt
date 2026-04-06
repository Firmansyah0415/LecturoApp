package com.lecturo.lecturo.viewmodel.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lecturo.lecturo.data.repository.FocusRepository

class FocusViewModelFactory(private val repository: FocusRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FocusViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FocusViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}