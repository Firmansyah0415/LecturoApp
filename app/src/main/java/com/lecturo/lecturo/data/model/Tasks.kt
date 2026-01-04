package com.lecturo.lecturo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Entity(tableName = "tasks")
data class Tasks(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @SerializedName("firestoreId")
    var firestoreId: String? = null,

    // Tetap ada untuk Manual Injection
    @SerializedName("user_id")
    val userId: String? = null,

    @SerializedName("title")
    val title: String,

    @SerializedName("date")
    val date: String,

    @SerializedName("time")
    val time: String,

    @SerializedName("location")
    val location: String? = null,

    @SerializedName("description")
    val description: String? = null,

    @SerializedName("priority")
    val priority: String? = "Sedang",

    @SerializedName("input_source")
    val inputSource: String? = "MANUAL",

    @SerializedName("is_completed")
    var isCompleted: Boolean = false,

    @SerializedName("notification_minutes")
    val notificationMinutesBefore: Int = 15
) : Serializable