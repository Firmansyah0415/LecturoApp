package com.lecturo.lecturo.viewmodel.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lecturo.lecturo.data.model.User
import com.lecturo.lecturo.data.pref.UserModel
import com.lecturo.lecturo.data.repository.UserRepository
import kotlinx.coroutines.launch

class CompleteProfileViewModel(private val repository: UserRepository) : ViewModel() {

    fun saveProfile(
        uid: String,
        phoneNumber: String,
        fullName: String,
        email: String,
        university: String,
        faculty: String,
        major: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onLoading: (Boolean) -> Unit
    ) {
        onLoading(true)
        viewModelScope.launch {
            try {
                // 1. Buat Objek User Lengkap
                val user = User(
                    uid = uid,
                    phoneNumber = phoneNumber,
                    email = email,
                    fullName = fullName,
                    university = university,
                    faculty = faculty,
                    major = major,
                    photoUrl = "" // Foto kosong dulu
                )

                // 2. Kirim ke Backend
                val result = repository.syncUserToBackend(user)

                // 3. Simpan Sesi Lokal
                // Kita perlu simpan bahwa user sudah login dan punya token
                val userSession = UserModel(
                    email = if (email.isNotEmpty()) email else "$phoneNumber@lecturo.com",
                    token = uid,
                    isLogin = true
                )
                repository.saveSession(userSession)

                onLoading(false)

                if (result.isSuccess) {
                    onSuccess()
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Gagal menyimpan profil"
                    onError(errorMsg)
                }

            } catch (e: Exception) {
                onLoading(false)
                onError(e.message ?: "Terjadi kesalahan sistem")
            }
        }
    }
}