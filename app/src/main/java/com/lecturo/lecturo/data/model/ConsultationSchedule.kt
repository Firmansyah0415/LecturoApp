package com.lecturo.lecturo.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Entity(tableName = "consultation_schedules")
data class ConsultationSchedule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @SerializedName("firestoreId")
    var firestoreId: String? = null,

    @ColumnInfo(name = "is_synced")
    var isSynced: Boolean = false,

    @ColumnInfo(name = "is_deleted")
    var isDeleted: Boolean = false,

    @SerializedName("user_id")
    @ColumnInfo(name = "user_id")
    val userId: String? = null,

    @SerializedName("recurring_id")
    @ColumnInfo(name = "recurring_id")
    val recurringId: String = "",

    @SerializedName("title")
    val title: String,

    @SerializedName("date")
    val date: String,

    @SerializedName("start_time")
    @ColumnInfo(name = "start_time")
    val startTime: String,

    @SerializedName("end_time")
    @ColumnInfo(name = "end_time")
    val endTime: String,

    // PERBAIKAN: Paksa menjadi String kosong
    @SerializedName("location")
    val location: String = "",

    // PERBAIKAN: Paksa menjadi String kosong
    @SerializedName("description")
    val description: String = "",

    @SerializedName("priority")
    val priority: String = "Medium",

    @SerializedName("status")
    var status: String = "SCHEDULED",

    @SerializedName("input_source")
    @ColumnInfo(name = "input_source")
    val inputSource: String = "MANUAL",

    @SerializedName("notification_minutes")
    @ColumnInfo(name = "notification_minutes")
    val notificationMinutesBefore: Int = 15
) : Serializable