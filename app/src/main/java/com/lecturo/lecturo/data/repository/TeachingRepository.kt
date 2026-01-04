package com.lecturo.lecturo.data.repository

import android.util.Log
import androidx.lifecycle.LiveData
import com.google.firebase.auth.FirebaseAuth
import com.lecturo.lecturo.data.db.dao.TeachingRuleDao
import com.lecturo.lecturo.data.db.dao.CalendarEntryDao
import com.lecturo.lecturo.data.model.TeachingRule
import com.lecturo.lecturo.data.model.CalendarEntry
import com.lecturo.lecturo.data.model.TeachingRequest
import com.lecturo.lecturo.data.remote.ApiService
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class TeachingRepository(
    private val teachingRuleDao: TeachingRuleDao,
    private val calendarEntryDao: CalendarEntryDao,
    private val apiService: ApiService,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    fun getAllRules(): LiveData<List<TeachingRule>> {
        return teachingRuleDao.getAllRules()
    }

    suspend fun insertOrUpdateRule(rule: TeachingRule): Long {
        // 1. Simpan Lokal
        val localId = teachingRuleDao.insertOrUpdateRule(rule)

        // 2. Simpan Cloud
        val userId = auth.currentUser?.uid

        if (userId != null) {
            withContext(NonCancellable) {
                try {
                    val request = TeachingRequest(
                        userId = userId,
                        // SEKARANG BENAR: Menggunakan firestoreId
                        id = rule.firestoreId,
                        courseName = rule.courseName,
                        classCode = rule.classCode,
                        dayOfWeek = rule.dayOfWeek, // Sesuaikan field di TeachingRequest (day_of_week)
                        startTime = rule.startTime,
                        endTime = rule.endTime,
                        classroom = rule.classroom,
                        studentCount = rule.studentCount,
                        startDate = rule.startDate,
                        notificationMinutes = rule.notificationMinutes
                    )

                    Log.d("TeachingRepo", "Syncing rule: ${rule.courseName}")
                    val response = apiService.syncTeaching(request)

                    if (response.isSuccessful && response.body()?.status == "success") {
                        val responseData = response.body()?.data
                        val newFirestoreId = responseData?.get("firestore_id") as? String

                        // Update Firestore ID di lokal jika baru
                        if (newFirestoreId != null && rule.firestoreId != newFirestoreId) {
                            // PERBAIKAN: Gunakan localId untuk update row yang sama
                            val updatedRule = rule.copy(localId = localId, firestoreId = newFirestoreId)
                            teachingRuleDao.insertOrUpdateRule(updatedRule)
                            Log.d("TeachingRepo", "Sync Success. Cloud ID: $newFirestoreId")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TeachingRepo", "Sync Failed: ${e.message}")
                }
            }
        }
        return localId
    }

    suspend fun deleteRuleById(ruleId: Long) {
        val rule = teachingRuleDao.getRuleById(ruleId)

        // Hapus Lokal
        teachingRuleDao.deleteRuleById(ruleId)

        // Hapus Cloud
        if (rule?.firestoreId != null && auth.currentUser != null) {
            withContext(NonCancellable) {
                try {
                    apiService.deleteTeaching(auth.currentUser!!.uid, rule.firestoreId!!)
                    Log.d("TeachingRepo", "Deleted from Backend")
                } catch (e: Exception) {
                    Log.e("TeachingRepo", "Backend Delete Failed: ${e.message}")
                }
            }
        }
    }

    // --- BAGIAN INI TETAP SAMA (TIDAK BERUBAH) ---
    suspend fun getRuleById(ruleId: Long): TeachingRule? {
        return teachingRuleDao.getRuleById(ruleId)
    }

    fun getRulesByDay(dayOfWeek: String): LiveData<List<TeachingRule>> {
        return teachingRuleDao.getRulesByDay(dayOfWeek)
    }

    suspend fun insertCalendarEntry(entry: CalendarEntry): Long {
        return calendarEntryDao.insertEntry(entry)
    }

    suspend fun insertCalendarEntries(entries: List<CalendarEntry>) {
        calendarEntryDao.insertEntries(entries)
    }

    suspend fun deleteCalendarEntriesForSource(type: String, id: Long) {
        calendarEntryDao.deleteEntriesForSource(type, id)
    }

    suspend fun getCalendarEntriesForSource(type: String, id: Long): List<CalendarEntry> {
        return calendarEntryDao.getEntriesForSource(type, id)
    }
}