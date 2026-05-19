package com.lecturo.lecturo.data.repository

import com.google.firebase.firestore.Source
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.db.dao.ConsultationDao
import com.lecturo.lecturo.data.model.CalendarEntry
import com.lecturo.lecturo.data.model.ConsultationPattern
import com.lecturo.lecturo.data.model.ConsultationSchedule
import com.lecturo.lecturo.notifications.NotificationScheduler
import com.lecturo.lecturo.workers.SyncConsultationWorker
import java.text.SimpleDateFormat
import java.util.Locale

class ConsultationRepository(
    private val consultationDao: ConsultationDao,
    private val context: Context
) {

    private fun mapPriorityToIndo(priority: String?): String {
        return when (priority?.lowercase()) {
            "high" -> "Tinggi"
            "medium" -> "Sedang"
            "low" -> "Rendah"
            else -> "Sedang"
        }
    }

    // --- [TRIK DALANG] Fungsi Helper untuk menerjemahkan String Status ke Boolean ---
    private fun isStatusCompletedOrCancelled(status: String): Boolean {
        return status == "COMPLETED" || status == "CANCELLED"
    }

    suspend fun syncConsultationsFromCloud() {
        try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val db = FirebaseFirestore.getInstance()

            val dbSqlite = AppDatabase.getDatabase(context)
            val calendarDao = dbSqlite.calendarEntryDao()
            val scheduler = NotificationScheduler(context)

            val snapshot = db.collection("users").document(uid).collection("consultations")
                .get(Source.SERVER).await()

            val cloudFirestoreIds = mutableListOf<String>()

            for (document in snapshot.documents) {
                try {
                    val firestoreId = document.id
                    cloudFirestoreIds.add(firestoreId)
                    val title = document.getString("title") ?: continue
                    val date = document.getString("date") ?: continue
                    val startTime = document.getString("start_time") ?: continue
                    val endTime = document.getString("end_time") ?: continue
                    val location = document.getString("location") ?: ""
                    val description = document.getString("description") ?: ""
                    val priority = document.getString("priority") ?: "Medium"
                    val status = document.getString("status") ?: "SCHEDULED"
                    val notificationMinutes = document.getLong("notification_minutes")?.toInt() ?: 15

                    val existing = consultationDao.getScheduleByFirestoreId(firestoreId)

                    if (existing != null) {
                        if (existing.isSynced) {
                            val updated = existing.copy(
                                userId = uid,
                                title = title,
                                date = date,
                                startTime = startTime,
                                endTime = endTime,
                                location = location,
                                description = description,
                                priority = priority,
                                status = status,
                                notificationMinutesBefore = notificationMinutes,
                                isSynced = true,
                                isDeleted = false
                            )
                            updated.firestoreId = firestoreId
                            consultationDao.updateScheduleRaw(updated)

                            // 1. Bersihkan alarm & entri lama
                            scheduler.cancelConsultation(existing.id)
                            calendarDao.deleteEntriesForSource("CONSULTATION", existing.id)

                            // 2. 🔴 [PERBAIKAN OPSI B] Selalu masukkan ke Kalender sebagai riwayat
                            val entry = CalendarEntry(
                                title = updated.title,
                                date = updated.date,
                                time = updated.startTime,
                                endTime = updated.endTime,
                                category = "Konsultasi",
                                priority = mapPriorityToIndo(updated.priority),
                                sourceFeatureType = "CONSULTATION",
                                sourceFeatureId = existing.id,
                                notificationMinutesBefore = updated.notificationMinutesBefore,
                                isCompleted = isStatusCompletedOrCancelled(updated.status) // <--- Trik Dalang
                            )
                            calendarDao.insertEntry(entry)

                            // 3. Alarm HANYA bunyi jika masih SCHEDULED
                            if (updated.status == "SCHEDULED") {
                                scheduler.scheduleConsultation(updated)
                            }
                        }
                    } else {
                        val newSchedule = ConsultationSchedule(
                            userId = uid,
                            title = title,
                            date = date,
                            startTime = startTime,
                            endTime = endTime,
                            location = location,
                            description = description,
                            priority = priority,
                            status = status,
                            notificationMinutesBefore = notificationMinutes,
                            firestoreId = firestoreId,
                            isSynced = true,
                            isDeleted = false,
                            inputSource = "WEB_UPLOAD"
                        )
                        val newId = consultationDao.insertScheduleRaw(newSchedule)
                        val finalSchedule = newSchedule.copy(id = newId)

                        // 🔴 [PERBAIKAN OPSI B]
                        val entry = CalendarEntry(
                            title = finalSchedule.title,
                            date = finalSchedule.date,
                            time = finalSchedule.startTime,
                            endTime = finalSchedule.endTime,
                            category = "Konsultasi",
                            priority = mapPriorityToIndo(finalSchedule.priority),
                            sourceFeatureType = "CONSULTATION",
                            sourceFeatureId = newId,
                            notificationMinutesBefore = finalSchedule.notificationMinutesBefore,
                            isCompleted = isStatusCompletedOrCancelled(finalSchedule.status)
                        )
                        calendarDao.insertEntry(entry)

                        if (finalSchedule.status == "SCHEDULED") {
                            scheduler.scheduleConsultation(finalSchedule)
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }

            // PEMBASMI HANTU
            val localSyncedSchedules = consultationDao.getSyncedSchedulesList()
            for (localSchedule in localSyncedSchedules) {
                if (!cloudFirestoreIds.contains(localSchedule.firestoreId)) {
                    scheduler.cancelConsultation(localSchedule.id)
                    calendarDao.deleteEntriesForSource("CONSULTATION", localSchedule.id)
                    consultationDao.hardDeleteSchedule(localSchedule.id)
                }
            }

        } catch (e: Exception) { }
    }

    suspend fun insertSchedule(baseSchedule: ConsultationSchedule, repeatMode: String, repeatValue: String) {
        val dbSqlite = AppDatabase.getDatabase(context)
        val calendarDao = dbSqlite.calendarEntryDao()
        val scheduler = NotificationScheduler(context)

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val startDate = try { dateFormat.parse(baseSchedule.date) } catch (e: Exception) { null } ?: return

        val calendar = java.util.Calendar.getInstance()
        calendar.time = startDate

        var currentIteration = 0
        var maxIterations = 1
        var isDateLimited = false
        var endDateLimit: java.util.Date? = null

        if (repeatMode == "COUNT") {
            maxIterations = repeatValue.toIntOrNull() ?: 1
        } else if (repeatMode == "DATE") {
            maxIterations = 52
            isDateLimited = true
            endDateLimit = try { dateFormat.parse(repeatValue) } catch (e: Exception) { null }
        }

        while (currentIteration < maxIterations) {

            if (isDateLimited && endDateLimit != null && calendar.time.after(endDateLimit)) {
                break
            }

            val currentDateStr = dateFormat.format(calendar.time)
            val newSchedule = baseSchedule.copy(
                id = 0,
                date = currentDateStr,
                isSynced = false,
                isDeleted = false
            )

            val insertedId = consultationDao.insertSchedule(newSchedule)

            // 🔴 [PERBAIKAN OPSI B]
            val entry = CalendarEntry(
                title = newSchedule.title,
                date = newSchedule.date,
                time = newSchedule.startTime,
                endTime = newSchedule.endTime,
                category = "Konsultasi",
                priority = mapPriorityToIndo(newSchedule.priority),
                sourceFeatureType = "CONSULTATION",
                sourceFeatureId = insertedId,
                notificationMinutesBefore = newSchedule.notificationMinutesBefore,
                isCompleted = isStatusCompletedOrCancelled(newSchedule.status)
            )
            calendarDao.insertEntry(entry)

            if (newSchedule.status == "SCHEDULED") {
                scheduler.scheduleConsultation(newSchedule.copy(id = insertedId))
            }

            calendar.add(java.util.Calendar.DAY_OF_MONTH, 7)
            currentIteration++
        }

        scheduleSync()
    }

    suspend fun updateSchedule(schedule: ConsultationSchedule) {
        schedule.isSynced = false
        consultationDao.updateSchedule(schedule)

        val dbSqlite = AppDatabase.getDatabase(context)
        val calendarDao = dbSqlite.calendarEntryDao()
        val scheduler = NotificationScheduler(context)

        // 1. Bersihkan entri kalender dan alarm lama dulu
        scheduler.cancelConsultation(schedule.id)
        calendarDao.deleteEntriesForSource("CONSULTATION", schedule.id)

        // 2. 🔴 [PERBAIKAN OPSI B] Masukkan entri baru ke kalender agar menjadi riwayat
        if (!schedule.isDeleted) {
            val entry = CalendarEntry(
                title = schedule.title,
                date = schedule.date,
                time = schedule.startTime,
                endTime = schedule.endTime,
                category = "Konsultasi",
                priority = mapPriorityToIndo(schedule.priority),
                sourceFeatureType = "CONSULTATION",
                sourceFeatureId = schedule.id,
                notificationMinutesBefore = schedule.notificationMinutesBefore,
                isCompleted = isStatusCompletedOrCancelled(schedule.status) // Dalang bekerja!
            )
            calendarDao.insertEntry(entry)

            // 3. Alarm hanya dibunyikan jika status SCHEDULED
            if (schedule.status == "SCHEDULED") {
                scheduler.scheduleConsultation(schedule)
            }
        }

        scheduleSync()
    }

    suspend fun deleteSchedule(schedule: ConsultationSchedule) {
        val dbSqlite = AppDatabase.getDatabase(context)
        val calendarDao = dbSqlite.calendarEntryDao()
        val scheduler = NotificationScheduler(context)

        // Jika dihapus oleh user, baru kita bersihkan dari Kalender Beranda sepenuhnya
        scheduler.cancelConsultation(schedule.id)
        calendarDao.deleteEntriesForSource("CONSULTATION", schedule.id)

        consultationDao.softDeleteSchedule(schedule.id)
        scheduleSync()
    }

    // ================= PATTERNS =================

    suspend fun insertPattern(pattern: ConsultationPattern) {
        pattern.isSynced = false
        pattern.isDeleted = false
        consultationDao.insertPattern(pattern)
        scheduleSync()
    }

    suspend fun updatePattern(pattern: ConsultationPattern) {
        pattern.isSynced = false
        consultationDao.updatePattern(pattern)
        scheduleSync()
    }

    suspend fun deletePattern(pattern: ConsultationPattern) {
        consultationDao.softDeletePattern(pattern.id)
        scheduleSync()
    }

    private fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncConsultationWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "SyncConsultationWork",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

    suspend fun getScheduleById(id: Long) = consultationDao.getScheduleById(id)
    fun getAllSchedules() = consultationDao.getAllSchedules()
    fun getUpcomingSchedules(date: String) = consultationDao.getUpcomingSchedules(date)
    fun getHistorySchedules(date: String) = consultationDao.getHistorySchedules(date)
    fun getSchedulesByDate(date: String) = consultationDao.getSchedulesByDate(date)
    fun searchSchedules(query: String) = consultationDao.searchSchedules(query)
    fun getActivePatterns() = consultationDao.getActivePatterns()
    fun getAllPatterns() = consultationDao.getAllPatterns()
}