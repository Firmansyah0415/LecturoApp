package com.lecturo.lecturo.data.model

import com.google.gson.annotations.SerializedName

data class EventRequest(
    @SerializedName("uid") val uid: String,
    @SerializedName("event_id") val eventId: String?, // Null jika baru
    @SerializedName("title") val title: String,
    @SerializedName("category") val category: String, // Contoh: "Rapat", "Seminar"
    @SerializedName("priority") val priority: String,
    @SerializedName("input_source") val inputSource: String,
    @SerializedName("date") val date: String,
    @SerializedName("time") val time: String,
    @SerializedName("location") val location: String,
    @SerializedName("description") val description: String,
    @SerializedName("is_completed") val isCompleted: Boolean,
    @SerializedName("notification_minutes") val notificationMinutes: Int
)
