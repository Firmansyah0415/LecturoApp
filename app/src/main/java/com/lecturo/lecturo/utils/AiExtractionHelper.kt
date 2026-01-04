package com.lecturo.lecturo.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.lecturo.lecturo.data.model.Event
import com.lecturo.lecturo.data.repository.AiRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AiExtractionHelper(private val context: Context) {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val aiRepository = AiRepository() // Asumsi AiRepository sudah Anda buat sebelumnya

    init {
        PDFBoxResourceLoader.init(context)
    }

    // Fungsi Utama: Proses Gambar/PDF -> Teks -> AI -> Event Object
    suspend fun extractEventFromUri(uri: Uri, isPdf: Boolean): Result<Event> {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Ekstrak Teks (OCR / PDF Parsing)
                val rawText = if (isPdf) extractTextFromPdf(uri) else extractTextFromImage(uri)

                if (rawText.isBlank()) {
                    return@withContext Result.failure(Exception("Gagal membaca teks atau dokumen kosong."))
                }

                // 2. Kirim ke Gemini AI
                val aiResult = aiRepository.extractEventInfo(rawText)

                // 3. Kembalikan Hasil
                aiResult
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private suspend fun extractTextFromImage(uri: Uri): String {
        return try {
            val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = textRecognizer.process(image).await()
            result.text
        } catch (e: Exception) {
            throw Exception("Gagal OCR Gambar: ${e.message}")
        }
    }

    private fun extractTextFromPdf(uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val document = PDDocument.load(stream)
                val stripper = PDFTextStripper()
                val text = stripper.getText(document)
                document.close()
                text
            } ?: ""
        } catch (e: Exception) {
            throw Exception("Gagal Baca PDF: ${e.message}")
        }
    }
}