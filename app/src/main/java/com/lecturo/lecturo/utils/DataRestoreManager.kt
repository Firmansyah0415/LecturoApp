package com.lecturo.lecturo.utils

import android.content.Context
import android.util.Log
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DataRestoreManager(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val api = RetrofitClient.instance

    suspend fun restoreUserData(uid: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                // -----------------------------------------------------------
                // 1. Restore Teaching Rules
                // -----------------------------------------------------------
                val teachingResponse = api.getAllTeachingRules(uid)
                if (teachingResponse.isSuccessful && teachingResponse.body()?.status == "success") {
                    val rules = teachingResponse.body()?.data ?: emptyList()

                    db.teachingRuleDao().deleteAll() // Hapus data lama

                    rules.forEach { rule ->
                        // [INJEKSI MANUAL]
                        // Kita gunakan .copy() untuk mengisi userId dengan UID dari parameter fungsi
                        // Ini memaksa data di Room memiliki pemilik yang jelas.
                        val ruleWithUid = rule.copy(userId = uid)

                        db.teachingRuleDao().insertOrUpdateRule(ruleWithUid)
                    }
                }

                // -----------------------------------------------------------
                // 2. Restore Tasks
                // -----------------------------------------------------------
                val tasksResponse = api.getAllTasks(uid)
                if (tasksResponse.isSuccessful && tasksResponse.body()?.status == "success") {
                    val tasks = tasksResponse.body()?.data ?: emptyList()

                    db.tasksDao().deleteAll()

                    tasks.forEach { task ->
                        // [INJEKSI MANUAL]
                        // Pastikan Task.kt punya field userId agar ini tidak error
                        val taskWithUid = task.copy(userId = uid)

                        db.tasksDao().insertOrUpdate(taskWithUid)
                    }
                }

                // -----------------------------------------------------------
                // 3. Restore Events
                // -----------------------------------------------------------
                val eventsResponse = api.getAllEvents(uid)
                if (eventsResponse.isSuccessful && eventsResponse.body()?.status == "success") {
                    val events = eventsResponse.body()?.data ?: emptyList()

                    db.eventDao().deleteAllEvents()

                    events.forEach { event ->
                        // [INJEKSI MANUAL]
                        // Pastikan Event.kt punya field userId (atau user_id)
                        // Jika Event belum punya field userId, kamu harus menambahkannya di Model Event dulu.
                        // Jika Event.kt belum ada field userId, hapus baris .copy() di bawah ini sementara.
                        val eventWithUid = event.copy(userId = uid)

                        db.eventDao().insertOrUpdate(eventWithUid)
                    }
                }

                Result.success(true)
            } catch (e: Exception) {
                Log.e("DataRestore", "Gagal Restore: ${e.message}")
                Result.failure(e)
            }
        }
    }
}