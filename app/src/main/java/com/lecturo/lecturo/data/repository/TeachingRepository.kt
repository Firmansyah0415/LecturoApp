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
import com.lecturo.lecturo.data.db.dao.CalendarEntryDao
import com.lecturo.lecturo.data.db.dao.TeachingScheduleDao
import com.lecturo.lecturo.data.model.CalendarEntry
import com.lecturo.lecturo.data.model.TeachingSchedule
import com.lecturo.lecturo.notifications.NotificationScheduler
import com.lecturo.lecturo.workers.SyncTeachingWorker
import java.text.SimpleDateFormat
import java.util.*

class TeachingRepository(
    private val teachingScheduleDao: TeachingScheduleDao,
    private val calendarEntryDao: CalendarEntryDao,
    private val context: Context
) {

    fun getAllSchedules(): LiveData<List<TeachingSchedule>> {
        return teachingScheduleDao.getAllSchedules()
    }

    suspend fun getScheduleById(scheduleId: Long): TeachingSchedule? {
        return teachingScheduleDao.getScheduleById(scheduleId)
    }

    fun getSchedulesByDay(dayOfWeek: String): LiveData<List<TeachingSchedule>> {
        return teachingScheduleDao.getSchedulesByDay(dayOfWeek)
    }

    // ====================================================================================
    // 1. SINKRONISASI CLOUD -> LOKAL (Mendukung Status Riwayat di Kalender)
    // ====================================================================================
    suspend fun syncTeachingFromCloud() {
        try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val db = FirebaseFirestore.getInstance()

            val dbSqlite = AppDatabase.getDatabase(context)
            val calendarDao = dbSqlite.calendarEntryDao()
            val scheduler = NotificationScheduler(context)

            val snapshot = db.collection("users").document(uid).collection("teaching_schedules")
                .get(Source.SERVER).await()

            val cloudFirestoreIds = mutableListOf<String>()

            for (document in snapshot.documents) {
                try {
                    val firestoreId = document.id
                    cloudFirestoreIds.add(firestoreId)
                    val courseName = document.getString("course_name") ?: continue
                    val classCode = document.getString("class_code") ?: "-"
                    val classroom = document.getString("classroom") ?: "-"
                    val dayOfWeek = document.getString("day_of_week") ?: "-"
                    val date = document.getString("date") ?: continue
                    val startTime = document.getString("start_time") ?: continue
                    val endTime = document.getString("end_time") ?: continue
                    val studentCount = document.getLong("student_count")?.toInt() ?: 0
                    val meetingNumber = document.getLong("meeting_number")?.toInt() ?: 1
                    val isCompleted = document.getBoolean("is_completed") ?: false
                    val notificationMinutes = document.getLong("notification_minutes")?.toInt() ?: 15

                    val existing = teachingScheduleDao.getScheduleByFirestoreId(firestoreId)
                    if (existing != null) {
                        if (existing.isSynced) {
                            val updated = existing.copy(
                                userId = uid,
                                courseName = courseName,
                                classCode = classCode,
                                classroom = classroom,
                                dayOfWeek = dayOfWeek,
                                date = date,
                                startTime = startTime,
                                endTime = endTime,
                                studentCount = studentCount,
                                meetingNumber = meetingNumber,
                                isCompleted = isCompleted,
                                notificationMinutes = notificationMinutes,
                                isSynced = true,
                                isDeleted = false
                            )
                            updated.firestoreId = firestoreId
                            teachingScheduleDao.updateScheduleRaw(updated)

                            // Bersihkan entri alarm lama agar tidak duplikat
                            val oldEntries = calendarDao.getEntriesForSource("TEACHING_SCHEDULE", existing.localId)
                            oldEntries.forEach { scheduler.cancelNotification(it.notificationId) }
                            calendarDao.deleteEntriesForSource("TEACHING_SCHEDULE", existing.localId)

                            // Masukkan kembali ke kalender dengan membawa status isCompleted terbaru
                            insertToCalendarAndAlarm(updated, existing.localId, calendarDao, scheduler)
                        }
                    } else {
                        val newSchedule = TeachingSchedule(
                            userId = uid,
                            courseName = courseName,
                            classCode = classCode,
                            classroom = classroom,
                            dayOfWeek = dayOfWeek,
                            date = date,
                            startTime = startTime,
                            endTime = endTime,
                            studentCount = studentCount,
                            meetingNumber = meetingNumber,
                            isCompleted = isCompleted,
                            notificationMinutes = notificationMinutes,
                            firestoreId = firestoreId,
                            isSynced = true,
                            isDeleted = false
                        )
                        val newId = teachingScheduleDao.insertScheduleRaw(newSchedule)

                        insertToCalendarAndAlarm(newSchedule, newId, calendarDao, scheduler)
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }

            // PEMBASMI DATA HANTU (Jika data di cloud sudah dihapus oleh user)
            val localSyncedSchedules = teachingScheduleDao.getSyncedSchedulesList()
            for (localSchedule in localSyncedSchedules) {
                if (!cloudFirestoreIds.contains(localSchedule.firestoreId)) {
                    val oldEntries = calendarDao.getEntriesForSource("TEACHING_SCHEDULE", localSchedule.localId)
                    oldEntries.forEach { scheduler.cancelNotification(it.notificationId) }
                    calendarDao.deleteEntriesForSource("TEACHING_SCHEDULE", localSchedule.localId)
                    teachingScheduleDao.deleteSchedulePermanently(localSchedule.localId)
                }
            }

        } catch (e: Exception) { }
    }

    // ====================================================================================
    // 2. MESIN GENERATE JADWAL PERULANGAN LOKAL (Saat Tambah Jadwal Baru)
    // ====================================================================================
    suspend fun insertTeachingSchedules(baseSchedule: TeachingSchedule, repeatMode: String, repeatValue: String) {
        val dbSqlite = AppDatabase.getDatabase(context)
        val calendarDao = dbSqlite.calendarEntryDao()
        val scheduler = NotificationScheduler(context)

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val startDate = try { dateFormat.parse(baseSchedule.date) } catch (e: Exception) { null } ?: return

        val calendar = Calendar.getInstance()
        calendar.time = startDate

        var currentIteration = 0
        var maxIterations = 1
        var isDateLimited = false
        var endDateLimit: Date? = null

        if (repeatMode == "COUNT") {
            maxIterations = repeatValue.toIntOrNull() ?: 1
        } else if (repeatMode == "DATE") {
            maxIterations = 52 // Sabuk Pengaman: Maksimal 1 Tahun (52 pertemuan)
            isDateLimited = true
            endDateLimit = try { dateFormat.parse(repeatValue) } catch (e: Exception) { null }
        }

        while (currentIteration < maxIterations) {
            if (isDateLimited && endDateLimit != null && calendar.time.after(endDateLimit)) {
                break
            }

            val currentDateStr = dateFormat.format(calendar.time)

            val newSchedule = baseSchedule.copy(
                localId = 0,
                date = currentDateStr,
                meetingNumber = currentIteration + 1, // Penomoran P1, P2, P3 secara fisik
                isSynced = false,
                isDeleted = false,
                isCompleted = false
            )

            val insertedId = teachingScheduleDao.insertOrUpdateSchedule(newSchedule)
            insertToCalendarAndAlarm(newSchedule, insertedId, calendarDao, scheduler)

            calendar.add(Calendar.DAY_OF_MONTH, 7) // Loncat tiap minggu
            currentIteration++
        }

        scheduleSync()
    }

    // ====================================================================================
    // 3. UPDATE SINGLE SCHEDULE (Mendukung Perubahan Status & Pembaruan Notifikasi)
    // ====================================================================================
    suspend fun updateSingleSchedule(schedule: TeachingSchedule) {
        schedule.isSynced = false
        teachingScheduleDao.insertOrUpdateSchedule(schedule)

        val dbSqlite = AppDatabase.getDatabase(context)
        val calendarDao = dbSqlite.calendarEntryDao()
        val scheduler = NotificationScheduler(context)

        // 1. Batalkan alarm lama agar tidak terjadi penumpukan trigger di sistem
        val oldEntries = calendarDao.getEntriesForSource("TEACHING_SCHEDULE", schedule.localId)
        oldEntries.forEach { scheduler.cancelNotification(it.notificationId) }

        // 2. Hapus entri kalender lama untuk menghindari redundansi teks lama
        calendarDao.deleteEntriesForSource("TEACHING_SCHEDULE", schedule.localId)

        // 3. Masukkan entri baru ke kalender jika statusnya tidak dihapus (soft delete)
        if (!schedule.isDeleted) {
            insertToCalendarAndAlarm(schedule, schedule.localId, calendarDao, scheduler)
        }

        scheduleSync()
    }

    // ====================================================================================
    // 4. SOFT DELETE JADWAL
    // ====================================================================================
    suspend fun deleteScheduleById(scheduleId: Long) {
        val scheduler = NotificationScheduler(context)
        val entries = calendarEntryDao.getEntriesForSource("TEACHING_SCHEDULE", scheduleId)
        entries.forEach { scheduler.cancelNotification(it.notificationId) }
        calendarEntryDao.deleteEntriesForSource("TEACHING_SCHEDULE", scheduleId)

        teachingScheduleDao.softDeleteSchedule(scheduleId)
        scheduleSync()
    }

    // ====================================================================================
    // HELPER: MEMASUKKAN DATA KE AGREGATOR KALENDER & ALARM SYSTEM
    // ====================================================================================
    private suspend fun insertToCalendarAndAlarm(
        schedule: TeachingSchedule,
        id: Long,
        calendarDao: CalendarEntryDao,
        scheduler: NotificationScheduler
    ) {
        val entry = CalendarEntry(
            title = "${schedule.courseName} - ${schedule.classCode}",
            date = schedule.date,
            time = schedule.startTime,
            endTime = schedule.endTime,
            category = "Mengajar",
            priority = schedule.priority ?: "Tinggi",
            sourceFeatureType = "TEACHING_SCHEDULE",
            sourceFeatureId = id,
            notificationMinutesBefore = schedule.notificationMinutes,
            isCompleted = schedule.isCompleted // 🔴 [BEST PRACTICE] Meneruskan status riwayat langsung ke kalender
        )
        val newId = calendarDao.insertEntry(entry)
        val finalEntry = entry.copy(id = newId)

        // 🔴 [LOGIKA AMAN] Notifikasi hanya akan dijadwalkan jika kelas BELUM SELESAI
        if (finalEntry.notificationMinutesBefore >= 0 && !schedule.isCompleted) {
            scheduler.scheduleNotification(finalEntry)
        }
    }

    private fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncTeachingWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "SyncTeachingWork",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }
}