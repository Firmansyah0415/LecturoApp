package com.lecturo.lecturo.data.model

import com.google.gson.annotations.SerializedName
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

    @SerializedName("repetition_type")
    val repetitionType: String?, // <--- TAMBAHKAN INI

    @SerializedName("repetition_value")
    val repetitionValue: String?, // <--- TAMBAHKAN INI

    @SerializedName("notification_minutes")
    val notificationMinutes: Int
)