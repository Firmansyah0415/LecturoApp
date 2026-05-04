package com.lecturo.lecturo.data.repository

import com.google.firebase.firestore.Source
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.lecturo.lecturo.data.db.dao.FocusSessionDao
import com.lecturo.lecturo.data.db.dao.TasksDao
import com.lecturo.lecturo.data.model.TaskWithFocusStats
import com.lecturo.lecturo.data.model.Tasks
import com.lecturo.lecturo.workers.SyncFocusWorker
import com.lecturo.lecturo.workers.SyncTasksWorker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.model.CalendarEntry
import com.lecturo.lecturo.notifications.NotificationScheduler
import kotlinx.coroutines.tasks.await

class TasksRepository(
    private val tasksDao: TasksDao,
    private val focusSessionDao: FocusSessionDao, // [TAMBAHAN PENTING] Untuk akses data sesi
    private val context: Context
) {

    fun getAllTasks(): LiveData<List<Tasks>> {
        return tasksDao.getAllTasks()
    }

    fun getTasksById(id: Long): LiveData<Tasks> {
        return tasksDao.getTasksById(id)
    }

    fun getAllTasksWithStats(): LiveData<List<TaskWithFocusStats>> {
        return tasksDao.getAllTasksWithStats()
    }

    // [FITUR BARU] Tarik data dari Cloud (PULL SYNC)
    suspend fun syncTasksFromCloud() {
        try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val db = FirebaseFirestore.getInstance()

            val dbSqlite = AppDatabase.getDatabase(context)
            val calendarDao = dbSqlite.calendarEntryDao()
            val scheduler = NotificationScheduler(context)

            val snapshot = db.collection("users").document(uid).collection("tasks")
                .get(Source.SERVER).await()

            for (document in snapshot.documents) {
                try {
                    val firestoreId = document.id
                    val title = document.getString("title") ?: continue
                    val date = document.getString("date") ?: continue
                    val time = document.getString("time") ?: continue
                    val endTime = document.getString("end_time") ?: ""
                    val location = document.getString("location") ?: ""
                    val description = document.getString("description") ?: ""
                    val priority = document.getString("priority") ?: "Sedang"
                    val isCompleted = document.getBoolean("is_completed") ?: false
                    val notificationMinutes = document.getLong("notification_minutes")?.toInt() ?: 15

                    val existingTask = tasksDao.getTaskByFirestoreId(firestoreId)

                    if (existingTask != null) {
                        // [ATURAN 2]: HANYA timpa jika data lokal tidak punya perubahan tertunda (isSynced == true)
                        if (existingTask.isSynced) {
                            val updatedTask = existingTask.copy(
                                userId = uid,
                                title = title,
                                date = date,
                                time = time,
                                endTime = endTime,
                                location = location,
                                description = description,
                                priority = priority,
                                isCompleted = isCompleted,
                                notificationMinutesBefore = notificationMinutes,
                                isSynced = true,
                                isDeleted = false
                            )
                            updatedTask.firestoreId = firestoreId
                            tasksDao.updateTaskRaw(updatedTask)

                            // [TAMBAHAN BARU] Bersihkan kalender dan alarm lama
                            val oldEntries = calendarDao.getEntriesForSource("TASK", existingTask.id)
                            oldEntries.forEach { scheduler.cancelNotification(it.notificationId) }
                            calendarDao.deleteEntriesForSource("TASK", existingTask.id)

                            // Jika tugas belum selesai, buat alarm dan kalender baru
                            if (!updatedTask.isCompleted) {
                                val entry = CalendarEntry(
                                    title = updatedTask.title,
                                    date = updatedTask.date,
                                    time = updatedTask.time,
                                    endTime = updatedTask.endTime,
                                    category = "Tugas",
                                    priority = updatedTask.priority ?: "Sedang",
                                    sourceFeatureType = "TASK",
                                    sourceFeatureId = existingTask.id,
                                    notificationMinutesBefore = updatedTask.notificationMinutesBefore
                                )
                                val entryId = calendarDao.insertEntry(entry)
                                scheduler.scheduleNotification(entry.copy(id = entryId))
                            }
                        }
                    } else {
                        val newTask = Tasks(
                            userId = uid,
                            title = title,
                            date = date,
                            time = time,
                            endTime = endTime,
                            location = location,
                            description = description,
                            priority = priority,
                            isCompleted = isCompleted,
                            notificationMinutesBefore = notificationMinutes,
                            firestoreId = firestoreId,
                            isSynced = true,
                            isDeleted = false,
                            inputSource = "WEB_UPLOAD"
                        )
                        val newId = tasksDao.insertTaskRaw(newTask)

                        // [TAMBAHAN BARU] Masukkan ke Agenda Home dan Set Notifikasi
                        if (!newTask.isCompleted) {
                            val entry = CalendarEntry(
                                title = newTask.title,
                                date = newTask.date,
                                time = newTask.time,
                                endTime = newTask.endTime,
                                category = "Tugas",
                                priority = newTask.priority ?: "Sedang",
                                sourceFeatureType = "TASK",
                                sourceFeatureId = newId,
                                notificationMinutesBefore = newTask.notificationMinutesBefore
                            )
                            val entryId = calendarDao.insertEntry(entry)
                            scheduler.scheduleNotification(entry.copy(id = entryId))
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        } catch (e: Exception) {
            // Sengaja dikosongkan agar jika offline, error Source.SERVER diabaikan dengan aman
        }
    }

    suspend fun insertOrUpdate(tasks: Tasks): Long {
        tasks.isSynced = false
        tasks.isDeleted = false

        val localId = tasksDao.insertOrUpdate(tasks)

        // [BASMI HANTU UPDATE & RE-SCHEDULE ALARM]
        val dbSqlite = com.lecturo.lecturo.data.db.AppDatabase.getDatabase(context)
        val calendarDao = dbSqlite.calendarEntryDao()
        val scheduler = com.lecturo.lecturo.notifications.NotificationScheduler(context)

        // Hapus entri lama
        val oldEntries = calendarDao.getEntriesForSource("TASK", localId)
        oldEntries.forEach { scheduler.cancelNotification(it.notificationId) }
        calendarDao.deleteEntriesForSource("TASK", localId)

        // Buat entri baru jika belum selesai
        if (!tasks.isCompleted) {
            val entry = CalendarEntry(
                title = tasks.title,
                date = tasks.date,
                time = tasks.time,
                endTime = tasks.endTime,
                category = "Tugas",
                priority = tasks.priority ?: "Sedang",
                sourceFeatureType = "TASK",
                sourceFeatureId = localId,
                notificationMinutesBefore = tasks.notificationMinutesBefore
            )
            val entryId = calendarDao.insertEntry(entry)
            scheduler.scheduleNotification(entry.copy(id = entryId))
        }

        scheduleSync()
        return localId
    }

    // --- LOGIC BARU: CASCADE SOFT DELETE ---
    suspend fun deleteTasks(id: Long) {
        // 1. Tandai hapus Tugas di lokal (Soft Delete)
        tasksDao.softDelete(id)

        // 2. [TAMBAHAN] Tandai hapus SEMUA Sesi Fokus yang terkait dengan Tugas ini (Cascade Soft Delete)
        focusSessionDao.softDeleteSessionsByTaskId(id)

        // 3. Trigger worker untuk hapus Tugas di cloud
        scheduleSync()

        // 4. [TAMBAHAN] Trigger worker untuk hapus Sesi Fokus di cloud
        scheduleFocusSync()
    }

    // --- LOGIC WORKER: SYNC TASKS ---
    private fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncTasksWorker>()
            .setConstraints(constraints)
            .build()

        // PENTING: Gunakan 'KEEP' agar worker tidak numpuk
        WorkManager.getInstance(context).enqueueUniqueWork(
            "SyncTasksWork", // Nama Unik
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

    // --- [TAMBAHAN BARU] LOGIC WORKER: SYNC FOCUS ---
    private fun scheduleFocusSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncFocusWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "SyncFocusWork",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

    // Fungsi Checklist Selesai
    suspend fun updateTasksCompletedStatus(id: Long, completed: Boolean) {
        // Otomatis set is_synced=0 di DAO
        tasksDao.updateCompletedStatus(id, completed)
        scheduleSync()
    }

    // Helper
    suspend fun getTaskByIdSuspend(id: Long): Tasks? {
        return tasksDao.getTaskById(id)
    }

    suspend fun getLatestTask(): Tasks? {
        return tasksDao.getLatestTask()
    }
}