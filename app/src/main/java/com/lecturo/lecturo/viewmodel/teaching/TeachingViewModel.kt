package com.lecturo.lecturo.viewmodel.teaching

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.lecturo.lecturo.data.model.TeachingSchedule
import com.lecturo.lecturo.data.repository.TeachingRepository
import kotlinx.coroutines.launch

class TeachingViewModel(private val repository: TeachingRepository, application: Application) : AndroidViewModel(application) {

    val teachingSchedules: LiveData<List<TeachingSchedule>> = repository.getAllSchedules()

    // --- State untuk Fitur Search & Filter ---
    val searchQuery = MutableLiveData<String>("")
    val isSortNewest = MutableLiveData<Boolean>(true)

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun toggleSort() {
        isSortNewest.value = !(isSortNewest.value ?: true)
    }

    // Fungsi simpan dengan mesin pencetak perulangan
    fun saveNewTeachingSchedule(schedule: TeachingSchedule, repeatMode: String, repeatValue: String) = viewModelScope.launch {
        try {
            repository.insertTeachingSchedules(schedule, repeatMode, repeatValue)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Fungsi update 1 jadwal (Misal: Centang selesai / Edit lokasi)
    fun updateTeachingSchedule(schedule: TeachingSchedule) = viewModelScope.launch {
        try {
            repository.updateSingleSchedule(schedule)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Fungsi hapus 1 jadwal
    fun deleteTeachingSchedule(scheduleId: Long) = viewModelScope.launch {
        repository.deleteScheduleById(scheduleId)
    }

    suspend fun getTeachingScheduleById(scheduleId: Long): TeachingSchedule? {
        return repository.getScheduleById(scheduleId)
    }
}