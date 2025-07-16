package com.lecturo.lecturo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "tasks")
data class Tasks(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val date: String,
    val time: String,
    val location: String,
    val description: String?,
    var completed: Boolean = false,

    // --- KOLOM BARU YANG PENTING ---
    // Menyimpan nilai menit untuk notifikasi (misal: 15, 30, 60)
    val notificationMinutesBefore: Int = 15 // Default 15 menit
) : Serializable