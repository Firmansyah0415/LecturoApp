package com.lecturo.lecturo.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.lecturo.lecturo.R
import com.lecturo.lecturo.ui.focus.FocusActivity
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import com.lecturo.lecturo.utils.FocusPreferences

class TimerService : Service() {

    companion object {
        // ID Actions
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_STOP = "ACTION_STOP"

        // ID Broadcast (Data yang dikirim ke UI)
        const val ACTION_TICK = "ACTION_TICK"
        const val ACTION_TIMER_FINISHED = "ACTION_TIMER_FINISHED"

        // Extras untuk Timer
        const val EXTRA_TIME_LEFT = "EXTRA_TIME_LEFT"
        const val EXTRA_DURATION = "EXTRA_DURATION"
        const val EXTRA_TIMER_DURATION_INPUT = "EXTRA_TIMER_DURATION_INPUT"

        // [TAMBAHAN BARU] Extras untuk Notifikasi & Intent Balik
        const val EXTRA_TASK_ID = "EXTRA_TASK_ID"
        const val EXTRA_TASK_TITLE = "EXTRA_TASK_TITLE"
        const val EXTRA_TASK_FIRESTORE_ID = "EXTRA_TASK_FIRESTORE_ID"
        const val EXTRA_SESSION_LABEL = "EXTRA_SESSION_LABEL"

        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "lecturo_timer_channel"
    }

    private var timer: CountDownTimer? = null
    private var timeLeftInMillis: Long = 0L
    private var initialDurationInMillis: Long = 0L
    private var isTimerRunning = false

    // Variabel penyimpan data tugas
    private var currentTaskId: Long = -1L
    private var currentTaskTitle: String = "Tanpa Judul"
    private var currentTaskFirestoreId: String? = null
    private var currentSessionLabel: String = "Fokus"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // Ambil durasi
                val durationInput = intent.getLongExtra(EXTRA_TIMER_DURATION_INPUT, 0L)
                if (durationInput > 0) {
                    timeLeftInMillis = durationInput
                    initialDurationInMillis = durationInput
                }

                // [TAMBAHAN BARU] Ambil data tugas dari intent
                currentTaskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
                currentTaskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "Tanpa Judul"
                currentTaskFirestoreId = intent.getStringExtra(EXTRA_TASK_FIRESTORE_ID)
                currentSessionLabel = intent.getStringExtra(EXTRA_SESSION_LABEL) ?: "Fokus"

                startTimer()
            }
            ACTION_PAUSE -> {
                pauseTimer()
            }
            ACTION_STOP -> {
                stopTimerService()
            }
        }
        return START_NOT_STICKY
    }

    private var targetEndTime: Long = 0L // Tambahkan variabel ini di atas

    private fun startTimer() {
        if (isTimerRunning) return

        // [PERBAIKAN 1]: Catat Waktu Berakhir di Dunia Nyata
        targetEndTime = System.currentTimeMillis() + timeLeftInMillis

        val notification = createNotification(
            title = "$currentSessionLabel: $currentTaskTitle",
            content = "Menghitung mundur..."
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } catch (e: Exception) {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        isTimerRunning = true

        timer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // [PERBAIKAN 2]: Hitung sisa waktu berdasarkan Jam Dunia Nyata (Kebal Doze Mode)
                val realTimeLeft = targetEndTime - System.currentTimeMillis()

                if (realTimeLeft <= 0) {
                    // Waktu sudah habis, tapi CountDownTimer telat sadar! Paksa Selesai!
                    this.cancel()
                    onFinish()
                } else {
                    timeLeftInMillis = realTimeLeft
                    val timeString = getFormattedTime(realTimeLeft)
                    updateNotification(
                        title = "$currentSessionLabel: $currentTaskTitle",
                        content = "Sisa Waktu: $timeString"
                    )
                    sendBroadcastUpdate(realTimeLeft)
                }
            }

            override fun onFinish() {
                timeLeftInMillis = 0
                isTimerRunning = false

                val prefs = FocusPreferences(this@TimerService)
                prefs.setTimerState("FINISHED")

                playAlarmAndNotification()

                val finishIntent = Intent(ACTION_TIMER_FINISHED)
                finishIntent.setPackage(packageName)
                sendBroadcast(finishIntent)

                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }.start()
    }

    private fun pauseTimer() {
        timer?.cancel()
        isTimerRunning = false
        val timeString = getFormattedTime(timeLeftInMillis)
        val notification = createNotification(
            title = "Jeda - $currentTaskTitle",
            content = "Sisa Waktu: $timeString (Tap untuk lanjut)"
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun stopTimerService() {
        timer?.cancel()
        isTimerRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun sendBroadcastUpdate(millisUntilFinished: Long) {
        val intent = Intent(ACTION_TICK)
        intent.putExtra(EXTRA_TIME_LEFT, millisUntilFinished)
        intent.putExtra(EXTRA_DURATION, initialDurationInMillis)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    // --- LOGIKA NOTIFIKASI ---

    private fun createNotification(title: String, content: String): android.app.Notification {
        createNotificationChannel()

        // [PERBAIKAN FATAL] Intent untuk membuka FocusActivity
        val intent = Intent(this, FocusActivity::class.java).apply {
            // SINGLE_TOP memastikan tidak membuka jendela baru jika FocusActivity sudah terbuka di background
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP

            // Masukkan "Karcis Masuk" agar FocusActivity tahu tugas apa yang harus ditampilkan
            putExtra("TASK_ID", currentTaskId)
            putExtra("TASK_TITLE", currentTaskTitle)
            putExtra("TASK_FIRESTORE_ID", currentTaskFirestoreId)
        }

        // Gunakan FLAG_UPDATE_CURRENT agar data intent (Karcis Masuk) yang lama diperbarui dengan yang baru
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title) // Misal: "FOKUS #1: Periksa Jurnal"
            .setContentText(content) // Misal: "Sisa Waktu: 24:59"
            .setSmallIcon(R.drawable.ic_timer)
            .setContentIntent(pendingIntent) // Pasang karcis masuk ke Notifikasi
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        val notification = createNotification(title, content)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Lecturo Timer Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun getFormattedTime(millis: Long): String {
        val minutes = (millis / 1000) / 60
        val seconds = (millis / 1000) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    // --- [TAMBAHKAN FUNGSI INI DI BAGIAN BAWAH KELAS TimerService] ---
    private fun playAlarmAndNotification() {
        val prefs = FocusPreferences(this)

        if (prefs.isVibrationEnabled()) {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
        }

        if (prefs.isSoundEnabled()) {
            try {
                val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                if (notification != null) {
                    val r = RingtoneManager.getRingtone(applicationContext, notification)
                    r?.play()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Tampilkan Notifikasi Waktu Habis (Gunakan ID = 2 agar tidak tertimpa)
        createNotificationChannel()
        val intent = Intent(this, FocusActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("TASK_ID", currentTaskId)
            putExtra("TASK_TITLE", currentTaskTitle)
            putExtra("TASK_FIRESTORE_ID", currentTaskFirestoreId)
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("Waktu Habis!")
            .setContentText("Sesi $currentSessionLabel untuk tugas selesai.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(2, notification) // ID 2
    }
}