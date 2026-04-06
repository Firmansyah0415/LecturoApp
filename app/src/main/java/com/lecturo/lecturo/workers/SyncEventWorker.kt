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

        if (unsyncedEvents.isEmpty()) {
            return@withContext Result.success()
        }

        Log.d("SyncEvent", "Memproses ${unsyncedEvents.size} event...")

        try {
            for (event in unsyncedEvents) {

                if (event.userId.isNullOrEmpty()) {
                    Log.e("SyncEvent", "SKIP: Data rusak (No User ID).")
                    continue
                }

                // --- LOGIKA CABANG: DELETE vs UPLOAD ---

                if (event.isDeleted) {
                    // KASUS 1: DELETE
                    if (event.firestoreId != null) {
                        try {
                            Log.d("SyncEvent", "Mencoba DELETE di Server: ${event.title}")
                            val response = apiService.deleteEvent(event.userId, event.firestoreId!!)

                            if (response.isSuccessful || response.code() == 404) {
                                dao.hardDelete(event.id)
                                Log.d("SyncEvent", "SUKSES DELETE PERMANEN: ${event.title}")
                            } else {
                                Log.e("SyncEvent", "GAGAL DELETE Server: ${response.code()}")
                            }
                        } catch (e: Exception) {
                            Log.e("SyncEvent", "GAGAL DELETE Network: ${e.message}")
                        }
                    } else {
                        // Belum pernah ke server, hapus lokal langsung
                        dao.hardDelete(event.id)
                    }

                } else {
                    // KASUS 2: UPLOAD (INSERT/UPDATE)
                    val request = EventRequest(
                        uid = event.userId,
                        eventId = event.firestoreId,
                        title = event.title,
                        category = event.category,
                        priority = event.priority ?: "Sedang",
                        inputSource = event.inputSource ?: "MANUAL",
                        date = event.date,
                        time = event.time,
                        location = event.location ?: "-",
                        description = event.description ?: "",
                        isCompleted = event.isCompleted,
                        notificationMinutes = event.notificationMinutesBefore
                    )

                    val response = apiService.syncEvent(request)

                    if (response.isSuccessful && response.body()?.status == "success") {
                        val newFirestoreId = response.body()?.data?.get("firestore_id") as? String
                        if (newFirestoreId != null) {
                            dao.updateSyncStatus(event.id, newFirestoreId)
                            Log.d("SyncEvent", "SUKSES UPLOAD: ${event.title}")
                        }
                    } else {
                        Log.e("SyncEvent", "GAGAL UPLOAD: ${response.code()}")
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncEvent", "CRASH: ${e.message}")
            Result.retry()
        }
    }
}