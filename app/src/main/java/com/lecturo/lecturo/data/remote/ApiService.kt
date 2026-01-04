package com.lecturo.lecturo.data.remote

import com.lecturo.lecturo.data.model.ApiResponse
import com.lecturo.lecturo.data.model.Event
import com.lecturo.lecturo.data.model.EventRequest
import com.lecturo.lecturo.data.model.ExtractEventRequest
import com.lecturo.lecturo.data.model.ExtractEventResponse
import com.lecturo.lecturo.data.model.TaskRequest
import com.lecturo.lecturo.data.model.Tasks
import com.lecturo.lecturo.data.model.TeachingRequest
import com.lecturo.lecturo.data.model.TeachingRule
import com.lecturo.lecturo.data.model.User // <-- Pastikan ini ter-import
import com.lecturo.lecturo.data.model.UserResponse
import com.lecturo.lecturo.utils.Constants
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {

    // --- FITUR AI (EVENT) ---
    @POST(Constants.ENDPOINT_EXTRACT_EVENT)
    suspend fun extractEvent(@Body request: ExtractEventRequest): Response<ExtractEventResponse>

    // --- FITUR USER (LOGIN/SYNC) ---
    @POST("api/users/sync") // Pastikan rute ini sama persis dengan backend routes/userRoutes.js
    suspend fun syncUser(@Body user: User): Response<ApiResponse<User>>

    @GET("api/users/{uid}")
    suspend fun getUser(@Path("uid") uid: String): Response<UserResponse>

    // 1. Simpan/Update Jadwal
    // Map<String, Any> digunakan karena response backend berisi data campuran (string & object)
    @POST("api/teachings/sync")
    suspend fun syncTeaching(@Body request: TeachingRequest): Response<ApiResponse<Map<String, Any>>>

    // Ambil semua Teaching
    @GET("api/teachings/{uid}")
    suspend fun getAllTeachingRules(@Path("uid") uid: String): Response<ApiResponse<List<TeachingRule>>>

    // 2. Hapus Jadwal
    @DELETE("api/teachings/{uid}/{scheduleId}")
    suspend fun deleteTeaching(
        @Path("uid") uid: String,
        @Path("scheduleId") scheduleId: String
    ): Response<ApiResponse<Any>>

    // --- FITUR EVENT ---
    @POST("api/events/sync")
    suspend fun syncEvent(@Body request: EventRequest): Response<ApiResponse<Map<String, Any>>>

    // Ambil semua Events
    @GET("api/events/{uid}") // Pastikan backend punya route ini
    suspend fun getAllEvents(@Path("uid") uid: String): Response<ApiResponse<List<Event>>>

    @DELETE("api/events/{uid}/{eventId}")
    suspend fun deleteEvent(
        @Path("uid") uid: String,
        @Path("eventId") eventId: String
    ): Response<ApiResponse<Any>>

    // --- FITUR TASK ---
    @POST("api/tasks/sync")
    suspend fun syncTask(@Body request: TaskRequest): Response<ApiResponse<Map<String, Any>>>

    // Ambil semua Tasks
    @GET("api/tasks/{uid}") // Pastikan backend punya route ini
    suspend fun getAllTasks(@Path("uid") uid: String): Response<ApiResponse<List<Tasks>>>

    @DELETE("api/tasks/{uid}/{taskId}")
    suspend fun deleteTask(
        @Path("uid") uid: String,
        @Path("taskId") taskId: String
    ): Response<ApiResponse<Any>>

}