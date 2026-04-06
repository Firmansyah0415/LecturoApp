package com.lecturo.lecturo.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @SerializedName("firestoreId")
    var firestoreId: String? = null,

    // [BARU] Flag Sinkronisasi
    @ColumnInfo(name = "is_synced")
    var isSynced: Boolean = false,

    @ColumnInfo(name = "is_deleted")
    var isDeleted: Boolean = false,

    @SerializedName("user_id")
    @ColumnInfo(name = "user_id")
    val userId: String? = null,

    @ColumnInfo(name = "task_id")
    val taskId: Long,

    @SerializedName("task_firestore_id")
    @ColumnInfo(name = "task_firestore_id")
    val taskFirestoreId: String? = null,

    @SerializedName("start_time")
    @ColumnInfo(name = "start_time")
    val startTime: Long,

    @SerializedName("end_time")
    @ColumnInfo(name = "end_time")
    val endTime: Long,

    @SerializedName("duration_minutes")
    @ColumnInfo(name = "duration_minutes")
    val durationMinutes: Int,

    @SerializedName("status")
    val status: String

) : Serializable