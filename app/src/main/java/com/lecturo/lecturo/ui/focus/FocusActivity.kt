package com.lecturo.lecturo.ui.focus

import android.content.Context
import android.media.RingtoneManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.repository.FocusRepository
import com.lecturo.lecturo.databinding.ActivityFocusBinding
import com.lecturo.lecturo.ui.base.BaseActivity
import com.lecturo.lecturo.utils.FocusPreferences
import com.lecturo.lecturo.viewmodel.focus.FocusViewModel
import com.lecturo.lecturo.viewmodel.focus.FocusViewModelFactory

class FocusActivity : BaseActivity() {

    private lateinit var binding: ActivityFocusBinding

    private val viewModel: FocusViewModel by viewModels {
        val db = AppDatabase.getDatabase(this)
        // Repository baru butuh Context, bukan API
        val repo = FocusRepository(
            db.focusSessionDao(),
            db.tasksDao(),
            applicationContext
        )
        FocusViewModelFactory(repo)
    }

    // Launcher Settings (Callback saat user kembali dari pengaturan)
    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            refreshTimerFromPreferences()
            Toast.makeText(this, "Waktu diperbarui", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFocusBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSystemUI()

        // 1. Ambil Data Tugas
        val taskId = intent.getLongExtra("TASK_ID", -1)
        val taskTitle = intent.getStringExtra("TASK_TITLE") ?: "Tanpa Judul"
        val taskFirestoreId = intent.getStringExtra("TASK_FIRESTORE_ID")

        if (taskId == -1L) {
            finish()
            return
        }

        viewModel.currentTaskId = taskId
        viewModel.currentTaskTitle = taskTitle
        viewModel.currentTaskFirestoreId = taskFirestoreId
        binding.tvTaskTitle.text = taskTitle

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // UBAH MENJADI:
        // 1. Ambil settingan menit durasi dulu dari preferences
        refreshTimerFromPreferences()

        // 2. [PERBAIKAN BUG 2] Pulihkan ingatan waktu pause, ATAU inisialisasi baru jika tidak ada
        viewModel.restoreStateOrInitialize(this)

        setupObservers()
        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        val filter = android.content.IntentFilter().apply {
            addAction(com.lecturo.lecturo.service.TimerService.ACTION_TICK)
            addAction(com.lecturo.lecturo.service.TimerService.ACTION_TIMER_FINISHED)
        }

        // [FIX] Gunakan ContextCompat agar support semua versi Android tanpa error merah
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            timerReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    // Tambahkan blok fungsi ini di dalam class FocusActivity
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Set intent baru yang datang dari Notifikasi sebagai intent utama
        setIntent(intent)

        // Ambil ulang data dari Notifikasi
        val taskId = intent.getLongExtra("TASK_ID", -1)
        val taskTitle = intent.getStringExtra("TASK_TITLE") ?: "Tanpa Judul"
        val taskFirestoreId = intent.getStringExtra("TASK_FIRESTORE_ID")

        // Jika valid, perbarui tampilan
        if (taskId != -1L) {
            viewModel.currentTaskId = taskId
            viewModel.currentTaskTitle = taskTitle
            viewModel.currentTaskFirestoreId = taskFirestoreId
            binding.tvTaskTitle.text = taskTitle

            // Refresh riwayat agar label FOKUS #... nya akurat
            viewModel.getHistory().value?.let {
                viewModel.syncSessionCountFromDb(it)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(timerReceiver)
    }

    // Di dalam class FocusActivity
    private val timerReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                com.lecturo.lecturo.service.TimerService.ACTION_TICK -> {
                    val millisLeft = intent.getLongExtra(com.lecturo.lecturo.service.TimerService.EXTRA_TIME_LEFT, 0L)
                    val duration = intent.getLongExtra(com.lecturo.lecturo.service.TimerService.EXTRA_DURATION, 1L)
                    viewModel.updateTimerFromService(millisLeft, duration)
                }
                com.lecturo.lecturo.service.TimerService.ACTION_TIMER_FINISHED -> {
                    // [PERBAIKAN] Panggil fungsi ini agar tidak nyangkut
                    viewModel.onTimerFinishedFromService(this@FocusActivity)
                }
            }
        }
    }

    private fun refreshTimerFromPreferences() {
        val prefs = FocusPreferences(this)
        val focus = prefs.getFocusDuration()
        val short = prefs.getShortBreakDuration()
        val long = prefs.getLongBreakDuration()

        viewModel.updateSettings(focus, short, long)
    }

    private fun setupObservers() {
        // Observer Timer String
        viewModel.getFormattedTime().let { binding.tvTimer.text = it }
        viewModel.timeLeftInMillis.observe(this) {
            binding.tvTimer.text = viewModel.getFormattedTime()
        }

        // Observer Progress Bar
        viewModel.progress.observe(this) { prog ->
            binding.progressBarTimer.setProgress(prog, true)
        }

        // Observer Ikon Play/Pause
        viewModel.isPlaying.observe(this) { isPlaying ->
            val icon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
            binding.ivPlayPause.setImageResource(icon)
        }

        // Observer Status Sesi
        viewModel.sessionStatusLabel.observe(this) { status ->
            binding.tvFocusStatus.text = status
            if (status.contains("ISTIRAHAT")) {
                binding.tvFocusStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
            } else {
                binding.tvFocusStatus.setTextColor(getColor(R.color.colorPrimary))
            }
        }

        // [TAMBAHAN BARU] Observer untuk memainkan suara saat tombol Skip ditekan
        viewModel.playSoundEvent.observe(this) { play ->
            if (play) {
                playFeedback()
            }
        }

        // 2. Ganti observer Selesai Tugas
        viewModel.taskFinishedEvent.observe(this) { finished ->
            if (finished) {
                // Matikan service dan simpan sisa waktu dengan benar
                viewModel.stopTimerManual(this)
                finish()
            }
        }

        // [TAMBAHAN BARU] Pantau database riwayat!
        // Jika ada yang dihapus di History, ini akan otomatis terpanggil
        viewModel.getHistory().observe(this) { historyList ->
            viewModel.syncSessionCountFromDb(historyList)
        }
    }

    private fun setupButtons() {
        binding.btnPlayPause.setOnClickListener {
            if (viewModel.isPlaying.value == true) {
                viewModel.pauseTimerService(this)
            } else {
                viewModel.startTimerService(this)
            }
        }

        binding.btnStop.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Reset Timer?")
                .setMessage("Waktu akan dikembalikan ke awal.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Reset") { _, _ ->
                    // [PERBAIKAN] Panggil nama fungsi baru
                    viewModel.stopTimerManual(this)
                }
                .show()
        }

        binding.btnSkip.setOnClickListener {
            val isFocus = viewModel.currentPhase.value == "Fokus"
            val actionTitle = if (isFocus) "Selesaikan Sesi Ini?" else "Lewati Istirahat?"
            val message = if (isFocus) "Sesi akan dihitung selesai." else "Langsung mulai sesi fokus berikutnya."

            MaterialAlertDialogBuilder(this)
                .setTitle(actionTitle)
                .setMessage(message)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Ya") { _, _ ->
                    viewModel.skipTimer(this)
                }
                .show()
        }

        binding.btnSettings.setOnClickListener {
            val intent = Intent(this, FocusSettingsActivity::class.java)
            settingsLauncher.launch(intent)
        }

        binding.btnFinishTask.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Tugas Selesai?")
                .setMessage("Selamat! Tugas ini akan ditandai selesai.")
                .setNegativeButton("Belum", null)
                .setPositiveButton("Ya, Selesai") { _, _ ->
                    viewModel.stopTimerManual(this)
                    viewModel.finishTask()
                }
                .show()
        }

        // Setup tombol history (yang ada di kiri atas layout baru)
        // Pastikan ID di xml adalah btnHistory
        binding.btnHistory.setOnClickListener {
            val intent = Intent(this, FocusHistoryActivity::class.java)
            intent.putExtra("TASK_ID", viewModel.currentTaskId) // Kirim ID Tugas
            // --- [TAMBAHAN BARU] Kirim juga judul tugasnya ---
            intent.putExtra("TASK_TITLE", viewModel.currentTaskTitle)
            // ------------------------------------------------
            startActivity(intent)
        }
    }

    // [TAMBAHAN BARU] Fungsi khusus untuk memainkan Suara & Getar saat Skip
    private fun playFeedback() {
        val prefs = FocusPreferences(this)

        // 1. GETARAN (VIBRATION)
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

        // 2. SUARA (SOUND)
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
    }

    private fun setupSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // 1. Cek apakah aplikasi sedang di Mode Gelap
        val isNightMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        // 2. atur warna status bar
        window.statusBarColor = getColor(R.color.colorPrimary)
        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = !isNightMode
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(view.paddingLeft, statusBarInsets.top, view.paddingRight, view.paddingBottom)
            insets
        }
    }
}