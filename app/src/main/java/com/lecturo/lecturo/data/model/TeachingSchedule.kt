package com.lecturo.lecturo.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Entity(tableName = "teaching_schedules") // Nama tabel sudah benar
data class TeachingSchedule(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    var firestoreId: String? = null,

    @ColumnInfo(name = "is_synced")
    var isSynced: Boolean = false,

    @ColumnInfo(name = "is_deleted")
    var isDeleted: Boolean = false,

    @SerializedName("user_id")
    val userId: String? = null,

    @SerializedName("course_name")
    val courseName: String,

    @SerializedName("class_code")
    val classCode: String,

    @SerializedName("classroom")
    val classroom: String,

    @SerializedName("day_of_week")
    val dayOfWeek: String,

    // [TAMBAHAN BARU] Tanggal spesifik pertemuan
    @SerializedName("date")
    val date: String,

    @SerializedName("start_time")
    val startTime: String,

    @SerializedName("end_time")
    val endTime: String,

    @SerializedName("priority")
    val priority: String? = "High",

    @SerializedName("student_count")
    val studentCount: Int,

    // [TAMBAHAN BARU] Penanda pertemuan ke-berapa (P1, P2, P3...)
    @SerializedName("meeting_number")
    @ColumnInfo(name = "meeting_number")
    val meetingNumber: Int = 1,

    // [TAMBAHAN BARU] Penanda apakah kelas sudah selesai
    @SerializedName("is_completed")
    @ColumnInfo(name = "is_completed")
    var isCompleted: Boolean = false,

    @SerializedName("notification_minutes")
    val notificationMinutes: Int = 15,

    // 🔴 [TAMBAHAN BARU] Menyimpan sumber data (WA_BOT, WEB_UPLOAD, MANUAL)
    @SerializedName("input_source")
    @ColumnInfo(name = "input_source")
    val inputSource: String = "MANUAL"
) : Serializable