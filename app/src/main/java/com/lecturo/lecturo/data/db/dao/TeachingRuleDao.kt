package com.lecturo.lecturo.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.lecturo.lecturo.data.model.TeachingRule

@Dao
interface TeachingRuleDao {

    @Query("SELECT * FROM teaching_rules ORDER BY dayOfWeek, startTime ASC")
    fun getAllRules(): LiveData<List<TeachingRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateRule(rule: TeachingRule): Long

    // Perbaiki 'id' menjadi 'localId'
    @Query("DELETE FROM teaching_rules WHERE localId = :ruleId")
    suspend fun deleteRuleById(ruleId: Long)

    // TAMBAHKAN INI:
    // Pastikan nama tabelnya sesuai (misal: teaching_rules)
    @Query("DELETE FROM teaching_rules")
    suspend fun deleteAll()

    // Perbaiki 'id' menjadi 'localId'
    @Query("SELECT * FROM teaching_rules WHERE localId = :ruleId")
    suspend fun getRuleById(ruleId: Long): TeachingRule?

    @Query("SELECT * FROM teaching_rules WHERE dayOfWeek = :dayOfWeek ORDER BY startTime ASC")
    fun getRulesByDay(dayOfWeek: String): LiveData<List<TeachingRule>>
}