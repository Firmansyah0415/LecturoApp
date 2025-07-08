package com.lecturo.lecturo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.lecturo.lecturo.databinding.ActivityCameraOcrBinding
import java.util.regex.Pattern

class CameraOCRActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraOcrBinding
    private var capturedBitmap: Bitmap? = null
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            if (bitmap != null) {
                capturedBitmap = bitmap
                binding.imageView.setImageBitmap(bitmap)
                extractTextFromImage(bitmap)
            } else {
                showToast("Gagal mengambil gambar dari kamera")
            }
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                capturedBitmap = bitmap
                binding.imageView.setImageBitmap(bitmap)
                extractTextFromImage(bitmap)
            } catch (e: Exception) {
                showToast("Gagal memuat gambar")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraOcrBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Scan Jadwal" // 🟡 bisa pindah ke strings.xml
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupClickListeners()
        binding.buttonProcess.text = "Pilih Gambar Terlebih Dahulu" // 🟡
        binding.buttonProcess.isEnabled = false
    }

    private fun setupClickListeners() {
        binding.buttonCamera.setOnClickListener {
            if (hasCameraPermission()) {
                openCamera()
            } else {
                requestCameraPermission()
            }
        }

        binding.buttonGallery.setOnClickListener {
            openGallery()
        }

        binding.buttonProcess.setOnClickListener {
            confirmAndProceed()
        }
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraLauncher.launch(intent)
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    private fun extractTextFromImage(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)

        binding.textResult.text = "Memproses gambar..." // 🟡
        binding.buttonProcess.text = "Memproses..."
        binding.buttonProcess.isEnabled = false

        textRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                val extractedText = visionText.text
                binding.textResult.text = extractedText
                binding.buttonProcess.text = "Konfirmasi & Lanjutkan"
                binding.buttonProcess.isEnabled = true
            }
            .addOnFailureListener { e ->
                binding.textResult.text = "Gagal memproses gambar: ${e.message}"
                binding.buttonProcess.text = "Proses OCR"
                binding.buttonProcess.isEnabled = false
                showToast("Gagal memproses gambar: ${e.message}")
            }
    }

    private fun confirmAndProceed() {
        val extractedText = binding.textResult.text.toString()

        if (extractedText.isBlank() || extractedText.contains("Hasil OCR")) {
            showToast("Tidak ada teks yang diekstrak")
            return
        }

        val scheduleInfo = classifyScheduleText(extractedText)

        if (scheduleInfo.isNotEmpty()) {
            val intent = Intent(this, AddScheduleActivity::class.java).apply {
                putExtra("extracted_title", scheduleInfo["title"] ?: "")
                putExtra("extracted_date", scheduleInfo["date"] ?: "")
                putExtra("extracted_time", scheduleInfo["time"] ?: "")
                putExtra("extracted_location", scheduleInfo["location"] ?: "")
                putExtra("from_ocr", true)
            }
            startActivity(intent)
            finish()
        } else {
            showToast("Tidak dapat mengidentifikasi informasi jadwal dari teks ini")
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 102)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 102 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            showToast("Izin kamera diperlukan")
        }
    }

    private fun classifyScheduleText(text: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

        val datePattern = Pattern.compile(
            "\\b(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{1,2}\\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec|Januari|Februari|Maret|April|Mei|Juni|Juli|Agustus|September|Oktober|November|Desember)\\s+\\d{2,4})\\b",
            Pattern.CASE_INSENSITIVE
        )

        val timePattern = Pattern.compile("\\b\\d{1,2}[:.\\-]\\d{2}(\\s*(AM|PM|WIB|WITA|WIT))?\\b", Pattern.CASE_INSENSITIVE)

        val locationKeywords = listOf("ruang", "kelas", "lab", "aula", "gedung", "lantai", "room", "hall")

        for (line in lines) {
            val dateMatcher = datePattern.matcher(line)
            if (dateMatcher.find() && !result.containsKey("date")) {
                result["date"] = dateMatcher.group()
            }

            val timeMatcher = timePattern.matcher(line)
            if (timeMatcher.find() && !result.containsKey("time")) {
                result["time"] = timeMatcher.group()
            }

            val lowerLine = line.lowercase()
            for (keyword in locationKeywords) {
                if (lowerLine.contains(keyword) && !result.containsKey("location")) {
                    result["location"] = line
                    break
                }
            }
        }

        if (lines.isNotEmpty() && !result.containsKey("title")) {
            result["title"] = lines[0]
        }

        return result
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        textRecognizer.close()
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
