package com.lecturo.lecturo.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.preference.PreferenceManager
import com.lecturo.lecturo.data.model.CalendarEntry
import com.lecturo.lecturo.data.model.ConsultationSchedule
import java.text.SimpleDateFormat
import java.util.*

class NotificationScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    // =========================================================================
    // BAGIAN 1: FITUR BARU (KONSULTASI)
    // =========================================================================

    fun scheduleConsultation(schedule: ConsultationSchedule) {
        // 1. Cek Global Setting
        if (!sharedPreferences.getBoolean("global_notification_enabled", true)) return

        // 2. Hitung Waktu Alarm
        val alarmTime = calculateAlarmTime(schedule.date, schedule.startTime, schedule.notificationMinutesBefore)
        if (alarmTime <= System.currentTimeMillis()) return // Sudah lewat

        // 3. Siapkan Intent
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("ID", schedule.id)
            putExtra("TYPE", "CONSULTATION")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            schedule.id.toInt(), // ID Request Code pakai ID Database (Long ke Int)
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setAlarm(alarmTime, pendingIntent)
        Log.d("Scheduler", "Consultation Scheduled: ${schedule.title} at $alarmTime")
    }

    fun cancelConsultation(scheduleId: Long) {
        val intent = Intent(context, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            scheduleId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    // =========================================================================
    // BAGIAN 2: FITUR LAMA (EVENT, TASK, TEACHING) - CalendarEntry
    // =========================================================================

    fun scheduleNotification(entry: CalendarEntry) {
        if (!sharedPreferences.getBoolean("global_notification_enabled", true)) {
            // Jika global mati, pastikan alarm dibatalkan
            cancelNotification(entry.notificationId)
            return
        }

        if (entry.notificationMinutesBefore < 0) return

        // Logika hitung waktu entry lama
        val timeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val alarmTime = try {
            val date = timeFormat.parse("${entry.date} ${entry.time}")
            val triggerTime = date?.time ?: return
            triggerTime - (entry.notificationMinutesBefore * 60 * 1000L)
        } catch (e: Exception) {
            0L
        }

        if (alarmTime <= System.currentTimeMillis()) return

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("ID", entry.id) // ID Entry lama
            putExtra("TYPE", "CALENDAR_ENTRY")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            entry.notificationId, // Menggunakan notificationId (Int) dari CalendarEntry
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setAlarm(alarmTime, pendingIntent)
    }

    // --- FUNGSI INI YANG HILANG SEBELUMNYA ---
    // Dipakai oleh EventViewModel, TasksViewModel, TeachingViewModel
    fun cancelNotification(notificationId: Int) {
        val intent = Intent(context, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d("NotificationScheduler", "Notifikasi Lama ID $notificationId dibatalkan.")
    }

    // =========================================================================
    // BAGIAN 3: HELPER
    // =========================================================================

    private fun calculateAlarmTime(dateStr: String, timeStr: String, minutesBefore: Int): Long {
        // Format Consultasi: yyyy-MM-dd dan HH:mm
        // Jika format date di DB kamu dd/MM/yyyy, sesuaikan format di bawah ini
        // Asumsi format di Entity baru: yyyy-MM-dd (sesuai DateHelper yg kita buat)
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return try {
            val date = format.parse("$dateStr $timeStr")
            val triggerTime = date?.time ?: return 0L
            triggerTime - (minutesBefore * 60 * 1000L)
        } catch (e: Exception) {
            e.printStackTrace()
            0L
        }
    }

    private fun setAlarm(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        try {
            // [JURUS RAHASIA: setAlarmClock]
            // Fungsi ini menjamin notifikasi akan mendobrak Doze Mode dan OEM Restrictions (seperti MIUI)
            // Ini adalah metode yang sama persis digunakan oleh Google Calendar & Loop Habit Tracker
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, pendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)

        } catch (e: SecurityException) {
            Log.e("Scheduler", "Security Exception: ${e.message}")
        }
    }
}