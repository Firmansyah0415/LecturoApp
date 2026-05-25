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
    private val focusSessionDao: FocusSessionDao,
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

    suspend fun syncTasksFromCloud() {
        try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val db = FirebaseFirestore.getInstance()

            val dbSqlite = AppDatabase.getDatabase(context)
            val calendarDao = dbSqlite.calendarEntryDao()
            val scheduler = NotificationScheduler(context)

            val snapshot = db.collection("users").document(uid).collection("tasks")
                .get(Source.SERVER).await()

            val cloudFirestoreIds = mutableListOf<String>()

            for (document in snapshot.documents) {
                try {
                    val firestoreId = document.id
                    cloudFirestoreIds.add(firestoreId)
                    val title = document.getString("title") ?: continue
                    val date = document.getString("date") ?: continue
                    val time = document.getString("time") ?: continue
                    val endTime = document.getString("end_time") ?: ""
                    val location = document.getString("location") ?: ""
                    val description = document.getString("description") ?: ""
                    val priority = document.getString("priority") ?: "Sedang"
                    val isCompleted = document.getBoolean("is_completed") ?: false
                    val notificationMinutes = document.getLong("notification_minutes")?.toInt() ?: 15

                    val inputSource = document.getString("input_source") ?: "MANUAL"

                    val existingTask = tasksDao.getTaskByFirestoreId(firestoreId)

                    if (existingTask != null) {
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
                                inputSource = inputSource,
                                isSynced = true,
                                isDeleted = false
                            )
                            updatedTask.firestoreId = firestoreId
                            tasksDao.updateTaskRaw(updatedTask)

                            // Bersihkan entri alarm & kalender lama
                            val oldEntries = calendarDao.getEntriesForSource("TASK", existingTask.id)
                            oldEntries.forEach { scheduler.cancelNotification(it.notificationId) }
                            calendarDao.deleteEntriesForSource("TASK", existingTask.id)

                            val entry = CalendarEntry(
                                title = updatedTask.title,
                                date = updatedTask.date,
                                time = updatedTask.time,
                                endTime = updatedTask.endTime,
                                category = "Tugas",
                                priority = updatedTask.priority ?: "Sedang",
                                sourceFeatureType = "TASK",
                                sourceFeatureId = existingTask.id,
                                notificationMinutesBefore = updatedTask.notificationMinutesBefore,
                                isCompleted = updatedTask.isCompleted // Bawa status selesai
                            )
                            val entryId = calendarDao.insertEntry(entry)

                            // Alarm hanya bunyi jika belum selesai
                            if (!updatedTask.isCompleted) {
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
                            inputSource = inputSource
                        )
                        val newId = tasksDao.insertTaskRaw(newTask)

                        val entry = CalendarEntry(
                            title = newTask.title,
                            date = newTask.date,
                            time = newTask.time,
                            endTime = newTask.endTime,
                            category = "Tugas",
                            priority = newTask.priority ?: "Sedang",
                            sourceFeatureType = "TASK",
                            sourceFeatureId = newId,
                            notificationMinutesBefore = newTask.notificationMinutesBefore,
                            isCompleted = newTask.isCompleted
                        )
                        val entryId = calendarDao.insertEntry(entry)

                        if (!newTask.isCompleted) {
                            scheduler.scheduleNotification(entry.copy(id = entryId))
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }

            // LOGIKA PEMBASMI HANTU
            val localSyncedTasks = tasksDao.getSyncedTasksList()
            for (localTask in localSyncedTasks) {
                if (!cloudFirestoreIds.contains(localTask.firestoreId)) {
                    val oldEntries = calendarDao.getEntriesForSource("TASK", localTask.id)
                    oldEntries.forEach { scheduler.cancelNotification(it.notificationId) }
                    calendarDao.deleteEntriesForSource("TASK", localTask.id)
                    tasksDao.hardDelete(localTask.id)
                }
            }

        } catch (e: Exception) { }
    }

    suspend fun insertOrUpdate(tasks: Tasks): Long {
        tasks.isSynced = false
        tasks.isDeleted = false

        val localId = tasksDao.insertOrUpdate(tasks)

        val dbSqlite = AppDatabase.getDatabase(context)
        val calendarDao = dbSqlite.calendarEntryDao()
        val scheduler = NotificationScheduler(context)

        val oldEntries = calendarDao.getEntriesForSource("TASK", localId)
        oldEntries.forEach { scheduler.cancelNotification(it.notificationId) }
        calendarDao.deleteEntriesForSource("TASK", localId)

        // 🔴 [PERBAIKAN OPSI B] Selalu masukkan untuk riwayat kalender
        val entry = CalendarEntry(
            title = tasks.title,
            date = tasks.date,
            time = tasks.time,
            endTime = tasks.endTime,
            category = "Tugas",
            priority = tasks.priority ?: "Sedang",
            sourceFeatureType = "TASK",
            sourceFeatureId = localId,
            notificationMinutesBefore = tasks.notificationMinutesBefore,
            isCompleted = tasks.isCompleted
        )
        val entryId = calendarDao.insertEntry(entry)

        if (!tasks.isCompleted) {
            scheduler.scheduleNotification(entry.copy(id = entryId))
        }

        scheduleSync()
        return localId
    }

    suspend fun deleteTasks(id: Long) {
        // Hapus dari kalender jika dihapus user
        val dbSqlite = AppDatabase.getDatabase(context)
        val calendarDao = dbSqlite.calendarEntryDao()
        val scheduler = NotificationScheduler(context)
        val entries = calendarDao.getEntriesForSource("TASK", id)
        entries.forEach { scheduler.cancelNotification(it.notificationId) }
        calendarDao.deleteEntriesForSource("TASK", id)

        tasksDao.softDelete(id)
        focusSessionDao.softDeleteSessionsByTaskId(id)

        scheduleSync()
        scheduleFocusSync()
    }

    private fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val syncRequest = OneTimeWorkRequestBuilder<SyncTasksWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "SyncTasksWork",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

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

    // 🔴 [PERBAIKAN OPSI B] Fungsi Checklist Selesai agar sinkron ke kalender
    suspend fun updateTasksCompletedStatus(id: Long, completed: Boolean) {
        tasksDao.updateCompletedStatus(id, completed)

        val dbSqlite = AppDatabase.getDatabase(context)
        val calendarDao = dbSqlite.calendarEntryDao()
        val scheduler = NotificationScheduler(context)

        // Update status di dalam agregator kalender tanpa menghapusnya
        calendarDao.updateStatusBySource("TASK", id, completed)

        // Matikan alarm jika tugas ditandai selesai
        if (completed) {
            val entriesToCancel = calendarDao.getEntriesForSource("TASK", id)
            entriesToCancel.forEach { scheduler.cancelNotification(it.notificationId) }
        }

        scheduleSync()
    }

    suspend fun getTaskByIdSuspend(id: Long): Tasks? {
        return tasksDao.getTaskById(id)
    }

    suspend fun getLatestTask(): Tasks? {
        return tasksDao.getLatestTask()
    }
}