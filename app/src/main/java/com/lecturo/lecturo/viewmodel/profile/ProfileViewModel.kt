package com.lecturo.lecturo.viewmodel.profile

import android.net.Uri
import kotlinx.coroutines.flow.firstOrNull
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lecturo.lecturo.data.model.User
import com.lecturo.lecturo.data.pref.UserModel
import com.lecturo.lecturo.data.repository.UserRepository
import kotlinx.coroutines.launch

class ProfileViewModel(private val repository: UserRepository) : ViewModel() {

    private val _currentUser = MutableLiveData<User?>()
    val currentUser: LiveData<User?> = _currentUser

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    // 1. Load Data User (CACHE-FIRST STRATEGY)
    fun loadUserProfile() {
        _isLoading.value = true
        viewModelScope.launch {

            // A. Ambil dari DataStore Lokal (Sangat Cepat & Bisa Offline)
            val session = repository.getSession().firstOrNull()
            val uid = session?.token

            // B. TAMPILKAN CACHE LOKAL SEGERA (ANTI-KEDIP)
            if (session != null && session.name.isNotEmpty()) {
                _currentUser.value = User(
                    uid = uid ?: "",
                    phoneNumber = "", // Kosong, hanya butuh nama & foto di Beranda
                    email = session.email,
                    fullName = session.name,
                    university = "",
                    faculty = "",
                    major = "",
                    photoUrl = session.photoUrl
                )
            }

            // C. AMBIL DATA TERBARU DARI API DI LATAR BELAKANG
            if (uid != null) {
                val result = repository.getUserFromBackend(uid)

                if (result.isSuccess) {
                    val user = result.getOrNull()

                    // Hanya update UI jika data dari server BEDA dengan cache lokal
                    if (_currentUser.value != user) {
                        _currentUser.value = user
                    }

                    // D. SIMPAN/UPDATE CACHE LOKAL DENGAN DATA BARU
                    if (user != null) {
                        repository.saveSession(
                            UserModel(
                                token = uid,
                                email = user.email,
                                isLogin = true,
                                name = user.fullName,     // Simpan ke Cache
                                photoUrl = user.photoUrl  // Simpan ke Cache
                            )
                        )
                    }
                } else {
                    // JIKA OFFLINE/ERROR:
                    // Jangan timpa _currentUser dengan null! Biarkan cache tetap tampil.
                    _message.value = "Mode Offline: Menampilkan data tersimpan."
                }
            }
            _isLoading.value = false
        }
    }

    // 2. Update Data User
    fun updateProfile(
        name: String,
        email: String,
        phone: String,
        university: String,
        faculty: String,
        major: String,
        photoUri: Uri?,
        onSuccess: () -> Unit
    ) {
        _isLoading.value = true
        viewModelScope.launch {
            val currentUid = _currentUser.value?.uid ?: return@launch
            val currentPhone = _currentUser.value?.phoneNumber ?: phone

            var finalPhotoUrl = _currentUser.value?.photoUrl ?: ""

            if (photoUri != null) {
                _message.value = "Mengupload foto..."
                val uploadResult = repository.uploadProfilePhoto(currentUid, photoUri)
                if (uploadResult.isSuccess) {
                    finalPhotoUrl = uploadResult.getOrNull() ?: finalPhotoUrl
                } else {
                    _isLoading.value = false
                    _message.value = "Gagal upload foto: ${uploadResult.exceptionOrNull()?.message}"
                    return@launch
                }
            }

            _message.value = "Menyimpan data..."
            val updatedUser = User(
                uid = currentUid,
                phoneNumber = currentPhone,
                email = email,
                fullName = name,
                university = university,
                faculty = faculty,
                major = major,
                photoUrl = finalPhotoUrl
            )

            val result = repository.syncUserToBackend(updatedUser)
            _isLoading.value = false

            if (result.isSuccess) {
                _currentUser.value = result.getOrNull()

                // 🔥 UPDATE CACHE LOKAL SETELAH BERHASIL EDIT PROFIL
                repository.saveSession(
                    UserModel(
                        token = currentUid,
                        email = email,
                        isLogin = true,
                        name = name,
                        photoUrl = finalPhotoUrl
                    )
                )

                _message.value = "Profil berhasil diperbarui"
                onSuccess()
            } else {
                _message.value = "Gagal update profil: ${result.exceptionOrNull()?.message}"
            }
        }
    }
}