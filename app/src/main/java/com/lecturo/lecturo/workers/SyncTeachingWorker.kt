package com.lecturo.lecturo.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.model.TeachingRequest
import com.lecturo.lecturo.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncTeachingWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.teachingRuleDao()
        val apiService = RetrofitClient.instance

        val unsyncedRules = dao.getUnsyncedRules()
        if (unsyncedRules.isEmpty()) return@withContext Result.success()

        try {
            for (rule in unsyncedRules) {
                if (rule.userId.isNullOrEmpty()) continue

                if (rule.isDeleted) {
                    if (rule.firestoreId != null) {
                        try {
                            val response = apiService.deleteTeaching(rule.userId, rule.firestoreId!!)
                            if (response.isSuccessful) {
                                dao.deleteRulePermanently(rule.localId)
                            } else {
                                return@withContext Result.retry()
                            }
                        } catch (e: Exception) {
                            return@withContext Result.retry()
                        }
                    } else {
                        dao.deleteRulePermanently(rule.localId)
                    }
                } else {
                    val request = TeachingRequest(
                        userId = rule.userId,
                        id = rule.firestoreId,
                        courseName = rule.courseName,
                        classCode = rule.classCode,
                        dayOfWeek = rule.dayOfWeek,
                        startTime = rule.startTime,
                        endTime = rule.endTime,
                        classroom = rule.classroom,
                        studentCount = rule.studentCount,
                        startDate = rule.startDate,
                        repetitionType = rule.repetitionType,
                        repetitionValue = rule.repetitionValue,
                        notificationMinutes = rule.notificationMinutes
                    )
                    try {
                        val response = apiService.syncTeaching(request)
                        if (response.isSuccessful && response.body()?.status == "success") {
                            val newFirestoreId = response.body()?.data?.get("firestore_id") as? String
                            if (newFirestoreId != null) dao.updateSyncStatus(rule.localId, newFirestoreId)
                        } else {
                            return@withContext Result.retry()
                        }
                    } catch (e: Exception) {
                        return@withContext Result.retry()
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}