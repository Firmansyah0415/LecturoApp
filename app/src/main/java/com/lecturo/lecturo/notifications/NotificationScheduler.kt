package com.lecturo.lecturo.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.lecturo.lecturo.data.model.CalendarEntry
import java.text.SimpleDateFormat
import java.util.*

class NotificationScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleNotification(entry: CalendarEntry) {
        if (entry.notificationMinutesBefore < 0) return // Notifikasi dinonaktifkan

        val notificationTime = calculateNotificationTime(entry.date, entry.time, entry.notificationMinutesBefore)
        if (notificationTime <= System.currentTimeMillis()) return // Waktu sudah lewat

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("entry_id", entry.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            entry.notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // --- TAMBAHKAN LOG DI SINI ---
        val readableTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(notificationTime))
        Log.d("NotificationScheduler", "Menjadwalkan notifikasi untuk ID: ${entry.notificationId} pada: $readableTime")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    notificationTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    notificationTime,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            // Handle permission denied for exact alarms
            e.printStackTrace()
        }
    }

    fun cancelNotification(notificationId: Int) {
        val intent = Intent(context, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
    }

    private fun calculateNotificationTime(date: String, time: String, minutesBefore: Int): Long {
        val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateTimeString = "$date $time"

        return try {
            val eventTime = dateTimeFormat.parse(dateTimeString)?.time ?: 0L
            eventTime - (minutesBefore * 60 * 1000L) // Konversi menit ke milliseconds
        } catch (e: Exception) {
            0L
        }
    }
}
