package com.lecturo.lecturo.ui.cameraocr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.model.Event
import com.lecturo.lecturo.databinding.ActivityCameraOcrBinding
import com.lecturo.lecturo.ui.event.AddEventActivity
import com.lecturo.lecturo.data.repository.AiRepository // <-- IMPORT BARU
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CameraOCRActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraOcrBinding
    private val textRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    // --- TAMBAHAN BARU: Inisialisasi Repository ---
    private val aiRepository = AiRepository()

    // Variabel untuk menyimpan URI dari file gambar sementara
    private var tempImageUri: Uri? = null

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) openCamera() else showToast("Izin kamera ditolak")
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            tempImageUri?.let { uri ->
                processImage(uri)
            }
        } else {
            showToast("Gagal mengambil gambar")
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { processImage(it) }
    }

    private val pdfLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { processPdf(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraOcrBinding.inflate(layoutInflater)
        setContentView(binding.root)

        PDFBoxResourceLoader.init(applicationContext)
        setupToolbar()
        setupClickListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupClickListeners() {
        binding.buttonCamera.setOnClickListener { checkCameraPermission() }
        binding.buttonGallery.setOnClickListener { galleryLauncher.launch("image/*") }
        binding.buttonPdf.setOnClickListener { pdfLauncher.launch("application/pdf") }
        binding.buttonProcess.setOnClickListener { processTextWithAI() }
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> openCamera()
            else -> requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openCamera() {
        try {
            val uri = createImageUri()
            tempImageUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            showToast("Gagal mempersiapkan kamera: ${e.message}")
        }
    }

    private fun createImageUri(): Uri {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFile = File.createTempFile("JPEG_${timeStamp}_", ".jpg", externalCacheDir)
        return FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            imageFile
        )
    }

    // --- LOGIKA PEMROSESAN DATA ---

    private fun processImage(bitmap: Bitmap) {
        binding.imageView.setImageBitmap(bitmap)
        setLoadingState(true, "Membaca teks dari gambar...")
        val image = InputImage.fromBitmap(bitmap, 0)
        textRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                binding.textResult.setText(visionText.text)
                setLoadingState(false, "Proses dengan AI")
            }
            .addOnFailureListener { e ->
                showToast("Gagal membaca teks: ${e.message}")
                setLoadingState(false, "Pilih Sumber Lain", isError = true)
            }
    }

    private fun processImage(uri: Uri) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            processImage(bitmap)
        } catch (e: Exception) {
            showToast("Gagal memuat gambar")
        }
    }

    private fun processPdf(uri: Uri) {
        binding.imageView.setImageResource(R.drawable.ic_pdf)
        setLoadingState(true, "Membaca teks dari PDF...")
        lifecycleScope.launch {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val document = PDDocument.load(stream)
                    val stripper = PDFTextStripper()
                    binding.textResult.setText(stripper.getText(document))
                    document.close()
                    setLoadingState(false, "Proses dengan AI")
                }
            } catch (e: Exception) {
                showToast("Gagal memproses PDF: ${e.message}")
                setLoadingState(false, "Pilih Sumber Lain", isError = true)
            }
        }
    }

    // --- PERBAIKAN UTAMA DI SINI ---
    private fun processTextWithAI() {
        val rawText = binding.textResult.text.toString().trim()
        if (rawText.isBlank()) {
            showToast("Tidak ada teks untuk diproses")
            return
        }
        setLoadingState(true, "Memproses dengan AI...")

        lifecycleScope.launch {
            // MENGGUNAKAN REPOSITORY, BUKAN EVENTGEMINIEXTRACTOR LAGI
            val result = aiRepository.extractEventInfo(rawText)

            result.onSuccess { event ->
                navigateToForm(event)
            }.onFailure { error ->
                showToast("Gagal memproses AI: ${error.message}")
                setLoadingState(false, "Coba Lagi", isError = true)
            }
        }
    }

    private fun navigateToForm(event: Event) {
        val intent = Intent(this, AddEventActivity::class.java).apply {
            putExtra("EXTRA_EVENT_AI", event)
        }
        startActivity(intent)
        finish()
    }

    // --- FUNGSI BANTUAN ---

    private fun setLoadingState(isLoading: Boolean, buttonText: String, isError: Boolean = false) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.buttonProcess.text = buttonText
        binding.buttonProcess.isEnabled = !isLoading || isError
        binding.buttonCamera.isEnabled = !isLoading
        binding.buttonGallery.isEnabled = !isLoading
        binding.buttonPdf.isEnabled = !isLoading
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        textRecognizer.close()
    }
}