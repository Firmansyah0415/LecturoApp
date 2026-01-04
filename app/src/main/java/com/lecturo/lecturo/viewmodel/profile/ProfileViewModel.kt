package com.lecturo.lecturo.viewmodel.profile

import android.net.Uri
import kotlinx.coroutines.flow.firstOrNull
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lecturo.lecturo.data.model.User
import com.lecturo.lecturo.data.repository.UserRepository
import kotlinx.coroutines.launch

class ProfileViewModel(private val repository: UserRepository) : ViewModel() {

    // LiveData untuk menampung data user yang sedang ditampilkan
    private val _currentUser = MutableLiveData<User?>()
    val currentUser: LiveData<User?> = _currentUser

    // LiveData untuk status Loading
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // LiveData untuk pesan Error/Sukses (Single Event)
    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    // 1. Load Data User (Dari Database/Backend)
    fun loadUserProfile() {
        _isLoading.value = true
        viewModelScope.launch {

            // A. Ambil dari Session Lokal (Cepat)
            val session = repository.getSession()
            val uid = session.firstOrNull()?.token

            if (uid != null) {
                val result = repository.getUserFromBackend(uid)

                if (result.isSuccess) {
                    _currentUser.value = result.getOrNull()
                } else {
                    _message.value = "Gagal memuat profil: ${result.exceptionOrNull()?.message}"
                }
            }
            _isLoading.value = false
        }
    }

    // 2. Update Data User
    fun updateProfile(
        name: String,
        email: String,
        phone: String, // Note: Phone biasanya ReadOnly karena ID, tapi kita siapkan saja
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

            // Default: Gunakan foto lama
            var finalPhotoUrl = _currentUser.value?.photoUrl ?: ""

            // Jika User memilih foto baru (photoUri tidak null), Upload dulu!
            if (photoUri != null) {
                // Beri tahu user sedang upload (Opsional: bisa update pesan loading)
                _message.value = "Mengupload foto..."

                val uploadResult = repository.uploadProfilePhoto(currentUid, photoUri)

                if (uploadResult.isSuccess) {
                    // Jika sukses, ganti URL lama dengan URL baru dari Firebase Storage
                    finalPhotoUrl = uploadResult.getOrNull() ?: finalPhotoUrl
                } else {
                    // Jika gagal upload, stop proses dan beri pesan error
                    _isLoading.value = false
                    _message.value = "Gagal upload foto: ${uploadResult.exceptionOrNull()?.message}"
                    return@launch
                }
            }

            // --- LANJUT SIMPAN DATA PROFIL ---
            _message.value = "Menyimpan data..."

            val updatedUser = User(
                uid = currentUid,
                phoneNumber = currentPhone,
                email = email,
                fullName = name,
                university = university,
                faculty = faculty,
                major = major,
                photoUrl = finalPhotoUrl // Gunakan URL hasil upload (atau url lama)
            )

            val result = repository.syncUserToBackend(updatedUser)
            _isLoading.value = false

            if (result.isSuccess) {
                _currentUser.value = result.getOrNull()
                _message.value = "Profil berhasil diperbarui"
                onSuccess()
            } else {
                _message.value = "Gagal update profil: ${result.exceptionOrNull()?.message}"
            }
        }
    }
}