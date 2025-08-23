package com.lecturo.lecturo.data.model

import android.os.Parcelable // <-- IMPORT BARU
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize // <-- IMPORT BARU

@Parcelize // <-- TAMBAHKAN ANOTASI INI
@Entity(tableName = "events")
data class Event(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val category: String,
    val date: String,
    val time: String,
    val location: String,
    val description: String? = null,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val notificationMinutesBefore: Int = 15
) : Parcelable // <-- TAMBAHKAN IMPLEMENTASI INI