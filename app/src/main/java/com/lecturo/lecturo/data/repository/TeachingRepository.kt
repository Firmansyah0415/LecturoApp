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
import com.lecturo.lecturo.data.db.dao.TeachingRuleDao
import com.lecturo.lecturo.data.model.CalendarEntry
import com.lecturo.lecturo.data.model.TeachingRule
import com.lecturo.lecturo.notifications.NotificationScheduler
import com.lecturo.lecturo.workers.SyncTeachingWorker
import java.text.SimpleDateFormat
import java.util.*

class TeachingRepository(
    private val teachingRuleDao: TeachingRuleDao,
    private val calendarEntryDao: CalendarEntryDao,
    private val context: Context
) {

    fun getAllRules(): LiveData<List<TeachingRule>> {
        return teachingRuleDao.getAllRules()
    }

    suspend fun syncTeachingFromCloud() {
        try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val db = FirebaseFirestore.getInstance()

            val dbSqlite = AppDatabase.getDatabase(context)
            val calendarDao = dbSqlite.calendarEntryDao()
            val scheduler = NotificationScheduler(context)

            val snapshot = db.collection("users").document(uid).collection("teaching_schedules")
                .get(Source.SERVER).await()

            for (document in snapshot.documents) {
                try {
                    val firestoreId = document.id
                    val courseName = document.getString("course_name") ?: continue
                    val classCode = document.getString("class_code") ?: "-"
                    val classroom = document.getString("classroom") ?: "-"
                    val dayOfWeek = document.getString("day_of_week") ?: "-"
                    val startTime = document.getString("start_time") ?: continue
                    val endTime = document.getString("end_time") ?: continue
                    val studentCount = document.getLong("student_count")?.toInt() ?: 0
                    val startDate = document.getString("start_date") ?: ""
                    val notificationMinutes = document.getLong("notification_minutes")?.toInt() ?: 15

                    // Kita juga ambil repetition data dari Cloud (Penting untuk generate)
                    val repetitionType = document.getString("repetition_type") ?: "COUNT"
                    val repetitionValue = document.getString("repetition_value") ?: "1"

                    val existing = teachingRuleDao.getRuleByFirestoreId(firestoreId)
                    if (existing != null) {
                        if (existing.isSynced) {
                            val updated = existing.copy(
                                userId = uid,
                                courseName = courseName,
                                classCode = classCode,
                                classroom = classroom,
                                dayOfWeek = dayOfWeek,
                                startTime = startTime,
                                endTime = endTime,
                                studentCount = studentCount,
                                startDate = startDate,
                                repetitionType = repetitionType,
                                repetitionValue = repetitionValue,
                                notificationMinutes = notificationMinutes,
                                isSynced = true,
                                isDeleted = false
                            )
                            updated.firestoreId = firestoreId
                            teachingRuleDao.updateRuleRaw(updated)

                            val oldEntries = calendarDao.getEntriesForSource("TEACHING_RULE", existing.localId)
                            oldEntries.forEach { scheduler.cancelNotification(it.notificationId) }
                            calendarDao.deleteEntriesForSource("TEACHING_RULE", existing.localId)

                            // --- MENGGUNAKAN MESIN PENCETAK PERULANGAN ---
                            generateAndScheduleEntriesFromCloud(updated.copy(localId = existing.localId), scheduler, calendarDao)
                        }
                    } else {
                        val newRule = TeachingRule(
                            userId = uid,
                            courseName = courseName,
                            classCode = classCode,
                            classroom = classroom,
                            dayOfWeek = dayOfWeek,
                            startTime = startTime,
                            endTime = endTime,
                            studentCount = studentCount,
                            startDate = startDate,
                            repetitionType = repetitionType,
                            repetitionValue = repetitionValue,
                            notificationMinutes = notificationMinutes,
                            firestoreId = firestoreId,
                            isSynced = true,
                            isDeleted = false
                        )
                        val newId = teachingRuleDao.insertRuleRaw(newRule)

                        // --- MENGGUNAKAN MESIN PENCETAK PERULANGAN ---
                        generateAndScheduleEntriesFromCloud(newRule.copy(localId = newId), scheduler, calendarDao)
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        } catch (e: Exception) { }
    }

    // --- PERBAIKAN: Fungsi simpan sekarang otomatis mencetak jadwal ---
    suspend fun insertOrUpdateRule(rule: TeachingRule): Long {
        rule.isSynced = false
        rule.isDeleted = false

        val localId = teachingRuleDao.insertOrUpdateRule(rule)

        // Bersihkan jadwal & alarm lama jika ini proses Update/Edit
        val scheduler = NotificationScheduler(context)
        val oldEntries = calendarEntryDao.getEntriesForSource("TEACHING_RULE", localId)
        oldEntries.forEach { scheduler.cancelNotification(it.notificationId) }
        calendarEntryDao.deleteEntriesForSource("TEACHING_RULE", localId)

        // Cetak jadwal baru (Gunakan mesin yang sama dengan Cloud Sync)
        generateAndScheduleEntriesFromCloud(rule.copy(localId = localId), scheduler, calendarEntryDao)

        scheduleSync()
        return localId
    }

    // --- PERBAIKAN: Fungsi hapus juga otomatis bersihkan kalender ---
    suspend fun deleteRuleById(ruleId: Long) {
        // 1. Bersihkan Notifikasi & Kalender
        val scheduler = NotificationScheduler(context)
        val entries = calendarEntryDao.getEntriesForSource("TEACHING_RULE", ruleId)
        entries.forEach { scheduler.cancelNotification(it.notificationId) }
        calendarEntryDao.deleteEntriesForSource("TEACHING_RULE", ruleId)

        // 2. Soft Delete
        teachingRuleDao.softDeleteRule(ruleId)
        scheduleSync()
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
            ExistingWorkPolicy.KEEP,
            syncRequest
        )
    }

    suspend fun getRuleById(ruleId: Long): TeachingRule? {
        return teachingRuleDao.getRuleById(ruleId)
    }

    fun getRulesByDay(dayOfWeek: String): LiveData<List<TeachingRule>> {
        return teachingRuleDao.getRulesByDay(dayOfWeek)
    }

    suspend fun insertCalendarEntry(entry: CalendarEntry): Long {
        return calendarEntryDao.insertEntry(entry)
    }

    suspend fun insertCalendarEntries(entries: List<CalendarEntry>) {
        calendarEntryDao.insertEntries(entries)
    }

    suspend fun deleteCalendarEntriesForSource(type: String, id: Long) {
        calendarEntryDao.deleteEntriesForSource(type, id)
    }

    suspend fun getCalendarEntriesForSource(type: String, id: Long): List<CalendarEntry> {
        return calendarEntryDao.getEntriesForSource(type, id)
    }

    // ====================================================================================
    // MESIN PENCETAK JADWAL PERULANGAN (KHUSUS UNTUK SYNC CLOUD)
    // ====================================================================================
    private suspend fun generateAndScheduleEntriesFromCloud(
        rule: TeachingRule,
        scheduler: NotificationScheduler,
        calendarDao: CalendarEntryDao
    ) {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val startDate = try { dateFormat.parse(rule.startDate) } catch (e: Exception) { null } ?: return

        val calendar = Calendar.getInstance()
        calendar.time = startDate

        val dayMap = mapOf(
            "Senin" to Calendar.MONDAY, "Selasa" to Calendar.TUESDAY, "Rabu" to Calendar.WEDNESDAY,
            "Kamis" to Calendar.THURSDAY, "Jumat" to Calendar.FRIDAY, "Sabtu" to Calendar.SATURDAY,
            "Minggu" to Calendar.SUNDAY
        )
        val targetDayOfWeek = dayMap[rule.dayOfWeek] ?: return

        val processAndSchedule: suspend (Date) -> Unit = { date ->
            val tempEntry = CalendarEntry(
                title = "${rule.courseName} - ${rule.classCode}",
                date = dateFormat.format(date),
                time = rule.startTime,
                category = "Mengajar",
                priority = rule.priority ?: "Tinggi",
                sourceFeatureType = "TEACHING_RULE",
                sourceFeatureId = rule.localId,
                notificationMinutesBefore = rule.notificationMinutes
            )
            val newId = calendarDao.insertEntry(tempEntry)
            val finalEntry = tempEntry.copy(id = newId)
            if (finalEntry.notificationMinutesBefore >= 0) {
                scheduler.scheduleNotification(finalEntry)
            }
        }

        if (rule.repetitionType == "DATE") {
            val endDate = try { dateFormat.parse(rule.repetitionValue ?: "") } catch (e: Exception) { null } ?: return
            while (calendar.time <= endDate) {
                if (calendar.get(Calendar.DAY_OF_WEEK) == targetDayOfWeek) {
                    processAndSchedule(calendar.time)
                }
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }
        } else if (rule.repetitionType == "COUNT") {
            val targetMeetings = rule.repetitionValue?.toIntOrNull() ?: return
            if (targetMeetings <= 0) return
            var meetingCount = 0
            val maxCalendar = Calendar.getInstance().apply { time = startDate; add(Calendar.YEAR, 1) }

            while (meetingCount < targetMeetings && calendar.time <= maxCalendar.time) {
                if (calendar.get(Calendar.DAY_OF_WEEK) == targetDayOfWeek) {
                    processAndSchedule(calendar.time)
                    meetingCount++
                }
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }
        } else {
            // Fallback: Jika suatu saat data rusak, minimal cetak 1 jadwal
            processAndSchedule(calendar.time)
        }
    }
}