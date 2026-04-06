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

class ConsultationRepository(
    private val consultationDao: ConsultationDao,
    private val context: Context
) {

    suspend fun syncConsultationsFromCloud() {
        try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val db = FirebaseFirestore.getInstance()

            val dbSqlite = AppDatabase.getDatabase(context)
            val calendarDao = dbSqlite.calendarEntryDao()
            val scheduler = NotificationScheduler(context)

            val snapshot = db.collection("users").document(uid).collection("consultations")
                .get(Source.SERVER).await()

            for (document in snapshot.documents) {
                try {
                    val firestoreId = document.id
                    val title = document.getString("title") ?: continue
                    val date = document.getString("date") ?: continue
                    val startTime = document.getString("start_time") ?: continue
                    val endTime = document.getString("end_time") ?: continue
                    val location = document.getString("location") ?: ""
                    val description = document.getString("description") ?: ""
                    val priority = document.getString("priority") ?: "Medium"
                    val status = document.getString("status") ?: "SCHEDULED"
                    val notificationMinutes = document.getLong("notification_minutes")?.toInt() ?: 15

                    // Konversi format tanggal YYYY-MM-DD dari Web ke DD/MM/YYYY untuk HomeFragment
                    val dateParts = date.split("-")
                    val dateForCalendar = if (dateParts.size == 3) "${dateParts[2]}/${dateParts[1]}/${dateParts[0]}" else date

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

                            // Bersihkan Alarm dan Kalender Lama
                            scheduler.cancelConsultation(existing.id)
                            calendarDao.deleteEntriesForSource("CONSULTATION", existing.id)

                            // Masukkan ke Kalender (HomeFragment) & Nyalakan Alarm Baru
                            if (updated.status != "CANCELLED") {
                                val entry = CalendarEntry(
                                    title = updated.title,
                                    date = dateForCalendar,
                                    time = updated.startTime,
                                    category = "Konsultasi",
                                    priority = updated.priority ?: "Sedang",
                                    sourceFeatureType = "CONSULTATION",
                                    sourceFeatureId = existing.id,
                                    notificationMinutesBefore = updated.notificationMinutesBefore
                                )
                                calendarDao.insertEntry(entry)

                                // Alarm konsultasi dipanggil menggunakan fungsinya sendiri
                                if (updated.status == "SCHEDULED") scheduler.scheduleConsultation(updated)
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

                        // Masukkan ke Kalender (HomeFragment) & Nyalakan Alarm Baru
                        if (finalSchedule.status != "CANCELLED") {
                            val entry = CalendarEntry(
                                title = finalSchedule.title,
                                date = dateForCalendar,
                                time = finalSchedule.startTime,
                                category = "Konsultasi",
                                priority = finalSchedule.priority ?: "Sedang",
                                sourceFeatureType = "CONSULTATION",
                                sourceFeatureId = newId,
                                notificationMinutesBefore = finalSchedule.notificationMinutesBefore
                            )
                            calendarDao.insertEntry(entry)

                            // Alarm konsultasi dipanggil menggunakan fungsinya sendiri
                            if (finalSchedule.status == "SCHEDULED") scheduler.scheduleConsultation(finalSchedule)
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        } catch (e: Exception) { }
    }

    // ================= SCHEDULES =================

    suspend fun insertSchedule(schedule: ConsultationSchedule): Long {
        schedule.isSynced = false
        schedule.isDeleted = false
        val id = consultationDao.insertSchedule(schedule)

        // Masukkan ke Kalender & Alarm
        val dbSqlite = AppDatabase.getDatabase(context)
        val calendarDao = dbSqlite.calendarEntryDao()
        val scheduler = NotificationScheduler(context)

        val dateParts = schedule.date.split("-")
        val dateForCalendar = if (dateParts.size == 3) "${dateParts[2]}/${dateParts[1]}/${dateParts[0]}" else schedule.date

        if (schedule.status != "CANCELLED") {
            val entry = CalendarEntry(
                title = schedule.title, date = dateForCalendar, time = schedule.startTime,
                category = "Konsultasi", priority = schedule.priority ?: "Sedang",
                sourceFeatureType = "CONSULTATION", sourceFeatureId = id,
                notificationMinutesBefore = schedule.notificationMinutesBefore
            )
            calendarDao.insertEntry(entry)
            if (schedule.status == "SCHEDULED") scheduler.scheduleConsultation(schedule.copy(id = id))
        }

        scheduleSync()
        return id
    }

    suspend fun updateSchedule(schedule: ConsultationSchedule) {
        schedule.isSynced = false
        consultationDao.updateSchedule(schedule)

        // [BASMI HANTU UPDATE]
        val dbSqlite = AppDatabase.getDatabase(context)
        val calendarDao = dbSqlite.calendarEntryDao()
        val scheduler = NotificationScheduler(context)

        // Hapus yang lama
        scheduler.cancelConsultation(schedule.id)
        calendarDao.deleteEntriesForSource("CONSULTATION", schedule.id)

        // Masukkan yang baru
        val dateParts = schedule.date.split("-")
        val dateForCalendar = if (dateParts.size == 3) "${dateParts[2]}/${dateParts[1]}/${dateParts[0]}" else schedule.date

        if (schedule.status != "CANCELLED") {
            val entry = CalendarEntry(
                title = schedule.title, date = dateForCalendar, time = schedule.startTime,
                category = "Konsultasi", priority = schedule.priority ?: "Sedang",
                sourceFeatureType = "CONSULTATION", sourceFeatureId = schedule.id,
                notificationMinutesBefore = schedule.notificationMinutesBefore
            )
            calendarDao.insertEntry(entry)
            if (schedule.status == "SCHEDULED") scheduler.scheduleConsultation(schedule)
        }

        scheduleSync()
    }

    suspend fun deleteSchedule(schedule: ConsultationSchedule) {
        // Soft Delete via DAO
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

    // ================= WORKER TRIGGER =================

    private fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncConsultationWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "SyncConsultationWork",
            ExistingWorkPolicy.KEEP,
            syncRequest
        )
    }

    // ================= READERS (Pass Through) =================
    // Tidak berubah, hanya meneruskan dari DAO
    suspend fun getScheduleById(id: Long) = consultationDao.getScheduleById(id)
    fun getUpcomingSchedules(date: String) = consultationDao.getUpcomingSchedules(date)
    fun getHistorySchedules(date: String) = consultationDao.getHistorySchedules(date)
    fun getSchedulesByDate(date: String) = consultationDao.getSchedulesByDate(date)
    fun searchSchedules(query: String) = consultationDao.searchSchedules(query)
    fun getActivePatterns() = consultationDao.getActivePatterns()
    fun getAllPatterns() = consultationDao.getAllPatterns()
}