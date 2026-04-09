package com.lecturo.lecturo.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.model.ConsultationPatternRequest
import com.lecturo.lecturo.data.model.ConsultationRequest
import com.lecturo.lecturo.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncConsultationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.consultationDao()
        val api = RetrofitClient.instance

        // ================= 1. SYNC SCHEDULES =================
        val unsyncedSchedules = dao.getUnsyncedSchedules()
        for (s in unsyncedSchedules) {
            if (s.userId.isNullOrEmpty()) continue

            if (s.isDeleted) {
                if (s.firestoreId != null) {
                    try {
                        val res = api.deleteConsultation(s.userId, s.firestoreId!!)
                        if (res.isSuccessful) {
                            dao.hardDeleteSchedule(s.id)
                        } else {
                            return@withContext Result.retry()
                        }
                    } catch (e: Exception) {
                        return@withContext Result.retry()
                    }
                } else {
                    dao.hardDeleteSchedule(s.id)
                }
            } else {
                val req = ConsultationRequest(
                    uid = s.userId,
                    consultationId = s.firestoreId,
                    recurringId = s.recurringId,
                    title = s.title,
                    date = s.date,
                    startTime = s.startTime,
                    endTime = s.endTime,
                    location = s.location,
                    description = s.description,
                    priority = s.priority ?: "Medium",
                    status = s.status,
                    inputSource = s.inputSource ?: "MANUAL",
                    notificationMinutes = s.notificationMinutesBefore
                )
                try {
                    val res = api.syncConsultation(req)
                    if (res.isSuccessful && res.body()?.status == "success") {
                        val fid = res.body()?.data?.firestoreId
                        if (fid != null) dao.updateScheduleSyncStatus(s.id, fid)
                    } else {
                        return@withContext Result.retry()
                    }
                } catch (e: Exception) {
                    return@withContext Result.retry()
                }
            }
        }

        // ================= 2. SYNC PATTERNS =================
        val unsyncedPatterns = dao.getUnsyncedPatterns()
        for (p in unsyncedPatterns) {
            if (p.userId.isNullOrEmpty()) continue

            if (p.isDeleted) {
                if (p.firestoreId != null) {
                    try {
                        val res = api.deletePattern(p.userId, p.firestoreId!!)
                        if (res.isSuccessful) {
                            dao.hardDeletePattern(p.id)
                        } else {
                            return@withContext Result.retry()
                        }
                    } catch (e: Exception) {
                        return@withContext Result.retry()
                    }
                } else {
                    dao.hardDeletePattern(p.id)
                }
            } else {
                val req = ConsultationPatternRequest(
                    uid = p.userId,
                    patternId = p.firestoreId,
                    titleTemplate = p.titleTemplate,
                    dayOfWeek = p.dayOfWeek,
                    startTime = p.startTime,
                    endTime = p.endTime,
                    locationDefault = p.locationDefault,
                    isActive = p.isActive
                )
                try {
                    val res = api.syncPattern(req)
                    if (res.isSuccessful && res.body()?.status == "success") {
                        val fid = res.body()?.data?.firestoreId
                        if (fid != null) dao.updatePatternSyncStatus(p.id, fid)
                    } else {
                        return@withContext Result.retry()
                    }
                } catch (e: Exception) {
                    return@withContext Result.retry()
                }
            }
        }
        Result.success()
    }
}