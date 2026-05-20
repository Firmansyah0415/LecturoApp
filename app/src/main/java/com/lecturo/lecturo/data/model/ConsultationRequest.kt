package com.lecturo.lecturo.data.model

import com.google.gson.annotations.SerializedName

data class ConsultationRequest(
    @SerializedName("uid") val uid: String,
    @SerializedName("consultation_id") val consultationId: String?,
    @SerializedName("recurring_id") val recurringId: String,
    @SerializedName("title") val title: String,
    @SerializedName("date") val date: String,
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String,
    @SerializedName("location") val location: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("priority") val priority: String,
    @SerializedName("status") val status: String,
    @SerializedName("input_source") val inputSource: String,
    @SerializedName("notification_minutes") val notificationMinutes: Int
)