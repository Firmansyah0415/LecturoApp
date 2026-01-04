package com.lecturo.lecturo.data.model

import com.google.gson.annotations.SerializedName

data class UserResponse(
    @SerializedName("status")
    val status: String,

    @SerializedName("message")
    val message: String,

    @SerializedName("data")
    val data: User?
)