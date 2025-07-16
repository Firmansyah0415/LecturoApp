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
    val category: String,
    val sourceFeatureType: String,
    val sourceFeatureId: Long,
    val notificationMinutesBefore: Int = 15,
    val isRepeating: Boolean = false,
    val repeatInterval: String? = null,
    val createdAt: Long = System.currentTimeMillis(),

    // PERBAIKAN: Buat ID unik untuk notifikasi.
    // Ini akan menghasilkan nomor acak yang berbeda setiap kali objek dibuat.
    val notificationId: Int = (System.currentTimeMillis().toInt() / 1000) + Random.nextInt()
)
