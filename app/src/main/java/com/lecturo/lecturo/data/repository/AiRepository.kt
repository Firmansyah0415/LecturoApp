package com.lecturo.lecturo.data.repository

import android.util.Log
import com.lecturo.lecturo.data.model.Event
import com.lecturo.lecturo.data.model.ExtractEventRequest
import com.lecturo.lecturo.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiRepository {
    private val TAG = "EventExtractor"

    suspend fun extractEventInfo(rawText: String): Result<Event> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Mengirim teks ke backend: $rawText")

                // 1. Siapkan data request
                val request = ExtractEventRequest(text = rawText)

                // 2. Panggil Backend
                val response = RetrofitClient.instance.extractEvent(request)

                // 3. Cek hasil
                if (response.isSuccessful && response.body() != null) {
                    val apiResponse = response.body()!!

                    Log.d(TAG, "Sukses! Data diterima: ${apiResponse.data}")

                    // PERBAIKAN DI SINI:
                    // Cek dulu apakah 'data' tidak null sebelum memanggil 'toEvent()'
                    if (apiResponse.data != null) {
                        val event = apiResponse.data.toEvent()
                        Result.success(event)
                    } else {
                        // Jika backend sukses tapi datanya kosong/null
                        Result.failure(Exception("Data respon dari AI kosong"))
                    }

                } else {
                    // Tangani error dari server (misal 404, 500)
                    val errorMsg = "Gagal: ${response.code()} ${response.message()}"
                    Log.e(TAG, errorMsg)
                    Result.failure(Exception(errorMsg))
                }

            } catch (e: Exception) {
                // Tangani error koneksi (misal server mati)
                Log.e(TAG, "Error koneksi", e)
                Result.failure(e)
            }
        }
    }
}