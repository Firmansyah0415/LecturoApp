package com.lecturo.lecturo.data.repository

import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.lecturo.lecturo.data.model.User
import com.lecturo.lecturo.data.pref.UserModel
import com.lecturo.lecturo.data.pref.UserPreference
import com.lecturo.lecturo.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class UserRepository(
    private val userPreference: UserPreference
) {

    private val api = RetrofitClient.instance

    // 1. SYNC (Simpan/Update Data)
    suspend fun syncUserToBackend(user: User): Result<User?> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("UserRepository", "Syncing user: ${user.phoneNumber}")
                val response = api.syncUser(user)

                if (response.isSuccessful && response.body()?.status == "success") {
                    val userData = response.body()?.data
                    Log.d("UserRepository", "Sync Success! Name: ${userData?.fullName}")
                    Result.success(userData)
                } else {
                    val errorMsg = "Gagal Sync: ${response.code()} ${response.message()}"
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // 2. GET USER (Ambil Data Terbaru) - [INI YANG KITA TAMBAHKAN]
    suspend fun getUserFromBackend(uid: String): Result<User?> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("UserRepository", "Fetching user: $uid")
                // Panggil endpoint GET yang baru dibuat di ApiService
                val response = api.getUser(uid)

                if (response.isSuccessful && response.body()?.status == "success") {
                    val userData = response.body()?.data
                    Log.d("UserRepository", "Fetch Success! Name: ${userData?.fullName}")
                    Result.success(userData)
                } else {
                    val errorMsg = "Gagal Fetch: ${response.code()} ${response.message()}"
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Log.e("UserRepository", "Fetch Error", e)
                Result.failure(e)
            }
        }
    }

    suspend fun uploadProfilePhoto(uid: String, imageUri: Uri): Result<String> {
        return try {
            // 1. Buat Referensi Folder di Cloud Storage
            // Format: profile_images/UID_USER.jpg
            // Kita pakai UID agar foto lama otomatis tertimpa (hemat storage)
            val storageRef = FirebaseStorage.getInstance().reference
                .child("profile_images")
                .child("$uid.jpg")

            // 2. Upload File
            storageRef.putFile(imageUri).await()

            // 3. Ambil URL Download (Public URL)
            val downloadUrl = storageRef.downloadUrl.await()

            // 4. Kembalikan URL sebagai String
            Result.success(downloadUrl.toString())

        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    fun isFirstTime(): Flow<Boolean> {
        return userPreference.isFirstTime()
    }

    suspend fun setNotFirstTime() {
        userPreference.setNotFirstTime()
    }

    fun getSession(): Flow<UserModel> {
        return userPreference.getSession()
    }

    suspend fun saveSession(user: UserModel) {
        userPreference.saveSession(user)
    }

    suspend fun logout() {
        userPreference.logout()
        FirebaseAuth.getInstance().signOut()
    }

    companion object {
        @Volatile
        private var instance: UserRepository? = null

        fun getInstance(userPreference: UserPreference): UserRepository =
            instance ?: synchronized(this) {
                instance ?: UserRepository(userPreference).also { instance = it }
            }
    }
}