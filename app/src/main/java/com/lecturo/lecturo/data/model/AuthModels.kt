package com.lecturo.lecturo.data.model

import com.google.gson.annotations.SerializedName

// Request: Kirim Nomor HP
data class OtpRequest(
    @SerializedName("phone_number")
    val phoneNumber: String
)

// Request: Kirim Nomor HP + Kode OTP
data class VerifyOtpRequest(
    @SerializedName("phone_number")
    val phoneNumber: String,
    @SerializedName("otp_code")
    val otpCode: String
)

// Response: Hasil Request OTP
data class OtpResponse(
    @SerializedName("status")
    val status: String,
    @SerializedName("message")
    val message: String
)

// Response: Hasil Verifikasi (Dapat Token)
data class VerifyOtpResponse(
    @SerializedName("status")
    val status: String,
    @SerializedName("message")
    val message: String,
    @SerializedName("token")
    val token: String?, // Token Custom dari Firebase
    @SerializedName("uid")
    val uid: String?
)