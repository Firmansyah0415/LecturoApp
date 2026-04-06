package com.lecturo.lecturo.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.lecturo.lecturo.data.db.dao.FocusSessionDao
import com.lecturo.lecturo.data.db.dao.TasksDao
import com.lecturo.lecturo.data.model.FocusSession
import com.lecturo.lecturo.workers.SyncFocusWorker
import com.lecturo.lecturo.workers.SyncTasksWorker // Kita butuh ini untuk trigger sync Task

class FocusRepository(
    private val focusSessionDao: FocusSessionDao,
    private val tasksDao: TasksDao,
    private val context: Context
) {

    // --- QUERY DATA ---

    fun getHistoryByTask(taskId: Long): LiveData<List<FocusSession>> {
        return focusSessionDao.getHistoryByTask(taskId)
    }

    fun getTotalFocusTimeByTask(taskId: Long): LiveData<Int?> {
        return focusSessionDao.getTotalFocusTimeByTask(taskId)
    }

    // --- SAVE SESSION (Offline First) ---
    suspend fun saveSession(session: FocusSession) {
        // 1. Set Flag
        session.isSynced = false
        session.isDeleted = false

        // 2. Simpan Lokal
        focusSessionDao.insertSession(session)

        // 3. Trigger Worker
        scheduleFocusSync()
    }

    // --- DELETE SESSION (Offline First) ---
    suspend fun deleteSession(session: FocusSession) {
        // 1. Soft Delete Lokal
        focusSessionDao.softDeleteSession(session.id)

        // 2. Trigger Worker
        scheduleFocusSync()
    }

    // --- UPDATE TASK STATUS (Offline First via Task Worker) ---
    suspend fun updateTaskStatus(taskId: Long, isCompleted: Boolean) {
        // 1. Update Lokal (TasksDao) - Fungsi ini harus men-set is_synced=0 di TasksDao
        // Pastikan TasksDao.updateCompletedStatus melakukan: SET isCompleted = :val, is_synced = 0
        tasksDao.updateCompletedStatus(taskId, isCompleted)

        // 2. Trigger Task Worker (Bukan Focus Worker)
        scheduleTaskSync()
    }

    // --- WORKER TRIGGERS ---

    private fun scheduleFocusSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncFocusWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "SyncFocusWork",
            ExistingWorkPolicy.KEEP,
            syncRequest
        )
    }

    private fun scheduleTaskSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncTasksWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "SyncTasksWork",
            ExistingWorkPolicy.KEEP,
            syncRequest
        )
    }
}