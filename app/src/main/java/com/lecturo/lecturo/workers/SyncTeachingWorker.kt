package com.lecturo.lecturo.workers

import android.content.Context
import android.util.Log
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

        // Ambil data yang isSynced = 0 (Bisa jadi data baru, update, atau soft-deleted)
        val unsyncedRules = dao.getUnsyncedRules()

        if (unsyncedRules.isEmpty()) {
            return@withContext Result.success()
        }

        Log.d("SyncTeaching", "Memproses ${unsyncedRules.size} data...")

        try {
            for (rule in unsyncedRules) {

                // Cek Validitas User ID
                if (rule.userId.isNullOrEmpty()) {
                    Log.e("SyncTeaching", "SKIP: Data rusak (No User ID).")
                    continue
                }

                // --- CABANG LOGIKA: DELETE vs UPLOAD ---
                if (rule.isDeleted) {
                    // KASUS 1: Data ini minta DIHAPUS
                    if (rule.firestoreId != null) {
                        try {
                            Log.d("SyncTeaching", "Menghapus di Server: ${rule.courseName}")
                            val response = apiService.deleteTeaching(rule.userId, rule.firestoreId!!)

                            // Jika sukses atau data sudah tidak ada (404), hapus permanen di lokal
                            if (response.isSuccessful || response.code() == 404) {
                                dao.deleteRulePermanently(rule.localId)
                                Log.d("SyncTeaching", "SUKSES DELETE PERMANEN: ${rule.courseName}")
                            } else {
                                Log.e("SyncTeaching", "GAGAL DELETE Server: ${response.code()}")
                            }
                        } catch (e: Exception) {
                            Log.e("SyncTeaching", "GAGAL DELETE Network: ${e.message}")
                            // Jangan retry global agar tidak memblokir item lain
                        }
                    } else {
                        // Data belum pernah naik ke server, langsung hapus lokal saja
                        dao.deleteRulePermanently(rule.localId)
                    }

                } else {
                    // KASUS 2: Data ini minta DI-UPLOAD (Insert/Update)
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
                        repetitionType = rule.repetitionType, // <--- TAMBAHKAN INI
                        repetitionValue = rule.repetitionValue, // <--- TAMBAHKAN INI
                        notificationMinutes = rule.notificationMinutes
                    )

                    val response = apiService.syncTeaching(request)

                    if (response.isSuccessful && response.body()?.status == "success") {
                        val newFirestoreId = response.body()?.data?.get("firestore_id") as? String
                        if (newFirestoreId != null) {
                            dao.updateSyncStatus(rule.localId, newFirestoreId)
                            Log.d("SyncTeaching", "SUKSES UPLOAD: ${rule.courseName}")
                        }
                    } else {
                        Log.e("SyncTeaching", "GAGAL UPLOAD: ${response.code()}")
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncTeaching", "CRASH: ${e.message}")
            Result.retry()
        }
    }
}