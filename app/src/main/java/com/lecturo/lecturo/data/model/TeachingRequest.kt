package com.lecturo.lecturo.data.model

import com.google.gson.annotations.SerializedName

/**
 * Request Model untuk Node.js
 * Konsisten menggunakan:
 * - 'id' untuk referensi dokumen Firestore (nullable saat create)
 * - 'user_id' menggantikan 'uid'
 */
data class TeachingRequest(
    @SerializedName("id")
    val id: String?,

    @SerializedName("user_id")
    val userId: String,

    @SerializedName("course_name")
    val courseName: String,

    @SerializedName("class_code")
    val classCode: String,

    @SerializedName("day_of_week")
    val dayOfWeek: String, // Senin, Selasa, dst.

    @SerializedName("start_time")
    val startTime: String, // HH:mm

    @SerializedName("end_time")
    val endTime: String, // HH:mm

    @SerializedName("classroom")
    val classroom: String, // Ruang A101, Lab, dll

    @SerializedName("student_count")
    val studentCount: Int,

    @SerializedName("start_date")
    val startDate: String, // YYYY-MM-DD

    @SerializedName("notification_minutes")
    val notificationMinutes: Int
)