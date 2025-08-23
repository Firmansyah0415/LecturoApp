package com.lecturo.lecturo.ai

import com.lecturo.lecturo.BuildConfig
import com.lecturo.lecturo.data.model.Event
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Object singleton untuk mengekstrak informasi Event menggunakan Gemini AI.
 * Didesain khusus untuk model data 'Event' aplikasi Lecturo.
 */
object EventGeminiExtractor {

    // Menggunakan 'lazy' agar model hanya diinisialisasi saat pertama kali dibutuhkan.
    private val generativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = BuildConfig.GEMINI_API_KEY
        )
    }

    /**
     * Menganalisis teks mentah dan mengekstrak informasi untuk membuat objek Event.
     * @param rawText Teks yang akan dianalisis, bisa dari OCR atau PDF.
     * @return Result<Event> yang berisi objek Event jika sukses, atau Exception jika gagal.
     */
    suspend fun extractEventInfo(rawText: String): Result<Event> {
        return withContext(Dispatchers.IO) {
            try {
                // Prompt ini secara spesifik meminta data yang sesuai dengan model Event Anda.
                val prompt = """
                Analisis teks berikut untuk sebuah acara, rapat, atau jadwal akademik. 
                Ekstrak informasi berikut dan kembalikan dalam format JSON yang ketat:
                - "title": Judul acara yang jelas dan singkat.
                - "category": Pilih salah satu dari kategori berikut yang paling sesuai: "Rapat", "Seminar", "Webinar", "Workshop", "Lokakarya", "Penelitian", "Pengabdian Masyarakat". Jika tidak ada yang cocok, gunakan "Lainnya".
                - "date": Tanggal acara dalam format "dd/MM/yyyy".
                - "time": Waktu acara dalam format "HH:mm".
                - "location": Lokasi fisik atau tautan online.
                - "description": Ringkasan singkat atau detail tambahan dari acara tersebut.

                Aturan Penting:
                1. Jika sebuah informasi tidak dapat ditemukan, gunakan string kosong "".
                2. Hanya kembalikan objek JSON, tanpa teks atau markdown tambahan.

                Teks untuk dianalisis:
                "$rawText"
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                val responseText = response.text

                if (responseText.isNullOrBlank()) {
                    Result.failure(Exception("Gemini tidak memberikan respons."))
                } else {
                    val event = parseJsonResponse(responseText)
                    Result.success(event)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Mem-parsing string JSON dari respons Gemini menjadi objek Event.
     */
    private fun parseJsonResponse(jsonString: String): Event {
        // Membersihkan string dari markdown yang mungkin ditambahkan oleh AI
        val cleanJson = jsonString.replace("```json", "").replace("```", "").trim()
        val jsonObject = JSONObject(cleanJson)

        // Membuat objek Event dari data JSON
        return Event(
            title = jsonObject.optString("title"),
            category = jsonObject.optString("category"),
            date = jsonObject.optString("date"),
            time = jsonObject.optString("time"),
            location = jsonObject.optString("location"),
            description = jsonObject.optString("description")
        )
    }
}