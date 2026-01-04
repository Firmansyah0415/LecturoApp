package com.lecturo.lecturo.data.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "events")
data class Event(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Backend mengirim 'firestoreId' (camelCase), aman.
    @SerializedName("firestoreId")
    var firestoreId: String? = null,

    // 1. WAJIB ADA: Agar Manual Injection di DataRestoreManager berhasil
    @SerializedName("user_id")
    val userId: String? = null,

    @SerializedName("title")
    val title: String,

    @SerializedName("category")
    val category: String,

    @SerializedName("date")
    val date: String,

    @SerializedName("time")
    val time: String,

    // 2. UBAH JADI NULLABLE: Agar tidak crash jika lokasi kosong di Cloud
    @SerializedName("location")
    val location: String? = null,

    @SerializedName("description")
    val description: String? = null,

    // 3. UBAH JADI NULLABLE: Default "Sedang"
    @SerializedName("priority")
    val priority: String? = "Sedang",

    @SerializedName("input_source")
    val inputSource: String? = "MANUAL",

    // 4. MAPPING: Backend 'is_completed' -> Android 'isCompleted'
    @SerializedName("is_completed")
    var isCompleted: Boolean = false,

    // Ini data lokal, tidak perlu @SerializedName karena backend tidak kirim ini
    val createdAt: Long = System.currentTimeMillis(),

    // 5. MAPPING: Backend 'notification_minutes' -> Android 'notificationMinutesBefore'
    @SerializedName("notification_minutes")
    val notificationMinutesBefore: Int = 15
) : Parcelable