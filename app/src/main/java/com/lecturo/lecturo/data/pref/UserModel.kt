package com.lecturo.lecturo.data.pref

data class UserModel(
    val email: String,
    val token: String,
    val isLogin: Boolean = false,
    val name: String = "",       // 👈 Tambahkan ini
    val photoUrl: String = ""    // 👈 Tambahkan ini
)
