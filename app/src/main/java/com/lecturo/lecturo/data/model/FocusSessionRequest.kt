package com.lecturo.lecturo.data.model

import com.google.gson.annotations.SerializedName

data class FocusSessionRequest(
    @SerializedName("uid")
    val uid: String,

    @SerializedName("session_id")
    val sessionId: String?, // firestoreId sesi ini

    @SerializedName("task_id")
    val taskFirestoreId: String?, // ID Tugas di Cloud

    @SerializedName("start_time")
    val startTime: String, // Format ISO String (yyyy-MM-dd HH:mm:ss)

    @SerializedName("end_time")
    val endTime: String,   // Format ISO String

    @SerializedName("duration")
    val duration: Int,

    @SerializedName("status")
    val status: String
)