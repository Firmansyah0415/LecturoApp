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

    fun saveNewTeachingRule(rule: TeachingRule, notificationValue: Int) {
        viewModelScope.launch {
            try {
                // 1. Simpan rule ke tabel teaching_rules
                val ruleId = repository.insertOrUpdateRule(rule)
                val updatedRule = rule.copy(id = ruleId)

                // 2. Hapus calendar entries lama jika edit mode
                if (rule.id != 0L) {
                    repository.deleteCalendarEntriesForSource("TEACHING_RULE", rule.id)
                }

                // 3. Generate calendar entries untuk setiap pertemuan kelas
                generateCalendarEntries(updatedRule, notificationValue)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- FUNGSI YANG DIPERBAIKI SECARA SIGNIFIKAN ---
    private suspend fun generateCalendarEntries(rule: TeachingRule, notificationMinutes: Int) {
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
        val calendarEntries = mutableListOf<CalendarEntry>()

        // PERBAIKAN: Logika sekarang memeriksa tipe pengulangan
        if (rule.repetitionType == "DATE") {
            // Logika untuk pengulangan berdasarkan rentang tanggal
            val endDate = dateFormat.parse(rule.repetitionValue) ?: return
            while (calendar.time <= endDate) {
                if (calendar.get(Calendar.DAY_OF_WEEK) == targetDayOfWeek) {
                    val entry = createCalendarEntry(rule, calendar.time, notificationMinutes)
                    calendarEntries.add(entry)
                }
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }
        } else if (rule.repetitionType == "COUNT") {
            // Logika untuk pengulangan berdasarkan jumlah pertemuan
            val targetMeetings = rule.repetitionValue.toIntOrNull() ?: return
            if (targetMeetings <= 0) return
            var meetingCount = 0

            // Pengaman untuk mencegah loop tak terbatas, berhenti setelah 1 tahun
            val maxCalendar = Calendar.getInstance().apply { time = startDate; add(Calendar.YEAR, 1) }

            while (meetingCount < targetMeetings && calendar.time <= maxCalendar.time) {
                if (calendar.get(Calendar.DAY_OF_WEEK) == targetDayOfWeek) {
                    val entry = createCalendarEntry(rule, calendar.time, notificationMinutes)
                    calendarEntries.add(entry)
                    meetingCount++
                }
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        // Simpan semua calendar entries sekaligus
        if (calendarEntries.isNotEmpty()) {
            repository.insertCalendarEntries(calendarEntries)

            // Jadwalkan notifikasi untuk setiap entry
            val notificationScheduler = NotificationScheduler(getApplication())
            calendarEntries.forEach { entry ->
                if (entry.notificationMinutesBefore >= 0) {
                    notificationScheduler.scheduleNotification(entry)
                }
            }
        }
    }

    // Fungsi bantuan untuk menghindari duplikasi kode
    private fun createCalendarEntry(rule: TeachingRule, date: Date, notificationMinutes: Int): CalendarEntry {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return CalendarEntry(
            title = "${rule.courseName} - ${rule.className}",
            date = dateFormat.format(date),
            time = rule.startTime,
            category = "Mengajar",
            sourceFeatureType = "TEACHING_RULE",
            sourceFeatureId = rule.id,
            notificationMinutesBefore = notificationMinutes,
            notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt() + Random().nextInt()
        )
    }

    fun deleteTeachingRule(ruleId: Long) {
        viewModelScope.launch {
            repository.deleteCalendarEntriesForSource("TEACHING_RULE", ruleId)
            repository.deleteRuleById(ruleId)
        }
    }

    suspend fun getTeachingRuleById(ruleId: Long): TeachingRule? {
        return repository.getRuleById(ruleId)
    }
}
