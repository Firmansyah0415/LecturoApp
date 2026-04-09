package com.lecturo.lecturo.viewmodel.focus

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lecturo.lecturo.data.model.FocusSession
import com.lecturo.lecturo.data.repository.FocusRepository
import com.lecturo.lecturo.service.TimerService
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class FocusViewModel(
    private val repository: FocusRepository
) : ViewModel() {

    // --- STATE TIMER ---
    // (Variabel timer sudah dihapus karena diganti TimerService)

    val timerFinishedEvent = MutableLiveData<Boolean>()

    // [TAMBAHAN BARU] Sinyal untuk memainkan suara & getar saat di-Skip
    val playSoundEvent = MutableLiveData<Boolean>()

    private val _timeLeftInMillis = MutableLiveData<Long>()
    val timeLeftInMillis: LiveData<Long> get() = _timeLeftInMillis

    private val _isPlaying = MutableLiveData<Boolean>(false)
    val isPlaying: LiveData<Boolean> get() = _isPlaying

    // Fase: "Fokus", "Istirahat Pendek", "Istirahat Panjang"
    private val _currentPhase = MutableLiveData<String>("Fokus")
    val currentPhase: LiveData<String> get() = _currentPhase

    private val _progress = MutableLiveData<Int>(100)
    val progress: LiveData<Int> get() = _progress

    private val _sessionStatusLabel = MutableLiveData<String>()
    val sessionStatusLabel: LiveData<String> get() = _sessionStatusLabel

    private val _taskFinishedEvent = MutableLiveData<Boolean>()
    val taskFinishedEvent: LiveData<Boolean> get() = _taskFinishedEvent

    // --- DATA TUGAS ---
    var currentTaskId: Long = -1
    var currentTaskFirestoreId: String? = null
    var currentTaskTitle: String = ""

    // --- SETTINGS ---
    var focusDurationMinutes: Int = 25
    var shortBreakMinutes: Int = 5
    var longBreakMinutes: Int = 15

    // Counter Sesi
    private var completedFocusSessions = 0
    private var initialDurationInMillis: Long = 0L

    // --- LOGIKA UTAMA ---

    fun initializeTimer() {
        completedFocusSessions = 0
        updateSessionLabel()
        setPhase("Fokus")
    }

    private fun updateSessionLabel() {
        val current = _currentPhase.value
        if (current == "Fokus") {
            _sessionStatusLabel.value = "FOKUS #${completedFocusSessions + 1}"
        } else {
            _sessionStatusLabel.value = current?.uppercase() ?: "ISTIRAHAT"
        }
    }

    private fun setPhase(phase: String) {
        _currentPhase.value = phase
        updateSessionLabel()

        val minutes = when (phase) {
            "Fokus" -> focusDurationMinutes
            "Istirahat Pendek" -> shortBreakMinutes
            "Istirahat Panjang" -> longBreakMinutes
            else -> 25
        }

        initialDurationInMillis = minutes * 60 * 1000L

        // Reset Timer UI
        _timeLeftInMillis.value = initialDurationInMillis
        _progress.value = 100
        _isPlaying.value = false
    }

    // =========================================================
    // [INTEGRASI BARU] FOREGROUND SERVICE CONTROL
    // =========================================================

    // 1. Start Timer yang baru (Kirim perintah ke Service)
    fun startTimerService(context: Context) {
        val intent = Intent(context, TimerService::class.java)
        intent.action = TimerService.ACTION_START

        // Cek apakah ini Mulai Baru atau Lanjut (Resume)
        val currentTime = _timeLeftInMillis.value ?: 0L

        if (currentTime == initialDurationInMillis || currentTime == 0L) {
            intent.putExtra(TimerService.EXTRA_TIMER_DURATION_INPUT, initialDurationInMillis)
        } else {
            intent.putExtra(TimerService.EXTRA_TIMER_DURATION_INPUT, currentTime)
        }

        // --- [TAMBAHAN BARU: KIRIM DATA KE SERVICE UNTUK NOTIFIKASI] ---
        intent.putExtra(TimerService.EXTRA_TASK_ID, currentTaskId)
        intent.putExtra(TimerService.EXTRA_TASK_TITLE, currentTaskTitle)
        intent.putExtra(TimerService.EXTRA_TASK_FIRESTORE_ID, currentTaskFirestoreId)

        // Kirim status sesi (Misal: "FOKUS #1" atau "ISTIRAHAT PENDEK")
        val currentSessionLabel = _sessionStatusLabel.value ?: "Fokus"
        intent.putExtra(TimerService.EXTRA_SESSION_LABEL, currentSessionLabel)
        // -------------------------------------------------------------

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }

        // --- [LOGIKA BARU: SIMPAN STATUS TUGAS AKTIF] ---
        // Simpan ID tugas yang sedang berjalan ke Preferences agar muncul label "SEDANG FOKUS" di list
        com.lecturo.lecturo.utils.FocusPreferences(context).setActiveTaskId(currentTaskId)
        // ------------------------------------------------

        // [PERBAIKAN BUG 2] Simpan ingatan bahwa timer JALAN
        val prefs = com.lecturo.lecturo.utils.FocusPreferences(context)
        prefs.setActiveTaskId(currentTaskId)
        prefs.setTimerState("RUNNING")
        prefs.setCurrentPhase(_currentPhase.value ?: "Fokus")

        _isPlaying.value = true
    }

    // 2. Pause Timer
    fun pauseTimerService(context: Context) {
        val intent = Intent(context, TimerService::class.java)
        intent.action = TimerService.ACTION_PAUSE
        context.startService(intent)

        // [PERBAIKAN BUG 2] Simpan ingatan bahwa timer PAUSE & Sisa waktunya
        val prefs = com.lecturo.lecturo.utils.FocusPreferences(context)
        prefs.setTimerState("PAUSED")
        prefs.setPausedTimeLeft(_timeLeftInMillis.value ?: 0L)

        _isPlaying.value = false
    }

    // --- FUNGSI UPDATE DARI BROADCAST (Dipanggil Activity) ---

    fun updateTimerFromService(millisLeft: Long, initialDuration: Long) {
        _timeLeftInMillis.value = millisLeft

        // [PERBAIKAN BUG 1: PROGRESS JUMP]
        // Abaikan initialDuration dari Service, gunakan initialDurationInMillis milik ViewModel sendiri
        if (this.initialDurationInMillis > 0) {
            val progressPercentage = (millisLeft.toFloat() / this.initialDurationInMillis.toFloat() * 100).toInt()
            _progress.value = progressPercentage
        }

        if (_isPlaying.value == false) {
            _isPlaying.value = true
        }
    }

    // 1. Dipanggil saat timer HABIS ALAMI dari Service
    fun onTimerFinishedFromService(context: Context) {
        val prefs = com.lecturo.lecturo.utils.FocusPreferences(context)
        if (prefs.getTimerState() == "FINISHED") {

            // [PERBAIKAN BUG AMNESIA 1]: Paksa waktu jadi 0 agar durasi full tersimpan!
            _timeLeftInMillis.value = 0L

            // Simpan data & Ubah Fase
            onTimerFinished()

            // Bersihkan jejak Service
            prefs.setTimerState("STOPPED")
            prefs.clearActiveTaskId()

            // Reset UI
            _timeLeftInMillis.value = initialDurationInMillis
            _progress.value = 100
            _isPlaying.value = false
        }
    }

    // =========================================================
    // PERBAIKAN LOGIKA STOP & SKIP
    // =========================================================

    // 1. Fungsi Stop Timer (Dipanggil saat tombol Stop atau Reset)
    // 3. Dipanggil HANYA saat user menekan tombol STOP/RESET (Batal)
    fun stopTimerManual(context: Context) {
        // Cegah spam
        if (_isPlaying.value == false && _timeLeftInMillis.value == initialDurationInMillis) return

        // Hentikan Service
        val intent = Intent(context, TimerService::class.java)
        intent.action = TimerService.ACTION_STOP
        context.startService(intent)

        com.lecturo.lecturo.utils.FocusPreferences(context).clearActiveTaskId()

        // Simpan sebagai Batal HANYA JIKA sedang fokus dan belum tuntas
        if (_isPlaying.value == true && _currentPhase.value == "Fokus") {
            saveSessionData("CANCELLED")
        }

        // Reset UI
        _timeLeftInMillis.value = initialDurationInMillis
        _progress.value = 100
        _isPlaying.value = false
    }

    // 2. Fungsi Skip (Lewati/Selesai Cepat)
    // 2. Dipanggil saat user menekan SKIP (Selesai Paksa)
    fun skipTimer(context: Context) {
        // Hentikan Service
        val intent = Intent(context, TimerService::class.java)
        intent.action = TimerService.ACTION_STOP
        context.startService(intent)

        com.lecturo.lecturo.utils.FocusPreferences(context).clearActiveTaskId()

        // Kirim Feedback Suara/Getar
        playSoundEvent.value = true
        playSoundEvent.value = false

        // Simpan data & Ubah Fase
        onTimerFinished()

        // Reset UI (Tanpa memanggil save "CANCELLED")
        _timeLeftInMillis.value = initialDurationInMillis
        _progress.value = 100
        _isPlaying.value = false
    }

    // 4. Fungsi internal untuk merubah Fase
    private fun onTimerFinished() {
        val phase = _currentPhase.value

        timerFinishedEvent.value = true
        timerFinishedEvent.value = false

        if (phase == "Fokus") {
            // Simpan data
            saveSessionData("COMPLETED")
            completedFocusSessions++

            // Tentukan Istirahat
            if (completedFocusSessions % 4 == 0) {
                setPhase("Istirahat Panjang")
            } else {
                setPhase("Istirahat Pendek")
            }
        } else {
            setPhase("Fokus")
        }
    }

    fun finishTask() {
        viewModelScope.launch {
            repository.updateTaskStatus(currentTaskId, true)
            _taskFinishedEvent.value = true
        }
    }

    // --- UPDATE SETTING & REFRESH ---

    fun updateSettings(focus: Int, shortBreak: Int, longBreak: Int) {
        this.focusDurationMinutes = focus
        this.shortBreakMinutes = shortBreak
        this.longBreakMinutes = longBreak

        // [PERBAIKAN BUG 2] Jangan reset timer jika kita sedang di tengah-tengah Pause!
        val currentMillis = _timeLeftInMillis.value ?: 0L
        val isAtStart = (currentMillis == this.initialDurationInMillis || currentMillis == 0L)

        if (_isPlaying.value == false && isAtStart) {
            val current = _currentPhase.value ?: "Fokus"
            setPhase(current)
        }
    }

    // --- PENYIMPANAN DATA ---
    private fun saveSessionData(status: String) {
        if (currentTaskId == -1L) return

        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid == null) return

        val endTime = System.currentTimeMillis()
        val elapsedMillis = initialDurationInMillis - (_timeLeftInMillis.value ?: initialDurationInMillis)
        val startTime = endTime - elapsedMillis

        // Cegah menyimpan sesi yang tidak berjalan sama sekali (0 ms)
        if (elapsedMillis <= 0) return

        // [PERBAIKAN BUG DURASI 0]
        // Jika user fokus 30 detik (0.5 mnt), Math.round akan menjadikannya 1 menit.
        // coerceAtLeast(1) memastikan durasi minimal yang tersimpan adalah 1 menit jika timer sudah jalan.
        val durationInMinutes = Math.round(elapsedMillis / 60000.0).toInt().coerceAtLeast(1)

        val session = FocusSession(
            userId = currentUid,
            taskId = currentTaskId,
            taskFirestoreId = currentTaskFirestoreId,
            startTime = startTime,
            endTime = endTime,
            durationMinutes = durationInMinutes, // Gunakan variabel yang baru diperbaiki
            status = status,
            isSynced = false,
            isDeleted = false
        )
        viewModelScope.launch {
            repository.saveSession(session)
        }
    }

    // --- FORMATTER & HISTORY ---
    fun getHistory() = repository.getHistoryByTask(currentTaskId)

    fun getFormattedTime(): String {
        val millis = _timeLeftInMillis.value ?: 0L
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    fun deleteSession(session: FocusSession) {
        viewModelScope.launch {
            repository.deleteSession(session)
        }
    }
    // Tambahkan fungsi ini di FocusViewModel.kt
    fun syncSessionCountFromDb(historyList: List<FocusSession>) {
        // Hitung berapa sesi yang BENAR-BENAR ada dan berstatus COMPLETED
        val realCompletedCount = historyList.filter { it.status == "COMPLETED" }.size

        // Update memori RAM kita
        this.completedFocusSessions = realCompletedCount

        // Update label (UI)
        updateSessionLabel()
    }

    // 2. Fungsi pemulihan saat Activity dibuka kembali
    fun restoreStateOrInitialize(context: Context) {
        val prefs = com.lecturo.lecturo.utils.FocusPreferences(context)
        val activeId = prefs.getActiveTaskId()
        val state = prefs.getTimerState()

        if (currentTaskId == activeId && activeId != -1L) {
            val phase = prefs.getCurrentPhase()
            _currentPhase.value = phase
            updateSessionLabel()

            val minutes = when (phase) {
                "Fokus" -> focusDurationMinutes
                "Istirahat Pendek" -> shortBreakMinutes
                "Istirahat Panjang" -> longBreakMinutes
                else -> 25
            }
            initialDurationInMillis = minutes * 60 * 1000L

            when (state) {
                "PAUSED" -> {
                    val pausedTime = prefs.getPausedTimeLeft()
                    _timeLeftInMillis.value = pausedTime
                    if (initialDurationInMillis > 0) {
                        _progress.value = (pausedTime.toFloat() / initialDurationInMillis.toFloat() * 100).toInt()
                    }
                    _isPlaying.value = false
                }
                "RUNNING" -> {
                    _isPlaying.value = true
                }
                "FINISHED" -> {
                    _isPlaying.value = false

                    // [PERBAIKAN BUG AMNESIA 2]: Paksa waktu jadi 0 agar durasi full tersimpan!
                    _timeLeftInMillis.value = 0L

                    onTimerFinished() // Simpan data ke database

                    prefs.setTimerState("STOPPED") // Bersihkan jejak
                    prefs.clearActiveTaskId()

                    // Reset UI
                    _timeLeftInMillis.value = initialDurationInMillis
                    _progress.value = 100
                }
            }
        } else {
            initializeTimer()
        }
    }
}