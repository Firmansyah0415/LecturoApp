package com.lecturo.lecturo.data.repository

import android.util.Log
import androidx.lifecycle.LiveData
import com.google.firebase.auth.FirebaseAuth
import com.lecturo.lecturo.data.db.dao.EventDao
import com.lecturo.lecturo.data.model.Event
import com.lecturo.lecturo.data.model.EventRequest // Pastikan import model request
import com.lecturo.lecturo.data.remote.ApiService
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class EventRepository(
    private val eventDao: EventDao,
    private val apiService: ApiService, // TAMBAHAN: Inject ApiService
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    fun getAllEvents(): LiveData<List<Event>> {
        return eventDao.getAllEvents()
    }

    // --- FUNGSI SIMPAN (UPDATE) ---
    suspend fun insertOrUpdate(event: Event): Long {
        // 1. Simpan Lokal (Room) - Agar Offline tetap jalan
        val localId = eventDao.insertOrUpdate(event)

        // 2. Simpan Cloud (Node.js -> Firestore)
        val userId = auth.currentUser?.uid
        if (userId != null) {
            withContext(NonCancellable) {
                try {
                    // Mapping Event (Lokal) ke EventRequest (Network)
                    val request = EventRequest(
                        uid = userId,
                        eventId = event.firestoreId, // Null jika baru
                        title = event.title,
                        category = event.category,
                        priority = event.priority ?: "Sedang",
                        inputSource = event.inputSource ?: "MANUAL",
                        date = event.date,
                        time = event.time,
                        location = event.location ?: "-",
                        description = event.description ?: "",
                        isCompleted = event.isCompleted,
                        notificationMinutes = event.notificationMinutesBefore
                    )

                    val response = apiService.syncEvent(request)

                    if (response.isSuccessful && response.body()?.status == "success") {
                        val newFirestoreId = response.body()?.data?.get("firestore_id") as? String

                        // Jika dapat ID baru dari cloud, update data lokal
                        if (newFirestoreId != null && event.firestoreId != newFirestoreId) {
                            val updatedEvent = event.copy(id = localId, firestoreId = newFirestoreId)
                            eventDao.insertOrUpdate(updatedEvent)
                            Log.d("EventRepo", "Sync Sukses. ID Cloud: $newFirestoreId")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("EventRepo", "Gagal Sync (Mungkin Offline): ${e.message}")
                }
            }
        }

        return localId
    }

    // --- FUNGSI HAPUS ---
    suspend fun deleteById(eventId: Long) {
        // Ambil data dulu untuk cek firestoreId
        val event = eventDao.getEventById(eventId)

        // 1. Hapus Lokal
        eventDao.deleteById(eventId)

        // 2. Hapus Cloud
        if (event?.firestoreId != null && auth.currentUser != null) {
            withContext(NonCancellable) {
                try {
                    apiService.deleteEvent(auth.currentUser!!.uid, event.firestoreId!!)
                    Log.d("EventRepo", "Hapus Cloud Sukses")
                } catch (e: Exception) {
                    Log.e("EventRepo", "Gagal Hapus Cloud: ${e.message}")
                }
            }
        }
    }

    // --- FUNGSI LAINNYA (TIDAK BERUBAH) ---
    suspend fun updateCompletedStatus(eventId: Long, isCompleted: Boolean) {
        eventDao.updateCompletedStatus(eventId, isCompleted)
        // Opsional: Jika ingin sync status completed ke backend, bisa tambahkan logika di sini nanti
    }

    suspend fun getEventById(eventId: Long): Event? {
        return eventDao.getEventById(eventId)
    }

    fun getEventsByCategory(category: String): LiveData<List<Event>> {
        return eventDao.getEventsByCategory(category)
    }

    fun getAllCategories(): LiveData<List<String>> {
        return eventDao.getAllCategories()
    }
}