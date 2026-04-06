package com.lecturo.lecturo.utils

import android.content.Context
import android.util.Log
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.model.FocusSession
import com.lecturo.lecturo.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

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
                    db.teachingRuleDao().deleteAll()

                    if (rules.isNotEmpty()) {
                        // --- PERBAIKAN: Set isSynced = true & isDeleted = false ---
                        val rulesWithStatus = rules.map {
                            it.copy(
                                userId = uid,
                                isSynced = true,   // <--- INI PENTING
                                isDeleted = false  // <--- INI PENTING
                            )
                        }
                        db.teachingRuleDao().insertAll(rulesWithStatus)
                    }
                }

                // -----------------------------------------------------------
                // 2. Restore Tasks
                // -----------------------------------------------------------
                val tasksResponse = api.getAllTasks(uid)
                if (tasksResponse.isSuccessful && tasksResponse.body()?.status == "success") {
                    val tasks = tasksResponse.body()?.data ?: emptyList()
                    db.tasksDao().deleteAll()

                    if (tasks.isNotEmpty()) {
                        // --- PERBAIKAN: Set isSynced = true (Kamu sudah buat, kita lengkapi) ---
                        val tasksWithStatus = tasks.map {
                            it.copy(
                                userId = uid,
                                isSynced = true,
                                isDeleted = false // (Tambahkan jika Task entity kamu sudah punya field ini)
                            )
                        }
                        db.tasksDao().insertAll(tasksWithStatus)
                    }
                }

                // -----------------------------------------------------------
                // 3. Restore Events
                // -----------------------------------------------------------
                val eventsResponse = api.getAllEvents(uid)
                if (eventsResponse.isSuccessful && eventsResponse.body()?.status == "success") {
                    val events = eventsResponse.body()?.data ?: emptyList()
                    db.eventDao().deleteAllEvents()

                    if (events.isNotEmpty()) {
                        // --- SUDAH DIAKTIFKAN ---
                        val eventsWithStatus = events.map {
                            it.copy(
                                userId = uid,
                                isSynced = true,   // <--- AKTIFKAN INI
                                isDeleted = false  // <--- AKTIFKAN INI
                            )
                        }
                        db.eventDao().insertAll(eventsWithStatus)
                    }
                }

                // -----------------------------------------------------------
                // 4. Restore Consultation Schedules
                // -----------------------------------------------------------
                val consultResponse = api.getConsultations(uid)
                if (consultResponse.isSuccessful && consultResponse.body()?.status == "success") {
                    val schedules = consultResponse.body()?.data ?: emptyList()
                    db.consultationDao().deleteAllSchedules()

                    if (schedules.isNotEmpty()) {
                        val schedulesWithStatus = schedules.map {
                            it.copy(
                                userId = uid,
                                isSynced = true,
                                isDeleted = false
                            )
                        }
                        db.consultationDao().insertSchedules(schedulesWithStatus)
                    }
                }

                // -----------------------------------------------------------
                // 5. Restore Consultation Patterns
                // -----------------------------------------------------------
                val patternResponse = api.getPatterns(uid)
                if (patternResponse.isSuccessful && patternResponse.body()?.status == "success") {
                    val patterns = patternResponse.body()?.data ?: emptyList()
                    db.consultationDao().deleteAllPatterns()

                    if (patterns.isNotEmpty()) {
                        val patternsWithStatus = patterns.map {
                            it.copy(
                                userId = uid,
                                isSynced = true,
                                isDeleted = false
                            )
                        }
                        db.consultationDao().insertPatterns(patternsWithStatus)
                    }
                }

                // -----------------------------------------------------------
                // 6. Restore Focus Sessions (Pomodoro)
                // -----------------------------------------------------------
                val focusResponse = api.getAllFocusSessions(uid)
                if (focusResponse.isSuccessful && focusResponse.body()?.status == "success") {
                    val cloudSessions = focusResponse.body()?.data ?: emptyList()
                    db.focusSessionDao().deleteAllSessions()

                    if (cloudSessions.isNotEmpty()) {
                        val mappedSessions = mutableListOf<FocusSession>()
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                        for (req in cloudSessions) {
                            val startTimeLong = try { dateFormat.parse(req.startTime)?.time ?: 0L } catch (e: Exception) { 0L }
                            val endTimeLong = try { dateFormat.parse(req.endTime)?.time ?: 0L } catch (e: Exception) { 0L }

                            if (req.taskFirestoreId != null) {
                                val relatedTask = db.tasksDao().getTaskByFirestoreId(req.taskFirestoreId)

                                if (relatedTask != null) {
                                    val session = FocusSession(
                                        firestoreId = req.sessionId,
                                        userId = uid,
                                        taskId = relatedTask.id,
                                        taskFirestoreId = req.taskFirestoreId,
                                        startTime = startTimeLong,
                                        endTime = endTimeLong,
                                        durationMinutes = req.duration,
                                        status = req.status
                                    )
                                    mappedSessions.add(session)
                                }
                            }
                        }

                        if (mappedSessions.isNotEmpty()) {
                            db.focusSessionDao().insertAll(mappedSessions)
                            Log.d("DataRestore", "Berhasil restore ${mappedSessions.size} sesi fokus.")
                        }
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