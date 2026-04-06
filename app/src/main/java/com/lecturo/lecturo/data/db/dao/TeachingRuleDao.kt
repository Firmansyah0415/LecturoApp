package com.lecturo.lecturo.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.lecturo.lecturo.data.model.TeachingRule

@Dao
interface TeachingRuleDao {

    // 1. READ: Hanya ambil data yang TIDAK ditandai hapus (is_deleted = 0)
    @Query("SELECT * FROM teaching_rules WHERE is_deleted = 0 ORDER BY dayOfWeek, startTime ASC")
    fun getAllRules(): LiveData<List<TeachingRule>>

    // 2. READ SYNC: Ambil semua data kotor (Entah itu baru, update, atau mau dihapus)
    @Query("SELECT * FROM teaching_rules WHERE is_synced = 0")
    suspend fun getUnsyncedRules(): List<TeachingRule>

    // 3. INSERT/UPDATE: Tetap sama
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateRule(rule: TeachingRule): Long

    // 4. SOFT DELETE: Hanya tandai data (Dipanggil saat user klik hapus)
    @Query("UPDATE teaching_rules SET is_deleted = 1, is_synced = 0 WHERE localId = :ruleId")
    suspend fun softDeleteRule(ruleId: Long)

    // 5. HARD DELETE: Hapus permanen (Dipanggil Worker setelah sukses delete di cloud)
    @Query("DELETE FROM teaching_rules WHERE localId = :ruleId")
    suspend fun deleteRulePermanently(ruleId: Long)

    // 6. UPDATE STATUS: Tandai sudah sync
    @Query("UPDATE teaching_rules SET is_synced = 1, firestoreId = :firestoreId WHERE localId = :localId")
    suspend fun updateSyncStatus(localId: Long, firestoreId: String)

    // Helper lainnya
    @Query("SELECT * FROM teaching_rules WHERE localId = :ruleId")
    suspend fun getRuleById(ruleId: Long): TeachingRule?

    @Query("SELECT * FROM teaching_rules WHERE dayOfWeek = :dayOfWeek AND is_deleted = 0 ORDER BY startTime ASC")
    fun getRulesByDay(dayOfWeek: String): LiveData<List<TeachingRule>>

    // Untuk Calendar
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<TeachingRule>)

    @Query("DELETE FROM teaching_rules")
    suspend fun deleteAll()

    @Query("SELECT * FROM teaching_rules WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getRuleByFirestoreId(firestoreId: String): TeachingRule?

    @Update
    suspend fun updateRuleRaw(rule: TeachingRule)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRuleRaw(rule: TeachingRule): Long
}