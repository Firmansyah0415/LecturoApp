package com.lecturo.lecturo.data.model

import com.google.gson.annotations.SerializedName

// T adalah Generic (Bisa berupa User, Event, Task, dll)
data class ApiResponse<T>(
    @SerializedName("status")
    val status: String,

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("data")
    val data: T? = null
)