package com.lecturo.lecturo.utils

import java.io.Serializable

data class EventInfo(
    val title: String?,
    val date: String?, // Format: dd/MM/yyyy
    val time: String?, // Format: HH:mm
    val location: String?
) : Serializable // Serializable agar bisa dikirim melalui Intent
