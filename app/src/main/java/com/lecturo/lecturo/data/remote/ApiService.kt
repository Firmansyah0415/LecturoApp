package com.lecturo.lecturo.data.remote

import com.lecturo.lecturo.data.model.ApiResponse
import com.lecturo.lecturo.data.model.ConsultationPattern
import com.lecturo.lecturo.data.model.ConsultationPatternRequest
import com.lecturo.lecturo.data.model.ConsultationRequest
import com.lecturo.lecturo.data.model.ConsultationSchedule
import com.lecturo.lecturo.data.model.Event
import com.lecturo.lecturo.data.model.EventRequest
import com.lecturo.lecturo.data.model.ExtractEventRequest
import com.lecturo.lecturo.data.model.ExtractEventResponse
import com.lecturo.lecturo.data.model.FocusSessionRequest
import com.lecturo.lecturo.data.model.OtpRequest
import com.lecturo.lecturo.data.model.OtpResponse
import com.lecturo.lecturo.data.model.TaskRequest
import com.lecturo.lecturo.data.model.Tasks
import com.lecturo.lecturo.data.model.TeachingRequest
import com.lecturo.lecturo.data.model.TeachingSchedule
import com.lecturo.lecturo.data.model.User // <-- Pastikan ini ter-import
import com.lecturo.lecturo.data.model.UserResponse
import com.lecturo.lecturo.data.model.VerifyOtpRequest
import com.lecturo.lecturo.data.model.VerifyOtpResponse
import com.lecturo.lecturo.utils.Constants
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {

    // 1. Request OTP via WhatsApp
    @POST("api/auth/request-otp")
    suspend fun requestOtp(@Body request: OtpRequest): Response<OtpResponse>

    // 2. Verifikasi OTP & Dapat Custom Token
    @POST("api/auth/verify-otp")
    suspend fun verifyOtp(@Body request: VerifyOtpRequest): Response<VerifyOtpResponse>

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
    suspend fun getAllTeachingRules(@Path("uid") uid: String): Response<ApiResponse<List<TeachingSchedule>>>

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

    // ================= KONSULTASI (SCHEDULE) =================

    // 1. Sync (Insert/Update)
    @POST("api/consultation/sync")
    suspend fun syncConsultation(@Body request: ConsultationRequest): Response<ApiResponse<ConsultationSchedule>>

    // 2. Get All
    @GET("api/consultation/{uid}")
    suspend fun getConsultations(@Path("uid") uid: String): Response<ApiResponse<List<ConsultationSchedule>>>

    // 3. Delete
    @DELETE("api/consultation/{uid}/{id}")
    suspend fun deleteConsultation(@Path("uid") uid: String, @Path("id") id: String): Response<ApiResponse<Any>>

    // ================= POLA (PATTERN) =================

    // 1. Sync Pattern
    @POST("api/consultation-pattern/sync")
    suspend fun syncPattern(@Body request: ConsultationPatternRequest): Response<ApiResponse<ConsultationPattern>>

    // 2. Get All Patterns
    @GET("api/consultation-pattern/{uid}")
    suspend fun getPatterns(@Path("uid") uid: String): Response<ApiResponse<List<ConsultationPattern>>>

    // 3. Delete Pattern
    @DELETE("api/consultation-pattern/{uid}/{id}")
    suspend fun deletePattern(@Path("uid") uid: String, @Path("id") id: String): Response<ApiResponse<Any>>

    // --- FITUR POMODORO (FOCUS SESSION) ---

    @POST("api/focus/sync")
    suspend fun syncFocusSession(@Body request: FocusSessionRequest): Response<ApiResponse<Map<String, Any>>>
    // Catatan: Map<String, Any> karena kita mau ambil 'firestoreId' dari dalam objek 'data'

    @GET("api/focus/{uid}")
    suspend fun getAllFocusSessions(@Path("uid") uid: String): Response<ApiResponse<List<FocusSessionRequest>>>
    // Catatan: Saat restore, format JSON response mungkin perlu dimapping manual di Repository jika beda dengan Entity

    @DELETE("api/focus/{uid}/{sessionId}")
    suspend fun deleteFocusSession(
        @Path("uid") uid: String,
        @Path("sessionId") sessionId: String
    ): Response<ApiResponse<Any>>

}