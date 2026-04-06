package com.lecturo.lecturo.ui.event

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.core.view.updateLayoutParams
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.model.Event
import com.lecturo.lecturo.databinding.ActivityEventBinding
import com.lecturo.lecturo.di.ViewModelFactory
import com.lecturo.lecturo.utils.AiExtractionHelper
import com.lecturo.lecturo.viewmodel.event.EventViewModel
import kotlinx.coroutines.launch
import java.io.File

class EventActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEventBinding
    private lateinit var eventAdapter: EventAdapter
    private lateinit var aiHelper: AiExtractionHelper // Helper AI
    private lateinit var loadingDialog: AlertDialog // Dialog Loading

    private var tempImageUri: Uri? = null // Uri untuk Kamera

    private val viewModel: EventViewModel by viewModels {
        ViewModelFactory.getInstance(this)
    }

    // --- LAUNCHERS (PERBAIKAN TYPE MISMATCH DI SINI) ---

    // 1. Launcher Kamera
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            // Kita harus memastikan tempImageUri tidak null sebelum memproses
            tempImageUri?.let { uri ->
                processSelection(uri, isPdf = false)
            } ?: run {
                Toast.makeText(this, "Gagal mengambil gambar (Uri Kosong)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 2. Launcher Galeri
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        // uri di sini bisa null (Uri?), jadi wajib pakai ?.let
        uri?.let { safeUri ->
            processSelection(safeUri, isPdf = false)
        }
    }

    // 3. Launcher PDF
    private val pdfLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        // uri di sini bisa null (Uri?), jadi wajib pakai ?.let
        uri?.let { safeUri ->
            processSelection(safeUri, isPdf = true)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) openCamera() else Toast.makeText(this, "Izin kamera ditolak", Toast.LENGTH_SHORT).show()
    }

    private val addEventLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Data akan refresh otomatis melalui LiveData
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup AI Helper & Loading Dialog
        aiHelper = AiExtractionHelper(this)
        setupLoadingDialog()

        setupStatusBar()
        setupToolbar()
        setupRecyclerView()
        setupSearchView()
        setupFab()
        observeViewModel()
        setupFilterChips()
    }

    private fun setupStatusBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = getColor(R.color.colorPrimary)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(view.paddingLeft, statusBarInsets.top, view.paddingRight, view.paddingBottom)
            insets
        }
    }

    private fun setupFab() {
        binding.fabAddEvent.setOnClickListener {
            startActivity(Intent(this, AddEventActivity::class.java))
        }
        // FAB AI Click -> Buka Dialog Sumber
        binding.fabAi.setOnClickListener {
            showSourceSelectionDialog()
        }

        // --- [SOLUSI PRO: Mendorong KEDUA FAB ke atas Navigasi Sistem] ---

        // 1. Insets untuk FAB Add (Tombol Bawah)
        ViewCompat.setOnApplyWindowInsetsListener(binding.fabAddEvent) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val baseMarginBottomPx = (20 * resources.displayMetrics.density).toInt()
            val baseMarginEndPx = (20 * resources.displayMetrics.density).toInt()

            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = systemBars.bottom + baseMarginBottomPx
                rightMargin = baseMarginEndPx
            }
            insets
        }

        // 2. Insets untuk FAB AI (Tombol Atas)
        ViewCompat.setOnApplyWindowInsetsListener(binding.fabAi) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Margin bawah 90dp agar selalu memiliki jarak aman di atas FAB Add
            val baseMarginBottomPx = (90 * resources.displayMetrics.density).toInt()
            val baseMarginEndPx = (26 * resources.displayMetrics.density).toInt()

            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = systemBars.bottom + baseMarginBottomPx
                rightMargin = baseMarginEndPx
            }
            insets
        }
    }

    private fun showSourceSelectionDialog() {
        val options = arrayOf("Dokumen PDF", "Galeri Gambar", "Kamera")
        MaterialAlertDialogBuilder(this)
            .setTitle("Pilih Sumber Jadwal")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pdfLauncher.launch("application/pdf")
                    1 -> galleryLauncher.launch("image/*")
                    2 -> checkCameraPermission()
                }
            }
            .show()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openCamera() {
        try {
            val tmpFile = File.createTempFile("JPEG_${System.currentTimeMillis()}_", ".jpg", externalCacheDir)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", tmpFile)
            tempImageUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(this, "Error kamera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // --- LOGIKA PROSES AI ---
    // Fungsi ini mewajibkan 'uri' yang TIDAK NULL (Uri, bukan Uri?)
    private fun processSelection(uri: Uri, isPdf: Boolean) {
        showLoading(true)

        lifecycleScope.launch {
            // Panggil Helper
            val result = aiHelper.extractEventFromUri(uri, isPdf)

            showLoading(false)

            result.onSuccess { event ->
                val intent = Intent(this@EventActivity, AddEventActivity::class.java).apply {
                    putExtra("EXTRA_EVENT_AI", event)
                }
                startActivity(intent)
            }.onFailure { error ->
                Toast.makeText(this@EventActivity, "Gagal memproses: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupLoadingDialog() {
        val builder = MaterialAlertDialogBuilder(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_loading_ai, null)
        builder.setView(view)
        builder.setCancelable(false)
        loadingDialog = builder.create()
    }

    private fun showLoading(show: Boolean) {
        if (show) loadingDialog.show() else loadingDialog.dismiss()
    }

    // --- SETUP UI STANDAR ---
    private fun setupToolbar() {
        setSupportActionBar(binding.eventToolbar)
        supportActionBar?.title = "Event Management"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupRecyclerView() {
        eventAdapter = EventAdapter(
            onCompletedChanged = { event, isCompleted ->
                viewModel.updateCompletedStatus(event.id, isCompleted)
            },
            onDeleteClick = { event ->
                showDeleteConfirmation(event)
            },
            onItemClick = { event ->
                val intent = Intent(this, AddEventActivity::class.java)
                intent.putExtra("event_id", event.id)
                addEventLauncher.launch(intent)
            }
        )

        binding.recyclerViewEvents.apply {
            layoutManager = LinearLayoutManager(this@EventActivity)
            adapter = eventAdapter
        }
    }

    // EventActivity.kt dan ConsultationActivity.kt

    private fun setupSearchView() {
        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                viewModel.setSearchQuery(s?.toString() ?: "")
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun observeViewModel() {
        viewModel.filteredEvents.observe(this) { events ->
            eventAdapter.submitList(events)
            updateEmptyState(events.isEmpty())
        }

        viewModel.categories.observe(this) { categories ->
            setupCategoryChips(categories)
        }
    }

    private fun setupFilterChips() {
        binding.chipAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                viewModel.setCategoryFilter("")
            }
        }
    }

    private fun setupCategoryChips(categories: List<String>) {
        val chipGroup = binding.chipGroupFilters
        val childCount = chipGroup.childCount
        for (i in childCount - 1 downTo 1) {
            chipGroup.removeViewAt(i)
        }

        categories.forEach { category ->
            val chip = Chip(this)
            chip.text = category
            chip.isCheckable = true
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    binding.chipAll.isChecked = false
                    viewModel.setCategoryFilter(category)
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.layoutEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerViewEvents.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun showDeleteConfirmation(event: Event) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Hapus Event")
            .setMessage("Apakah Anda yakin ingin menghapus event \"${event.title}\"?")
            .setPositiveButton("Hapus") { _, _ ->
                viewModel.delete(event.id)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.event_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_clear_filters -> {
                viewModel.clearFilters()
                binding.etSearch.text?.clear()
                binding.chipAll.isChecked = true
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
