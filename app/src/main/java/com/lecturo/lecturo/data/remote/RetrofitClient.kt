package com.lecturo.lecturo.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // GANTI IP DI SINI:
    // 1. Jika pakai Emulator Android Studio: Gunakan "http://10.0.2.2:3000/"
    // 2. Jika pakai HP Fisik colok USB: Gunakan IP Laptop (misal "http://192.168.1.5:3000/")
    //    (Pastikan Laptop & HP di WiFi yang sama, dan Firewall laptop dimatikan sebentar)

    private const val BASE_URL = "http://76.13.192.59:3000/"
//    private const val BASE_URL = "http://192.168.100.129:3000/"
//    private const val BASE_URL = "http://10.0.2.2:3000/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY // Ini biar bisa lihat log request/response di Logcat
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS) // Tambah waktu tunggu jika AI lambat
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}