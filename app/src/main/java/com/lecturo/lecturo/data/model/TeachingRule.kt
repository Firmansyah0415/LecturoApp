package com.lecturo.lecturo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Entity(tableName = "teaching_rules")
data class TeachingRule(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0, // ID Lokal (Room)

    // 1. Tambahkan SerializedName agar firestoreId dari JSON terbaca
    // Backend mengirim 'firestoreId' (sesuai update controller terakhir)
    var firestoreId: String? = null,

    // 2. Berikan default value kosong, karena JSON dari 'teaching_schedules' biasanya tidak memuat user_id (karena ada di parent doc)
    @SerializedName("user_id")
    val userId: String? = null,

    // 3. Tambahkan SerializedName untuk semua field yang namanya beda (camelCase vs snake_case)
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

    // 4. Data ini tidak ada di JSON Backend, jadi berikan Default Value
    @SerializedName("priority")
    val priority: String? = "High",

    @SerializedName("student_count")
    val studentCount: Int,

    @SerializedName("start_date")
    val startDate: String,

    // 5. CRASH UTAMA: Data ini tidak ada di JSON Backend, WAJIB Nullable atau Default
    // Karena logic repetition mungkin belum ada di backend, kita set null/default aman.
    @SerializedName("repetition_type")
    val repetitionType: String? = "Weekly", // Default mingguan

    @SerializedName("repetition_value")
    val repetitionValue: String? = "1", // Default 1

    @SerializedName("notification_minutes")
    val notificationMinutes: Int = 15
) : Serializable