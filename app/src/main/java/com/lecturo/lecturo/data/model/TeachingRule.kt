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

    // Pastikan kedua kolom ini ada di dalam data class Anda
    val repetitionType: String,
    val repetitionValue: String
): Serializable
