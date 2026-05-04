package com.lecturo.lecturo.data.repository

import com.google.firebase.firestore.Source
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.db.AppDatabase.Companion.getDatabase
import com.lecturo.lecturo.data.db.dao.EventDao
import com.lecturo.lecturo.data.model.CalendarEntry
import com.lecturo.lecturo.data.model.Event
import com.lecturo.lecturo.notifications.NotificationScheduler
import com.lecturo.lecturo.workers.SyncEventWorker

class EventRepository(
    private val eventDao: EventDao,
    private val context: Context
) {

    fun getAllEvents(): LiveData<List<Event>> {
        return eventDao.getAllEvents()
    }

    suspend fun syncEventsFromCloud() {
        try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val db = FirebaseFirestore.getInstance()

            val dbSqlite = AppDatabase.getDatabase(context)
            val calendarDao = dbSqlite.calendarEntryDao()
            val scheduler = NotificationScheduler(context)

            val snapshot = db.collection("users").document(uid).collection("events")
                .get(Source.SERVER).await()

            for (document in snapshot.documents) {
                try {
                    val firestoreId = document.id
                    val title = document.getString("title") ?: continue
                    val category = document.getString("category") ?: "Lainnya"
                    val date = document.getString("date") ?: continue
                    val time = document.getString("time") ?: continue
                    val endTime = document.getString("end_time") ?: ""
                    val location = document.getString("location") ?: ""
                    val description = document.getString("description") ?: ""
                    val priority = document.getString("priority") ?: "Sedang"
                    val isCompleted = document.getBoolean("is_completed") ?: false
                    val notificationMinutes = document.getLong("notification_minutes")?.toInt() ?: 15

                    val existing = eventDao.getEventByFirestoreId(firestoreId)

                    if (existing != null) {
                        if (existing.isSynced) {
                            val updated = existing.copy(
                                userId = uid,
                                title = title,
                                category = category,
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
                            updated.firestoreId = firestoreId
                            eventDao.updateEventRaw(updated)

                            val oldEntries = calendarDao.getEntriesForSource("EVENT", existing.id)
                            oldEntries.forEach { scheduler.cancelNotification(it.notificationId) }
                            calendarDao.deleteEntriesForSource("EVENT", existing.id)

                            if (!updated.isCompleted) {
                                val entry = CalendarEntry(
                                    title = updated.title,
                                    date = updated.date,
                                    time = updated.time,
                                    endTime = updated.endTime,
                                    category = updated.category,
                                    priority = updated.priority ?: "Sedang",
                                    sourceFeatureType = "EVENT",
                                    sourceFeatureId = existing.id,
                                    notificationMinutesBefore = updated.notificationMinutesBefore
                                )
                                val entryId = calendarDao.insertEntry(entry)
                                scheduler.scheduleNotification(entry.copy(id = entryId))
                            }
                        }
                    } else {
                        val newEvent = Event(
                            userId = uid,
                            title = title,
                            category = category,
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
                        val newId = eventDao.insertEventRaw(newEvent)

                        if (!newEvent.isCompleted) {
                            val entry = CalendarEntry(
                                title = newEvent.title,
                                date = newEvent.date,
                                time = newEvent.time,
                                endTime = newEvent.endTime,
                                category = newEvent.category,
                                priority = newEvent.priority ?: "Sedang",
                                sourceFeatureType = "EVENT",
                                sourceFeatureId = newId,
                                notificationMinutesBefore = newEvent.notificationMinutesBefore
                            )
                            val entryId = calendarDao.insertEntry(entry)
                            scheduler.scheduleNotification(entry.copy(id = entryId))
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        } catch (e: Exception) { }
    }

    suspend fun insertOrUpdate(event: Event): Long {
        event.isSynced = false
        event.isDeleted = false

        val localId = eventDao.insertOrUpdate(event)

        // [BASMI HANTU UPDATE & RE-SCHEDULE ALARM]
        val dbSqlite = getDatabase(context)
        val calendarDao = dbSqlite.calendarEntryDao()
        val scheduler = NotificationScheduler(context)

        // Hapus entri lama
        val oldEntries = calendarDao.getEntriesForSource("EVENT", localId)
        oldEntries.forEach { scheduler.cancelNotification(it.notificationId) }
        calendarDao.deleteEntriesForSource("EVENT", localId)

        // Buat entri baru jika belum selesai
        if (!event.isCompleted) {
            val entry = CalendarEntry(
                title = event.title,
                date = event.date,
                time = event.time,
                endTime = event.endTime,
                category = event.category,
                priority = event.priority ?: "Sedang",
                sourceFeatureType = "EVENT",
                sourceFeatureId = localId,
                notificationMinutesBefore = event.notificationMinutesBefore
            )
            val entryId = calendarDao.insertEntry(entry)
            scheduler.scheduleNotification(entry.copy(id = entryId))
        }

        scheduleSync()
        return localId
    }

    // --- LOGIC BARU: SOFT DELETE & CLEANUP CALENDAR ---
    suspend fun deleteById(eventId: Long) {
        // 1. Hapus Notifikasi & Kalender Lama dulu
        val dbSqlite = getDatabase(context)
        val calendarDao = dbSqlite.calendarEntryDao()
        val scheduler = NotificationScheduler(context)

        val entriesToDelete = calendarDao.getEntriesForSource("EVENT", eventId)
        entriesToDelete.forEach { scheduler.cancelNotification(it.notificationId) }
        calendarDao.deleteEntriesForSource("EVENT", eventId)

        // 2. Soft Delete Lokal
        eventDao.softDelete(eventId)

        // 3. Trigger Worker
        scheduleSync()
    }

    // --- LOGIC BARU: UNIQUE WORKER ---
    private fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncEventWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "SyncEventWork", // Nama Unik
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

    // --- HELPER LAINNYA ---
    suspend fun updateCompletedStatus(eventId: Long, isCompleted: Boolean) {
        eventDao.updateCompletedStatus(eventId, isCompleted)

        // Matikan alarm jika event diselesaikan
        if (isCompleted) {
            val dbSqlite = getDatabase(context)
            val calendarDao = dbSqlite.calendarEntryDao()
            val scheduler = NotificationScheduler(context)
            val entriesToCancel = calendarDao.getEntriesForSource("EVENT", eventId)
            entriesToCancel.forEach { scheduler.cancelNotification(it.notificationId) }
        }
        scheduleSync()
    }

    suspend fun getEventById(eventId: Long): Event? {
        return eventDao.getEventById(eventId)
    }

    fun getEventsByCategory(category: String): LiveData<List<Event>> {
        return eventDao.getEventsByCategory(category)
    }

    fun getAllCategories(): LiveData<List<String>> {
        return eventDao.getAllCategories()
    }
}