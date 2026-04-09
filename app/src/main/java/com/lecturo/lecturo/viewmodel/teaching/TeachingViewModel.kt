package com.lecturo.lecturo.viewmodel.teaching

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.lecturo.lecturo.data.model.TeachingRule
import com.lecturo.lecturo.data.repository.TeachingRepository
import kotlinx.coroutines.launch

class TeachingViewModel(private val repository: TeachingRepository, application: Application) : AndroidViewModel(application) {

    // Mengambil daftar aturan mengajar untuk ditampilkan di UI
    val teachingRules: LiveData<List<TeachingRule>> = repository.getAllRules()

    // Fungsi simpan: Sekarang sangat simpel karena logika perulangan sudah diurus Repository
    fun saveNewTeachingRule(rule: TeachingRule) = viewModelScope.launch {
        try {
            repository.insertOrUpdateRule(rule)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Fungsi hapus: Repository akan otomatis membersihkan jadwal terkait di kalender
    fun deleteTeachingRule(ruleId: Long) = viewModelScope.launch {
        repository.deleteRuleById(ruleId)
    }

    suspend fun getTeachingRuleById(ruleId: Long): TeachingRule? {
        return repository.getRuleById(ruleId)
    }
}