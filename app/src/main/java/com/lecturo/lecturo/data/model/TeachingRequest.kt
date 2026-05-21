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
    val dayOfWeek: String,

    @SerializedName("date")
    val date: String,

    @SerializedName("start_time")
    val startTime: String,

    @SerializedName("end_time")
    val endTime: String,

    @SerializedName("classroom")
    val classroom: String,

    @SerializedName("student_count")
    val studentCount: Int,

    @SerializedName("meeting_number")
    val meetingNumber: Int,

    @SerializedName("is_completed")
    val isCompleted: Boolean,

    @SerializedName("notification_minutes")
    val notificationMinutes: Int,

    // 🔴 [TAMBAHAN BARU]
    @SerializedName("input_source")
    val inputSource: String
)