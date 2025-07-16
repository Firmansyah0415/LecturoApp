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
        if (entry.notificationMinutesBefore < 0) {
            Log.d("NotificationScheduler", "Notifikasi untuk ID ${entry.notificationId} dinonaktifkan.")
            return
        }

        val notificationTime = calculateNotificationTime(entry.date, entry.time, entry.notificationMinutesBefore)
        val currentTime = System.currentTimeMillis()

        // --- LOG BARU UNTUK INVESTIGASI ---
        val readableNotifTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS", Locale.getDefault()).format(Date(notificationTime))
        val readableCurrentTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS", Locale.getDefault()).format(Date(currentTime))
        Log.d("NotificationScheduler", "-----------------------------------------")
        Log.d("NotificationScheduler", "Mencoba Menjadwalkan untuk ID: ${entry.notificationId}")
        Log.d("NotificationScheduler", "Waktu Alarm Dihitung   : $readableNotifTime")
        Log.d("NotificationScheduler", "Waktu Sistem Saat Ini  : $readableCurrentTime")
        // ------------------------------------

        if (notificationTime <= currentTime) {
            // --- LOG PENTING JIKA GAGAL ---
            Log.w("NotificationScheduler", "PENJADWALAN DIBATALKAN: Waktu alarm sudah lewat.")
            return // Waktu sudah lewat, jangan jadwalkan
        }

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("entry_id", entry.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            entry.notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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
            // --- LOG PENTING JIKA SUKSES ---
            Log.d("NotificationScheduler", "SUKSES: Alarm untuk ID ${entry.notificationId} berhasil disetel.")
        } catch (e: SecurityException) {
            Log.e("NotificationScheduler", "Gagal menjadwalkan: Izin ditolak", e)
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
        Log.d("NotificationScheduler", "Notifikasi dengan ID $notificationId dibatalkan.")
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
