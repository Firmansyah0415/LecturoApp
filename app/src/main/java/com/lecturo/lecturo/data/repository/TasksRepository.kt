package com.lecturo.lecturo.data.repository

import android.util.Log
import androidx.lifecycle.LiveData
import com.google.firebase.auth.FirebaseAuth
import com.lecturo.lecturo.data.db.dao.TasksDao
import com.lecturo.lecturo.data.model.Tasks
import com.lecturo.lecturo.data.model.TaskRequest
import com.lecturo.lecturo.data.remote.ApiService
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class TasksRepository(
    private val tasksDao: TasksDao,
    private val apiService: ApiService,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    fun getAllTasks(): LiveData<List<Tasks>> {
        return tasksDao.getAllTasks()
    }

    fun getTasksById(id: Long): LiveData<Tasks> {
        return tasksDao.getTasksById(id)
    }

    // --- FUNGSI SIMPAN (UPDATE) ---
    suspend fun insertOrUpdate(tasks: Tasks): Long {
        // 1. Simpan Lokal
        val localId = tasksDao.insertOrUpdate(tasks)

        // 2. Simpan Cloud
        val userId = auth.currentUser?.uid
        if (userId != null) {
            withContext(NonCancellable) {
                try {
                    val request = TaskRequest(
                        uid = userId,
                        taskId = tasks.firestoreId,
                        title = tasks.title,
                        description = tasks.description ?: "", // Pakai elvis operator biar aman
                        date = tasks.date,
                        time = tasks.time,
                        location = tasks.location ?: "-",
                        priority = tasks.priority ?: "Sedang",
                        inputSource = tasks.inputSource ?: "MANUAL",
                        isCompleted = tasks.isCompleted,
                        notificationMinutes = tasks.notificationMinutesBefore
                    )

                    val response = apiService.syncTask(request)

                    if (response.isSuccessful && response.body()?.status == "success") {
                        val newFirestoreId = response.body()?.data?.get("firestore_id") as? String

                        if (newFirestoreId != null && tasks.firestoreId != newFirestoreId) {
                            val updatedTask = tasks.copy(id = localId, firestoreId = newFirestoreId)
                            tasksDao.insertOrUpdate(updatedTask)
                            Log.d("TaskRepo", "Sync Sukses. ID Cloud: $newFirestoreId")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TaskRepo", "Gagal Sync (Mungkin Offline): ${e.message}")
                }
            }
        }
        return localId
    }

    // --- FUNGSI HAPUS (SUDAH DIPERBAIKI) ---
    suspend fun deleteTasks(id: Long) {
        // 1. Ambil data dulu sebelum dihapus (untuk mendapatkan firestoreId)
        // Pastikan Anda sudah menambahkan fungsi 'getTaskById' di DAO (Langkah 1)
        val task = tasksDao.getTaskById(id)

        // 2. Hapus dari Lokal (Room)
        tasksDao.deleteById(id)

        // 3. Hapus dari Cloud (Node.js)
        if (task?.firestoreId != null && auth.currentUser != null) {
            withContext(NonCancellable) {
                try {
                    apiService.deleteTask(auth.currentUser!!.uid, task.firestoreId!!)
                    Log.d("TaskRepo", "Sukses hapus data di Backend")
                } catch (e: Exception) {
                    Log.e("TaskRepo", "Gagal hapus di Backend: ${e.message}")
                }
            }
        }
    }

    suspend fun updateTasksCompletedStatus(id: Long, completed: Boolean) {
        tasksDao.updateCompletedStatus(id, completed)
    }

    suspend fun getLatestTask(): Tasks? {
        return tasksDao.getLatestTask()
    }
}