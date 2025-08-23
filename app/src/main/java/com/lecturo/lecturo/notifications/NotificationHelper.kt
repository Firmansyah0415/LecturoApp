package com.lecturo.lecturo.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import com.lecturo.lecturo.ui.main.MainActivity
import com.lecturo.lecturo.R

class NotificationHelper(private val context: Context) {

    // DIUBAH: Tambahkan SharedPreferences untuk membaca pengaturan
    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    companion object {
        // DIUBAH: Kita sekarang punya 4 ID channel untuk setiap skenario
        const val CHANNEL_ID_ALARM_VIBRATE = "lecturo_alarm_vibrate"
        const val CHANNEL_ID_ALARM_ONLY = "lecturo_alarm_only"
        const val CHANNEL_ID_DEFAULT_VIBRATE = "lecturo_default_vibrate"
        const val CHANNEL_ID_DEFAULT_ONLY = "lecturo_default_only"
    }

    init {
        // Panggil fungsi untuk membuat kedua channel
        createNotificationChannels()
    }

    // Contoh untuk suara custom
    //val soundUri = Uri.parse("android.resource://${context.packageName}/${R.raw.mysound}") // jika ingin menambahkan sound custom di raw/nama_audio.mp3

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // --- Channel 1: Suara Alarm + Getar (Skenario 3) ---
            val alarmSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val alarmAudioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()
            val alarmVibrateChannel = NotificationChannel(
                CHANNEL_ID_ALARM_VIBRATE,
                "Jadwal (Alarm & Getar)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi dengan suara alarm dan getaran"
                setSound(alarmSoundUri, alarmAudioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            notificationManager.createNotificationChannel(alarmVibrateChannel)

            // --- Channel 2: Suara Alarm Saja (Skenario 2) ---
            val alarmOnlyChannel = NotificationChannel(
                CHANNEL_ID_ALARM_ONLY,
                "Jadwal (Hanya Alarm)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi dengan suara alarm tanpa getaran"
                setSound(alarmSoundUri, alarmAudioAttributes)
                enableVibration(false) // Getar dinonaktifkan di level channel
            }
            notificationManager.createNotificationChannel(alarmOnlyChannel)

            // --- Channel 3: Suara Notifikasi Default + Getar (Skenario 4) ---
            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val defaultAudioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
            val defaultVibrateChannel = NotificationChannel(
                CHANNEL_ID_DEFAULT_VIBRATE,
                "Jadwal (Suara & Getar Default)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi dengan suara default dan getaran"
                setSound(defaultSoundUri, defaultAudioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            notificationManager.createNotificationChannel(defaultVibrateChannel)

            // --- Channel 4: Suara Notifikasi Default Saja (Skenario 1) ---
            val defaultOnlyChannel = NotificationChannel(
                CHANNEL_ID_DEFAULT_ONLY,
                "Jadwal (Suara Default)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi dengan suara default tanpa getaran"
                setSound(defaultSoundUri, defaultAudioAttributes)
                enableVibration(false) // Getar dinonaktifkan di level channel
            }
            notificationManager.createNotificationChannel(defaultOnlyChannel)
        }
    }

    fun showNotification(
        notificationId: Int,
        title: String,
        message: String,
        category: String
    ) {
        // --- PERBAIKAN UTAMA DI SINI ---

        // 1. Baca pengaturan dari SharedPreferences
        val isAlarmSoundEnabled = sharedPreferences.getBoolean("notification_sound_enabled", false)
        val isVibrationEnabled = sharedPreferences.getBoolean("notification_vibration_enabled", true)

        // 2. Pilih ID channel yang tepat berdasarkan pengaturan
        val selectedChannelId = when {
            isAlarmSoundEnabled && isVibrationEnabled -> CHANNEL_ID_ALARM_VIBRATE     // Skenario 3
            isAlarmSoundEnabled && !isVibrationEnabled -> CHANNEL_ID_ALARM_ONLY       // Skenario 2
            !isAlarmSoundEnabled && isVibrationEnabled -> CHANNEL_ID_DEFAULT_VIBRATE  // Skenario 4
            else -> CHANNEL_ID_DEFAULT_ONLY                                           // Skenario 1
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val icon = when (category) {
            "Mengajar" -> R.drawable.ic_class
            "Tugas" -> R.drawable.ic_task
            "Konsultasi" -> R.drawable.ic_consultant
            else -> R.drawable.ic_event
        }

        // 3. Buat builder notifikasi dengan channel yang sudah dipilih
        // PENTING: Hapus semua panggilan .setSound() dan .setVibrate() dari sini
        val builder = NotificationCompat.Builder(context, selectedChannelId)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun cancelNotification(notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }
}
