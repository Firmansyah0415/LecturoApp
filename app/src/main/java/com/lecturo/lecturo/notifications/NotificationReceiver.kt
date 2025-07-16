package com.lecturo.lecturo.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.lecturo.lecturo.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val entryId = intent.getLongExtra("entry_id", -1)

        // --- TAMBAHKAN LOG DI SINI ---
        Log.d("NotificationReceiver", "Alarm DITERIMA! ID Entri: $entryId")

        if (entryId == -1L) {
            Log.w("NotificationReceiver", "ID Entri tidak valid, proses dihentikan.")
            return
        }

        val database = AppDatabase.getDatabase(context)
        val notificationHelper = NotificationHelper(context)
        val notificationScheduler = NotificationScheduler(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entry = database.calendarEntryDao().getEntryById(entryId)
                if (entry != null) {
                    // Tampilkan notifikasi
                    val message = buildNotificationMessage(entry.category, entry.time, entry.date)
                    notificationHelper.showNotification(
                        entry.notificationId,
                        entry.title,
                        message,
                        entry.category
                    )

                    // Jadwalkan ulang jika berulang
                    if (entry.isRepeating && !entry.repeatInterval.isNullOrEmpty()) {
                        val nextEntry = calculateNextRepeatingEntry(entry)
                        if (nextEntry != null) {
                            // Update entry dengan tanggal baru
                            database.calendarEntryDao().updateEntry(nextEntry)
                            // Jadwalkan alarm berikutnya
                            notificationScheduler.scheduleNotification(nextEntry)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun buildNotificationMessage(category: String, time: String, date: String): String {
        return when (category) {
            "Mengajar" -> "Kelas dimulai pada $time"
            "Rapat" -> "Rapat dijadwalkan pada $time"
            "Tugas" -> "Deadline tugas pada $time"
            "Konsultasi" -> "Sesi konsultasi pada $time"
            else -> "Jadwal pada $time"
        }
    }

    private fun calculateNextRepeatingEntry(entry: com.lecturo.lecturo.data.model.CalendarEntry): com.lecturo.lecturo.data.model.CalendarEntry? {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val currentDate = dateFormat.parse(entry.date) ?: return null

        val calendar = Calendar.getInstance()
        calendar.time = currentDate

        when (entry.repeatInterval) {
            "DAILY" -> calendar.add(Calendar.DAY_OF_MONTH, 1)
            "WEEKLY" -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
            else -> return null
        }

        return entry.copy(
            id = 0, // Buat entry baru
            date = dateFormat.format(calendar.time),
            notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        )
    }
}
