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
        val dao = database.teachingScheduleDao()
        val apiService = RetrofitClient.instance

        val unsyncedSchedules = dao.getUnsyncedSchedules()
        if (unsyncedSchedules.isEmpty()) return@withContext Result.success()

        try {
            for (schedule in unsyncedSchedules) {
                if (schedule.userId.isNullOrEmpty()) continue

                if (schedule.isDeleted) {
                    if (schedule.firestoreId != null) {
                        try {
                            val response = apiService.deleteTeaching(schedule.userId, schedule.firestoreId!!)
                            if (response.isSuccessful) {
                                dao.deleteSchedulePermanently(schedule.localId)
                            } else {
                                return@withContext Result.retry()
                            }
                        } catch (e: Exception) {
                            return@withContext Result.retry()
                        }
                    } else {
                        dao.deleteSchedulePermanently(schedule.localId)
                    }
                } else {
                    val request = TeachingRequest(
                        userId = schedule.userId,
                        id = schedule.firestoreId,
                        courseName = schedule.courseName,
                        classCode = schedule.classCode,
                        dayOfWeek = schedule.dayOfWeek,
                        date = schedule.date,
                        startTime = schedule.startTime,
                        endTime = schedule.endTime,
                        classroom = schedule.classroom,
                        studentCount = schedule.studentCount,
                        meetingNumber = schedule.meetingNumber,
                        isCompleted = schedule.isCompleted,
                        notificationMinutes = schedule.notificationMinutes
                    )
                    try {
                        val response = apiService.syncTeaching(request)
                        if (response.isSuccessful && response.body()?.status == "success") {
                            val newFirestoreId = response.body()?.data?.get("firestore_id") as? String
                            if (newFirestoreId != null) dao.updateSyncStatus(schedule.localId, newFirestoreId)
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