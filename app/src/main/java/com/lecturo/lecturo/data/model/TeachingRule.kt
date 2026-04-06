package com.lecturo.lecturo.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Entity(tableName = "teaching_rules")
data class TeachingRule(
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

    @SerializedName("start_time")
    val startTime: String,

    @SerializedName("end_time")
    val endTime: String,

    @SerializedName("priority")
    val priority: String? = "High",

    @SerializedName("student_count")
    val studentCount: Int,

    @SerializedName("start_date")
    val startDate: String,

    @SerializedName("repetition_type")
    val repetitionType: String? = "Weekly",

    @SerializedName("repetition_value")
    val repetitionValue: String? = "1",

    @SerializedName("notification_minutes")
    val notificationMinutes: Int = 15
) : Serializable