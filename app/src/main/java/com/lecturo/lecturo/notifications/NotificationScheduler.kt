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
import java.text.SimpleDateFormat
import java.util.*

class NotificationScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    // DIUBAH: Tambahkan referensi ke SharedPreferences
    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    fun scheduleNotification(entry: CalendarEntry) {
        // --- PERBAIKAN UTAMA DI SINI ---
        // Pertama, cek apakah notifikasi global diaktifkan.
        val isGlobalNotificationEnabled = sharedPreferences.getBoolean("global_notification_enabled", true)
        if (!isGlobalNotificationEnabled) {
            Log.d("NotificationScheduler", "PENJADWALAN DIBATALKAN: Notifikasi global dinonaktifkan.")
            // Jika tidak aktif, batalkan notifikasi yang mungkin sudah ada dan hentikan proses.
            cancelNotification(entry.notificationId)
            return
        }
        // --- AKHIR DARI PERBAIKAN ---

        if (entry.notificationMinutesBefore < 0) {
            Log.d("NotificationScheduler", "Notifikasi untuk ID ${entry.notificationId} dinonaktifkan.")
            return
        }

        val notificationTime = calculateNotificationTime(entry.date, entry.time, entry.notificationMinutesBefore)
        val currentTime = System.currentTimeMillis()

        val readableNotifTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(notificationTime))
        Log.d("NotificationScheduler", "Mencoba Menjadwalkan ID: ${entry.notificationId} pada $readableNotifTime")

        if (notificationTime <= currentTime) {
            Log.w("NotificationScheduler", "PENJADWALAN DIBATALKAN: Waktu alarm sudah lewat.")
            return
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                Log.w("NotificationScheduler", "Izin untuk alarm presisi tidak diberikan.")
                // Di sini Anda bisa fallback ke alarm yang tidak presisi atau meminta izin ke user.
                // Untuk saat ini, kita tetap coba set.
            }

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                notificationTime,
                pendingIntent
            )
            Log.d("NotificationScheduler", "SUKSES: Alarm untuk ID ${entry.notificationId} berhasil disetel.")
        } catch (e: Exception) {
            Log.e("NotificationScheduler", "Gagal menjadwalkan: ${e.message}", e)
        }
    }

    // Sisa kode di bawah ini tidak berubah...
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
            eventTime - (minutesBefore * 60 * 1000L)
        } catch (e: Exception) {
            0L
        }
    }
}