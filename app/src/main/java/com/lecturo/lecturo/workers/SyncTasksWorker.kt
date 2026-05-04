package com.lecturo.lecturo.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.model.TaskRequest
import com.lecturo.lecturo.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncTasksWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.tasksDao()
        val apiService = RetrofitClient.instance

        val unsyncedTasks = dao.getUnsyncedTasks()
        if (unsyncedTasks.isEmpty()) return@withContext Result.success()

        Log.d("SyncTasks", "Memproses ${unsyncedTasks.size} tugas...")

        try {
            for (task in unsyncedTasks) {
                if (task.userId.isNullOrEmpty()) continue

                if (task.isDeleted) {
                    if (task.firestoreId != null) {
                        try {
                            val response = apiService.deleteTask(task.userId, task.firestoreId!!)
                            // PERBAIKAN 1: Hapus || response.code() == 404
                            if (response.isSuccessful) {
                                dao.hardDelete(task.id)
                            } else {
                                // PERBAIKAN 2: Tolak Hapus Lokal & Retry
                                return@withContext Result.retry()
                            }
                        } catch (e: Exception) {
                            // PERBAIKAN 3: Jika server/ngrok mati, Retry
                            return@withContext Result.retry()
                        }
                    } else {
                        dao.hardDelete(task.id)
                    }
                } else {
                    val request = TaskRequest(
                        uid = task.userId,
                        taskId = task.firestoreId,
                        title = task.title,
                        description = task.description ?: "",
                        date = task.date,
                        time = task.time,
                        endTime = task.endTime,
                        location = task.location ?: "-",
                        priority = task.priority ?: "Sedang",
                        inputSource = task.inputSource ?: "MANUAL",
                        isCompleted = task.isCompleted,
                        notificationMinutes = task.notificationMinutesBefore
                    )
                    try {
                        val response = apiService.syncTask(request)
                        if (response.isSuccessful && response.body()?.status == "success") {
                            val newFirestoreId = response.body()?.data?.get("firestore_id") as? String
                            if (newFirestoreId != null) dao.updateSyncStatus(task.id, newFirestoreId)
                        } else {
                            return@withContext Result.retry() // Gagal Upload? Retry!
                        }
                    } catch (e: Exception) {
                        return@withContext Result.retry() // Server mati saat upload? Retry!
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}