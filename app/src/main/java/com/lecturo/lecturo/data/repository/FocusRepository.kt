package com.lecturo.lecturo.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.lecturo.lecturo.data.db.AppDatabase // 🔴 [TAMBAHAN] Untuk akses kalender
import com.lecturo.lecturo.data.db.dao.FocusSessionDao
import com.lecturo.lecturo.data.db.dao.TasksDao
import com.lecturo.lecturo.data.model.FocusSession
import com.lecturo.lecturo.notifications.NotificationScheduler // 🔴 [TAMBAHAN] Untuk cabut alarm
import com.lecturo.lecturo.workers.SyncFocusWorker
import com.lecturo.lecturo.workers.SyncTasksWorker

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
        session.isSynced = false
        session.isDeleted = false
        focusSessionDao.insertSession(session)
        scheduleFocusSync()
    }

    // --- DELETE SESSION (Offline First) ---
    suspend fun deleteSession(session: FocusSession) {
        focusSessionDao.softDeleteSession(session.id)
        scheduleFocusSync()
    }

    // --- UPDATE TASK STATUS (Offline First via Task Worker) ---
    suspend fun updateTaskStatus(taskId: Long, isCompleted: Boolean) {
        // 1. Update Lokal di tabel Tasks
        tasksDao.updateCompletedStatus(taskId, isCompleted)

        // 🔴 [PERBAIKAN BUG OPSI B] Sinkronkan juga statusnya ke Kalender Agregator
        val dbSqlite = AppDatabase.getDatabase(context)
        val calendarDao = dbSqlite.calendarEntryDao()
        val scheduler = NotificationScheduler(context)

        // Bisikkan ke Kalender bahwa Tugas ini statusnya berubah
        calendarDao.updateStatusBySource("TASK", taskId, isCompleted)

        // Jika tugas diselesaikan, cabut alarmnya agar tidak berisik
        if (isCompleted) {
            val entriesToCancel = calendarDao.getEntriesForSource("TASK", taskId)
            entriesToCancel.forEach { scheduler.cancelNotification(it.notificationId) }
        }

        // 2. Trigger Task Worker untuk lapor ke Cloud
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