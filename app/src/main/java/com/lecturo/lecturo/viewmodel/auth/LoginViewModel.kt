package com.lecturo.lecturo.viewmodel.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lecturo.lecturo.data.model.User
import com.lecturo.lecturo.data.pref.UserModel
import com.lecturo.lecturo.data.repository.UserRepository
import kotlinx.coroutines.launch

class LoginViewModel(private val repository: UserRepository) : ViewModel() {

    // Fungsi ini dipanggil setelah OTP Sukses di LoginActivity
    fun handleLoginSuccess(
        firebaseUid: String,
        phoneNumber: String,
        onSuccess: (Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                // 1. Siapkan data untuk Backend
                val userForBackend = User(
                    uid = firebaseUid,
                    phoneNumber = phoneNumber,
                    // Data lain biarkan default (kosong) dulu, nanti diisi di Profile
                )

                // Panggil Repository yang baru
                val result = repository.syncUserToBackend(userForBackend)

                /// Simpan Sesi (Wajib)
                repository.saveSession(UserModel("$phoneNumber@lecturo.com", firebaseUid, true))

                if (result.isSuccess) {
                    val user = result.getOrNull()
                    val isProfileComplete = !user?.fullName.isNullOrEmpty()
                    onSuccess(isProfileComplete)
                } else {
                    // --- UBAH BAGIAN INI UNTUK DEBUGGING ---

                    // Ambil pesan error asli dari Retrofit
                    val errorException = result.exceptionOrNull()
                    val errorMessage = "Gagal Konek ke Backend: ${errorException?.message}"

                    // Cetak di Logcat (Cari kata kunci "LOGIN_DEBUG" nanti)
                    android.util.Log.e("LOGIN_DEBUG", errorMessage)

                    // Tampilkan error ke layar (JANGAN onSuccess(true) DULU)
                    onError(errorMessage)
                }

            } catch (e: Exception) {
                onError(e.message ?: "Error")
            }
        }
    }

    // Fungsi lama (jika masih ada kode yang panggil)
    fun saveSession(user: UserModel) {
        viewModelScope.launch {
            repository.saveSession(user)
        }
    }
}