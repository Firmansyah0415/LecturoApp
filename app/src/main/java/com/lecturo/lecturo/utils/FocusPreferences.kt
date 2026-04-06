package com.lecturo.lecturo.utils

import android.content.Context
import android.content.SharedPreferences

class FocusPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("FocusPrefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_FOCUS_DURATION = "focus_duration"
        const val KEY_SHORT_BREAK = "short_break"
        const val KEY_LONG_BREAK = "long_break"
        const val KEY_SOUND_ENABLED = "sound_enabled"
        const val KEY_VIBRATION_ENABLED = "vibration_enabled"

        // [TAMBAHAN BARU] Key untuk menyimpan ID tugas yang sedang aktif timernya
        const val KEY_ACTIVE_TASK_ID = "active_task_id"

        // --- [TAMBAHAN BARU UNTUK BUG 2: MEMORI STATUS] ---
        const val KEY_TIMER_STATE = "timer_state" // Isinya: "RUNNING", "PAUSED", "STOPPED"
        const val KEY_PAUSED_TIME = "paused_time_left"
        const val KEY_CURRENT_PHASE = "current_phase"
    }

    // --- GETTERS ---
    fun getFocusDuration(): Int = prefs.getInt(KEY_FOCUS_DURATION, 25)
    fun getShortBreakDuration(): Int = prefs.getInt(KEY_SHORT_BREAK, 5)
    fun getLongBreakDuration(): Int = prefs.getInt(KEY_LONG_BREAK, 15)
    fun isSoundEnabled(): Boolean = prefs.getBoolean(KEY_SOUND_ENABLED, true)
    fun isVibrationEnabled(): Boolean = prefs.getBoolean(KEY_VIBRATION_ENABLED, false)

    // [TAMBAHAN BARU] Ambil ID Tugas Aktif (-1 berarti tidak ada yang aktif)
    fun getActiveTaskId(): Long = prefs.getLong(KEY_ACTIVE_TASK_ID, -1L)

    // [TAMBAHAN BARU: Ambil Status]
    fun getTimerState(): String = prefs.getString(KEY_TIMER_STATE, "STOPPED") ?: "STOPPED"
    fun getPausedTimeLeft(): Long = prefs.getLong(KEY_PAUSED_TIME, 0L)
    fun getCurrentPhase(): String = prefs.getString(KEY_CURRENT_PHASE, "Fokus") ?: "Fokus"

    // --- SETTERS ---
    fun saveSettings(focus: Int, shortBreak: Int, longBreak: Int, sound: Boolean, vibration: Boolean) {
        prefs.edit().apply {
            putInt(KEY_FOCUS_DURATION, focus)
            putInt(KEY_SHORT_BREAK, shortBreak)
            putInt(KEY_LONG_BREAK, longBreak)
            putBoolean(KEY_SOUND_ENABLED, sound)
            putBoolean(KEY_VIBRATION_ENABLED, vibration)
            apply()
        }
    }

    // [TAMBAHAN BARU] Simpan ID Tugas yang sedang aktif
    fun setActiveTaskId(taskId: Long) {
        prefs.edit().putLong(KEY_ACTIVE_TASK_ID, taskId).apply()
    }

    // [TAMBAHAN BARU: Simpan Status]
    fun setTimerState(state: String) = prefs.edit().putString(KEY_TIMER_STATE, state).apply()
    fun setPausedTimeLeft(time: Long) = prefs.edit().putLong(KEY_PAUSED_TIME, time).apply()
    fun setCurrentPhase(phase: String) = prefs.edit().putString(KEY_CURRENT_PHASE, phase).apply()

    // [TAMBAHAN BARU] Hapus status aktif (Dipanggil saat timer distop/selesai)
    fun clearActiveTaskId() {
        prefs.edit().remove(KEY_ACTIVE_TASK_ID).apply()
        prefs.edit().remove(KEY_TIMER_STATE).apply() // <--- Tambahkan ini
        prefs.edit().remove(KEY_PAUSED_TIME).apply() // <--- Tambahkan ini
    }
}