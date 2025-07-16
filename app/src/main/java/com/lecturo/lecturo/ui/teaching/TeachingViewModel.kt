package com.lecturo.lecturo.ui.teaching

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.lecturo.lecturo.data.model.TeachingRule
import com.lecturo.lecturo.data.repository.TeachingRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.lecturo.lecturo.data.model.CalendarEntry
import com.lecturo.lecturo.notifications.NotificationScheduler

class TeachingViewModel(private val repository: TeachingRepository, application: Application) : AndroidViewModel(application) {

    val teachingRules: LiveData<List<TeachingRule>> = repository.getAllRules()

    // PERBAIKAN: Fungsi ini sekarang hanya menerima satu parameter: objek TeachingRule yang sudah lengkap
    fun saveNewTeachingRule(rule: TeachingRule) = viewModelScope.launch {
        try {
            // 1. Batalkan semua alarm lama yang terkait dengan aturan ini (penting untuk mode edit)
            if (rule.id != 0L) {
                val scheduler = NotificationScheduler(getApplication())
                val oldEntries = repository.getCalendarEntriesForSource("TEACHING_RULE", rule.id)
                oldEntries.forEach { oldEntry ->
                    scheduler.cancelNotification(oldEntry.notificationId)
                }
                // Hapus entri kalender lama dari database
                repository.deleteCalendarEntriesForSource("TEACHING_RULE", rule.id)
            }

            // 2. Simpan aturan ke tabel teaching_rules dan dapatkan ID-nya
            val ruleId = repository.insertOrUpdateRule(rule)
            val updatedRule = rule.copy(id = ruleId)

            // 3. Generate dan jadwalkan entri kalender baru satu per satu
            generateAndScheduleEntries(updatedRule)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun generateAndScheduleEntries(rule: TeachingRule) {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val startDate = dateFormat.parse(rule.semesterStartDate) ?: return

        val calendar = Calendar.getInstance()
        calendar.time = startDate

        val dayMap = mapOf(
            "Senin" to Calendar.MONDAY, "Selasa" to Calendar.TUESDAY, "Rabu" to Calendar.WEDNESDAY,
            "Kamis" to Calendar.THURSDAY, "Jumat" to Calendar.FRIDAY, "Sabtu" to Calendar.SATURDAY,
            "Minggu" to Calendar.SUNDAY
        )

        val targetDayOfWeek = dayMap[rule.dayOfWeek] ?: return
        val scheduler = NotificationScheduler(getApplication())

        // Fungsi bantuan untuk memproses setiap tanggal yang valid
        val processAndSchedule: suspend (Date) -> Unit = { date ->
            // Buat objek CalendarEntry sementara
            val tempEntry = createCalendarEntry(rule, date)

            // Simpan ke DB untuk mendapatkan ID asli
            val newId = repository.insertCalendarEntry(tempEntry)

            // Buat objek final dengan ID yang benar
            val finalEntry = tempEntry.copy(id = newId)

            // Jadwalkan notifikasi dengan objek final
            if (finalEntry.notificationMinutesBefore >= 0) {
                scheduler.scheduleNotification(finalEntry)
            }
        }

        if (rule.repetitionType == "DATE") {
            val endDate = dateFormat.parse(rule.repetitionValue) ?: return
            while (calendar.time <= endDate) {
                if (calendar.get(Calendar.DAY_OF_WEEK) == targetDayOfWeek) {
                    processAndSchedule(calendar.time)
                }
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }
        } else if (rule.repetitionType == "COUNT") {
            val targetMeetings = rule.repetitionValue.toIntOrNull() ?: return
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
        }
    }

    private fun createCalendarEntry(rule: TeachingRule, date: Date): CalendarEntry {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return CalendarEntry(
            title = "${rule.courseName} - ${rule.className}",
            date = dateFormat.format(date),
            time = rule.startTime,
            category = "Mengajar",
            sourceFeatureType = "TEACHING_RULE",
            sourceFeatureId = rule.id,
            notificationMinutesBefore = rule.notificationMinutesBefore
        )
    }

    fun deleteTeachingRule(ruleId: Long) = viewModelScope.launch {
        val scheduler = NotificationScheduler(getApplication())
        val entriesToDelete = repository.getCalendarEntriesForSource("TEACHING_RULE", ruleId)
        entriesToDelete.forEach { entry ->
            scheduler.cancelNotification(entry.notificationId)
        }
        repository.deleteRuleById(ruleId)
        repository.deleteCalendarEntriesForSource("TEACHING_RULE", ruleId)
    }

    suspend fun getTeachingRuleById(ruleId: Long): TeachingRule? {
        return repository.getRuleById(ruleId)
    }
}
