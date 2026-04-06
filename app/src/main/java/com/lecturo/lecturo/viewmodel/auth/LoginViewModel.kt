package com.lecturo.lecturo.viewmodel.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lecturo.lecturo.data.model.User
import com.lecturo.lecturo.data.pref.UserModel
import com.lecturo.lecturo.data.repository.UserRepository
import kotlinx.coroutines.launch

class LoginViewModel(private val repository: UserRepository) : ViewModel() {

    // --- FUNGSI 1: Minta OTP ke WA Backend ---
    fun requestOtp(phoneNumber: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = repository.requestOtpWa(phoneNumber)
            if (result.isSuccess) {
                onSuccess()
            } else {
                onError(result.exceptionOrNull()?.message ?: "Gagal mengirim OTP")
            }
        }
    }

    // --- FUNGSI 2: Verifikasi OTP ke Backend & Dapat Token ---
    fun verifyOtp(phoneNumber: String, code: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = repository.verifyOtpWa(phoneNumber, code)
            if (result.isSuccess) {
                val customToken = result.getOrNull() ?: ""
                onSuccess(customToken) // Kembalikan Token ke Activity
            } else {
                onError(result.exceptionOrNull()?.message ?: "Verifikasi gagal")
            }
        }
    }

    // --- FUNGSI 3: Sync User (Logika Lama Tetap Dipakai setelah Login Firebase Sukses) ---
    fun handleLoginSuccess(
        firebaseUid: String,
        phoneNumber: String,
        onSuccess: (Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                // 1. Cek/Simpan User ke Backend (User Sync)
                val userForBackend = User(uid = firebaseUid, phoneNumber = phoneNumber)
                val result = repository.syncUserToBackend(userForBackend)

                // 2. Simpan Sesi Lokal
                repository.saveSession(UserModel("$phoneNumber@lecturo.com", firebaseUid, true))

                if (result.isSuccess) {
                    val user = result.getOrNull()
                    // Cek apakah data profil sudah lengkap
                    val isProfileComplete = !user?.fullName.isNullOrEmpty() && !user?.university.isNullOrEmpty()
                    onSuccess(isProfileComplete)
                } else {
                    // Jika gagal sync (misal backend mati tapi firebase hidup), anggap sukses login tapi error sync
                    android.util.Log.e("LOGIN_DEBUG", "Gagal Sync Backend: ${result.exceptionOrNull()?.message}")
                    // Tetap izinkan masuk, nanti di Home bisa sync ulang/retry
                    onSuccess(false)
                }
            } catch (e: Exception) {
                onError(e.message ?: "Error Sync")
            }
        }
    }
}