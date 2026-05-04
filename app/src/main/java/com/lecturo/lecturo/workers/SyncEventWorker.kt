package com.lecturo.lecturo.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.model.EventRequest
import com.lecturo.lecturo.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncEventWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.eventDao()
        val apiService = RetrofitClient.instance

        val unsyncedEvents = dao.getUnsyncedEvents()
        if (unsyncedEvents.isEmpty()) return@withContext Result.success()

        try {
            for (event in unsyncedEvents) {
                if (event.userId.isNullOrEmpty()) continue

                if (event.isDeleted) {
                    if (event.firestoreId != null) {
                        try {
                            val response = apiService.deleteEvent(event.userId, event.firestoreId!!)
                            if (response.isSuccessful) {
                                dao.hardDelete(event.id)
                            } else {
                                return@withContext Result.retry()
                            }
                        } catch (e: Exception) {
                            return@withContext Result.retry()
                        }
                    } else {
                        dao.hardDelete(event.id)
                    }
                } else {
                    val request = EventRequest(
                        uid = event.userId,
                        eventId = event.firestoreId,
                        title = event.title,
                        category = event.category,
                        priority = event.priority ?: "Sedang",
                        inputSource = event.inputSource ?: "MANUAL",
                        date = event.date,
                        time = event.time,
                        endTime = event.endTime,
                        location = event.location ?: "-",
                        description = event.description ?: "",
                        isCompleted = event.isCompleted,
                        notificationMinutes = event.notificationMinutesBefore
                    )
                    try {
                        val response = apiService.syncEvent(request)
                        if (response.isSuccessful && response.body()?.status == "success") {
                            val newFirestoreId = response.body()?.data?.get("firestore_id") as? String
                            if (newFirestoreId != null) dao.updateSyncStatus(event.id, newFirestoreId)
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