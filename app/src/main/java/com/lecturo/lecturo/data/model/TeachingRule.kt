package com.lecturo.lecturo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "teaching_rules")
data class TeachingRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val courseName: String,
    val className: String,
    val dayOfWeek: String,
    val startTime: String,
    val endTime: String,
    val location: String,
    val studentCount: Int,
    val semesterStartDate: String,
    val repetitionType: String,
    val repetitionValue: String,

    // --- KOLOM BARU YANG PENTING ---
    // Menyimpan nilai menit untuk notifikasi (misal: 15, 30, 60)
    val notificationMinutesBefore: Int = 15 // Default 15 menit
): Serializable
