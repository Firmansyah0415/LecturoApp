package com.lecturo.lecturo.data.model

import com.google.gson.annotations.SerializedName

// 1. Request: Wadah untuk MENGIRIM Teks ke Backend
data class ExtractEventRequest(
    @SerializedName("text") val text: String
)

// 2. Response: Wadah untuk MENERIMA Balasan dari Backend
data class ExtractEventResponse(
    @SerializedName("status") val status: String,
    @SerializedName("data") val data: EventDto? // Bisa null jika gagal
)

// 3. DTO (Data Transfer Object): Struktur JSON detail event dari Gemini
data class EventDto(
    @SerializedName("title") val title: String?,
    @SerializedName("category") val category: String?,
    @SerializedName("date") val date: String?,
    @SerializedName("time") val time: String?,
    @SerializedName("end_time") val endTime: String?,
    @SerializedName("location") val location: String?,
    @SerializedName("description") val description: String?
) {
    // Fungsi PENTING: Mengubah data mentah dari AI menjadi object Event aplikasi
    fun toEvent(): Event {
        return Event(
            title = title ?: "Tanpa Judul",
            category = category ?: "Lainnya",
            date = date ?: "",
            time = time ?: "",
            endTime = endTime ?: "",
            location = location ?: "",
            description = description ?: "",
            inputSource = "AI_SCAN",
            priority = "Sedang",
            isCompleted = false,
            notificationMinutesBefore = 15
        )
    }
}