package com.lecturo.lecturo

import java.io.Serializable

data class Schedule(
    val id: Long = 0,
    val title: String,
    val date: String,
    val time: String,
    val location: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
) : Serializable