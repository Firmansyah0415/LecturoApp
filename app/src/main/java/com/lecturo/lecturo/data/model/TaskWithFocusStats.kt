package com.lecturo.lecturo.data.model

import androidx.room.Embedded

data class TaskWithFocusStats(
    @Embedded val task: Tasks, // Ini akan menampung semua isi tabel tasks
    val completedSessionsCount: Int, // Hasil COUNT dari tabel focus_sessions
    val totalFocusMinutes: Int // Hasil SUM dari tabel focus_sessions
)