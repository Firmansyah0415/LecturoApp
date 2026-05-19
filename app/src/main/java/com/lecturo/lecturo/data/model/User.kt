package com.lecturo.lecturo.data.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class User(
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

    // TAMBAHKAN BARIS INI
    @SerializedName("gender")
    val gender: String = "",

    @SerializedName("photo_url")
    val photoUrl: String = "",

) : Parcelable