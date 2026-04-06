package com.lecturo.lecturo.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lecturo.lecturo.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RebootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {

            // Inisialisasi Database & Scheduler
            val database = AppDatabase.getDatabase(context)
            val scheduler = NotificationScheduler(context)

            // Wajib pakai goAsync agar BroadcastReceiver tidak dimatikan Android saat codingan coroutine jalan
            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // ---------------------------------------------------
                    // 1. Reschedule Calendar Entries (Jadwal Umum)
                    // ---------------------------------------------------
                    val oldEntries = database.calendarEntryDao().getActiveNotificationEntries()
                    oldEntries.forEach { entry ->
                        scheduler.scheduleNotification(entry)
                    }

                    // ---------------------------------------------------
                    // 2. Reschedule Consultation Schedules (Jadwal Konsultasi)
                    // ---------------------------------------------------

                    // [PERBAIKAN] Gunakan fungsi baru 'getActiveSchedulesForReboot'
                    val activeConsultations = database.consultationDao().getActiveSchedulesForReboot()

                    // Loop dan jadwalkan ulang alarmnya
                    activeConsultations.forEach { schedule ->
                        scheduler.scheduleConsultation(schedule)
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    // Memberitahu Android bahwa proses background sudah selesai
                    pendingResult.finish()
                }
            }
        }
    }
}