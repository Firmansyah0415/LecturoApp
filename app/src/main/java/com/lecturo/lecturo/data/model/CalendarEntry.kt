package com.lecturo.lecturo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.random.Random

@Entity(tableName = "calendar_entries")
data class CalendarEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val date: String,
    val time: String,
    val endTime: String = "",
    val category: String,
    val priority: String = "Sedang",
    val sourceFeatureType: String,
    val sourceFeatureId: Long,
    val notificationMinutesBefore: Int = 15,
    val isRepeating: Boolean = false,
    val repeatInterval: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val notificationId: Int = (System.currentTimeMillis().toInt() / 1000) + Random.nextInt(),

    // 🔴 [TAMBAHAN BARU OPSI B] Menampung status selesai di kalender agregator
    val isCompleted: Boolean = false
)