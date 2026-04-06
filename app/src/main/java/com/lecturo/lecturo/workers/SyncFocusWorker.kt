package com.lecturo.lecturo.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.model.FocusSessionRequest
import com.lecturo.lecturo.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SyncFocusWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.focusSessionDao()
        val apiService = RetrofitClient.instance

        val unsyncedSessions = dao.getUnsyncedSessions()

        if (unsyncedSessions.isEmpty()) {
            return@withContext Result.success()
        }

        Log.d("SyncFocus", "Memproses ${unsyncedSessions.size} sesi fokus...")

        try {
            for (session in unsyncedSessions) {

                // Cek User ID
                if (session.userId.isNullOrEmpty()) {
                    Log.e("SyncFocus", "SKIP: User ID kosong.")
                    continue
                }

                if (session.isDeleted) {
                    // --- KASUS 1: DELETE ---
                    if (session.firestoreId != null) {
                        try {
                            val response = apiService.deleteFocusSession(session.userId, session.firestoreId!!)
                            if (response.isSuccessful || response.code() == 404) {
                                dao.hardDeleteSession(session.id)
                                Log.d("SyncFocus", "SUKSES DELETE PERMANEN: ID ${session.id}")
                            } else {
                                Log.e("SyncFocus", "GAGAL DELETE Server: ${response.code()}")
                            }
                        } catch (e: Exception) {
                            Log.e("SyncFocus", "GAGAL DELETE Network: ${e.message}")
                        }
                    } else {
                        // Belum sync, hapus lokal
                        dao.hardDeleteSession(session.id)
                    }

                } else {
                    // --- KASUS 2: UPLOAD (INSERT) ---
                    // Mapping Data
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val request = FocusSessionRequest(
                        uid = session.userId,
                        sessionId = session.firestoreId,
                        taskFirestoreId = session.taskFirestoreId,
                        startTime = dateFormat.format(Date(session.startTime)),
                        endTime = dateFormat.format(Date(session.endTime)),
                        duration = session.durationMinutes,
                        status = session.status
                    )

                    val response = apiService.syncFocusSession(request)

                    if (response.isSuccessful && response.body()?.status == "success") {

                        // --- [PERBAIKAN BUG DUPLIKAT] ---
                        // Ambil data sebagai Map, lalu cari kunci "firestoreId" (Sesuai Logcat)
                        val dataMap = response.body()?.data as? Map<String, Any>
                        val newFirestoreId = dataMap?.get("firestoreId") as? String

                        if (newFirestoreId != null) {
                            // Update status sinkronisasi di database lokal (Room)
                            dao.updateSyncStatus(session.id, newFirestoreId)
                            Log.d("SyncFocus", "SUKSES UPLOAD & SYNC LOKAL: ID $newFirestoreId")
                        } else {
                            Log.e("SyncFocus", "GAGAL SYNC LOKAL: Kunci 'firestoreId' tidak ditemukan di JSON.")
                        }
                        // --------------------------------

                    } else {
                        Log.e("SyncFocus", "GAGAL UPLOAD: ${response.code()}")
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncFocus", "CRASH: ${e.message}")
            Result.retry()
        }
    }
}