package com.lecturo.lecturo.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lecturo.lecturo.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RebootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val database = AppDatabase.getDatabase(context)
            val notificationScheduler = NotificationScheduler(context)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Ambil semua calendar entries yang memiliki notifikasi aktif
                    val entries = database.calendarEntryDao().getActiveNotificationEntries()

                    // Jadwalkan ulang semua notifikasi
                    entries.forEach { entry ->
                        if (entry.notificationMinutesBefore >= 0) {
                            notificationScheduler.scheduleNotification(entry)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
