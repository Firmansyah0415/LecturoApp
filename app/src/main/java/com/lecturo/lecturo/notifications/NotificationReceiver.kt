package com.lecturo.lecturo.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.lecturo.lecturo.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 1. PENTING: Panggil goAsync() agar proses tidak dibunuh saat akses Database
        val pendingResult = goAsync()

        val id = intent.getLongExtra("ID", -1)
        val type = intent.getStringExtra("TYPE") ?: "UNKNOWN" // "CALENDAR_ENTRY" atau "CONSULTATION"

        Log.d("NotificationReceiver", "Alarm DITERIMA! ID: $id, Tipe: $type")

        if (id == -1L) {
            pendingResult.finish()
            return
        }

        val database = AppDatabase.getDatabase(context)
        val notificationHelper = NotificationHelper(context)

        // Scope IO untuk database
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (type == "CONSULTATION") {
                    // --- HANDLE JADWAL KONSULTASI ---
                    val consultation = database.consultationDao().getScheduleById(id)
                    if (consultation != null) {
                        // Cek apakah status masih SCHEDULED (jangan notif kalau cancel)
                        if (consultation.status == "SCHEDULED") {
                            notificationHelper.showNotification(
                                notificationId = id.toInt(), // Gunakan ID database sebagai Notif ID
                                title = "Pengingat Konsultasi",
                                message = "${consultation.title} di ${consultation.location} pukul ${consultation.startTime}",
                                category = "Konsultasi"
                            )
                        }
                    }
                } else {
                    // --- HANDLE JADWAL LAMA (CalendarEntry) ---
                    val entry = database.calendarEntryDao().getEntryById(id)
                    if (entry != null) {
                        notificationHelper.showNotification(
                            notificationId = entry.notificationId,
                            title = entry.title,
                            message = buildOldMessage(entry.category, entry.time),
                            category = entry.category
                        )
                        // Logika repeat entry lama biarkan saja di sini jika masih dipakai
                    }
                }
            } catch (e: Exception) {
                Log.e("NotificationReceiver", "Error showing notification", e)
            } finally {
                // 2. WAJIB: Akhiri pendingResult setelah selesai
                pendingResult.finish()
            }
        }
    }

    private fun buildOldMessage(category: String, time: String): String {
        return "$category dijadwalkan pada $time"
    }
}