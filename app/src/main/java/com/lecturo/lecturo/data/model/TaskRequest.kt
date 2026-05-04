package com.lecturo.lecturo.data.model

import com.google.gson.annotations.SerializedName

data class TaskRequest(
    @SerializedName("uid") val uid: String,
    @SerializedName("task_id") val taskId: String?,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("date") val date: String,
    @SerializedName("time") val time: String,
    @SerializedName("end_time") val endTime: String,
    @SerializedName("location") val location: String,
    @SerializedName("priority") val priority: String,
    @SerializedName("input_source") val inputSource: String,
    @SerializedName("is_completed") val isCompleted: Boolean,
    @SerializedName("notification_minutes") val notificationMinutes: Int
)