package com.lecturo.lecturo.data.model

import androidx.room.ColumnInfo // <-- Pastikan ini di-import
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Entity(tableName = "consultation_patterns")
data class ConsultationPattern(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @SerializedName("firestoreId")
    var firestoreId: String? = null,

    @SerializedName("user_id")
    @ColumnInfo(name = "user_id") // <-- Tambahkan ini
    val userId: String? = null,

    @ColumnInfo(name = "is_synced")
    var isSynced: Boolean = false,

    @ColumnInfo(name = "is_deleted")
    var isDeleted: Boolean = false,

    @SerializedName("title_template")
    @ColumnInfo(name = "title_template") // <-- Tambahkan ini
    val titleTemplate: String,

    @SerializedName("day_of_week")
    @ColumnInfo(name = "day_of_week") // <-- PENTING untuk ORDER BY day_of_week
    val dayOfWeek: Int,

    @SerializedName("start_time")
    @ColumnInfo(name = "start_time") // <-- Tambahkan ini
    val startTime: String,

    @SerializedName("end_time")
    @ColumnInfo(name = "end_time") // <-- Tambahkan ini
    val endTime: String,

    @SerializedName("location_default")
    @ColumnInfo(name = "location_default") // <-- Tambahkan ini
    val locationDefault: String? = null,

    @SerializedName("is_active")
    @ColumnInfo(name = "is_active") // <-- PENTING untuk WHERE is_active = 1
    var isActive: Boolean = true

) : Serializable