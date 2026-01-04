package com.lecturo.lecturo.data.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class User(
    // Kunci Utama (Wajib ada)
    @SerializedName("uid")
    val uid: String,

    @SerializedName("phone_number")
    val phoneNumber: String,

    @SerializedName("email")
    val email: String = "",

    @SerializedName("full_name")
    val fullName: String = "",

    @SerializedName("university")
    val university: String = "",

    @SerializedName("faculty")
    val faculty: String = "",

    @SerializedName("major")
    val major: String = "",

    @SerializedName("photo_url")
    val photoUrl: String = "",

) : Parcelable