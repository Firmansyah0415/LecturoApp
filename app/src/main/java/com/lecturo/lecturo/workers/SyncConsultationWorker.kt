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
                // DELETE
                if (s.firestoreId != null) {
                    try {
                        val res = api.deleteConsultation(s.userId, s.firestoreId!!)
                        if (res.isSuccessful || res.code() == 404) {
                            dao.hardDeleteSchedule(s.id)
                            Log.d("SyncConsul", "Deleted Schedule: ${s.title}")
                        }
                    } catch (e: Exception) { Log.e("SyncConsul", "Err Del Schedule: ${e.message}") }
                } else {
                    dao.hardDeleteSchedule(s.id)
                }
            } else {
                // UPLOAD / UPDATE
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
                    }
                } catch (e: Exception) { Log.e("SyncConsul", "Err Sync Schedule: ${e.message}") }
            }
        }

        // ================= 2. SYNC PATTERNS =================
        val unsyncedPatterns = dao.getUnsyncedPatterns()

        for (p in unsyncedPatterns) {
            if (p.userId.isNullOrEmpty()) continue

            if (p.isDeleted) {
                // DELETE
                if (p.firestoreId != null) {
                    try {
                        val res = api.deletePattern(p.userId, p.firestoreId!!)
                        if (res.isSuccessful || res.code() == 404) {
                            dao.hardDeletePattern(p.id)
                            Log.d("SyncConsul", "Deleted Pattern: ${p.titleTemplate}")
                        }
                    } catch (e: Exception) { Log.e("SyncConsul", "Err Del Pattern: ${e.message}") }
                } else {
                    dao.hardDeletePattern(p.id)
                }
            } else {
                // UPLOAD
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
                    }
                } catch (e: Exception) { Log.e("SyncConsul", "Err Sync Pattern: ${e.message}") }
            }
        }

        Result.success()
    }
}