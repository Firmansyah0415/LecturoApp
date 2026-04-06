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

        if (unsyncedTasks.isEmpty()) {
            return@withContext Result.success()
        }

        Log.d("SyncTasks", "Memproses ${unsyncedTasks.size} tugas...")

        try {
            for (task in unsyncedTasks) {

                if (task.userId.isNullOrEmpty()) {
                    Log.e("SyncTasks", "SKIP: Data rusak (No User ID).")
                    continue
                }

                // --- LOGIKA CABANG: DELETE vs UPLOAD ---

                if (task.isDeleted) {
                    // KASUS 1: DELETE
                    if (task.firestoreId != null) {
                        try {
                            Log.d("SyncTasks", "Mencoba DELETE di Server: ${task.title}")
                            val response = apiService.deleteTask(task.userId, task.firestoreId!!)

                            if (response.isSuccessful || response.code() == 404) {
                                dao.hardDelete(task.id)
                                Log.d("SyncTasks", "SUKSES DELETE PERMANEN: ${task.title}")
                            } else {
                                Log.e("SyncTasks", "GAGAL DELETE Server: ${response.code()}")
                            }
                        } catch (e: Exception) {
                            Log.e("SyncTasks", "GAGAL DELETE Network: ${e.message}")
                        }
                    } else {
                        // Belum pernah ke server, hapus lokal langsung
                        dao.hardDelete(task.id)
                    }

                } else {
                    // KASUS 2: UPLOAD (INSERT/UPDATE)
                    val request = TaskRequest(
                        uid = task.userId,
                        taskId = task.firestoreId,
                        title = task.title,
                        description = task.description ?: "",
                        date = task.date,
                        time = task.time,
                        location = task.location ?: "-",
                        priority = task.priority ?: "Sedang",
                        inputSource = task.inputSource ?: "MANUAL",
                        isCompleted = task.isCompleted,
                        notificationMinutes = task.notificationMinutesBefore
                    )

                    val response = apiService.syncTask(request)

                    if (response.isSuccessful && response.body()?.status == "success") {
                        val newFirestoreId = response.body()?.data?.get("firestore_id") as? String
                        if (newFirestoreId != null) {
                            dao.updateSyncStatus(task.id, newFirestoreId)
                            Log.d("SyncTasks", "SUKSES UPLOAD: ${task.title}")
                        }
                    } else {
                        Log.e("SyncTasks", "GAGAL UPLOAD: ${response.code()}")
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncTasks", "CRASH: ${e.message}")
            Result.retry()
        }
    }
}