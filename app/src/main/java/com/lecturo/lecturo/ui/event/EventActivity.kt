package com.lecturo.lecturo.ui.event

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.model.Event
import com.lecturo.lecturo.databinding.ActivityEventBinding
import com.lecturo.lecturo.di.ViewModelFactory
import com.lecturo.lecturo.utils.AiExtractionHelper
import com.lecturo.lecturo.viewmodel.event.EventViewModel
import kotlinx.coroutines.launch
import java.io.File

// --- IMPORT COMPOSE ---
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class EventActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEventBinding
    private lateinit var eventAdapter: EventAdapter
    private lateinit var aiHelper: AiExtractionHelper // Helper AI
    private lateinit var loadingDialog: AlertDialog // Dialog Loading

    private var tempImageUri: Uri? = null // Uri untuk Kamera

    // Variabel penampung dialog AI Compose
    private var aiOptionsDialog: androidx.appcompat.app.AlertDialog? = null

    private val viewModel: EventViewModel by viewModels {
        ViewModelFactory.getInstance(this)
    }

    // --- LAUNCHERS ---

    // 1. Launcher Kamera
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            tempImageUri?.let { uri ->
                processSelection(uri, isPdf = false)
            } ?: run {
                Toast.makeText(this, "Gagal mengambil gambar (Uri Kosong)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 2. Launcher Galeri
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { safeUri ->
            processSelection(safeUri, isPdf = false)
        }
    }

    // 3. Launcher PDF
    private val pdfLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
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

        aiHelper = AiExtractionHelper(this)
        setupLoadingDialog()

        setupStatusBar()
        setupToolbar()
        setupRecyclerView()
        setupSearchView()
        setupFab()
        observeViewModel()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun setupComposeFilters() {
        binding.composeViewFilters.setContent {
            val categories by viewModel.categories.observeAsState(initial = emptyList())
            val activeFilter by viewModel.categoryFilter.observeAsState(initial = "")
            val allFilters = listOf("Semua") + categories

            MaterialTheme {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(allFilters) { category ->
                        val isSelected = if (category == "Semua") activeFilter == "" else activeFilter == category

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (category == "Semua") {
                                    viewModel.setCategoryFilter("")
                                } else {
                                    viewModel.setCategoryFilter(category)
                                }
                            },
                            label = { Text(text = category) },
                            leadingIcon = if (isSelected) {
                                {
                                    val iconRes = when (category.lowercase(java.util.Locale.getDefault())) {
                                        "semua" -> R.drawable.ic_event_available
                                        "rapat" -> R.drawable.ic_meet
                                        "seminar" -> R.drawable.ic_seminar
                                        "webinar" -> R.drawable.ic_webinar
                                        "workshop", "lokakarya" -> R.drawable.ic_workshop
                                        "penelitian" -> R.drawable.ic_research
                                        "pengabdian masyarakat" -> R.drawable.ic_community
                                        "lainnya" -> R.drawable.ic_event_available
                                        else -> R.drawable.ic_event_available
                                    }
                                    Icon(
                                        painter = painterResource(id = iconRes),
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = colorResource(id = R.color.event_color_light),
                                labelColor = colorResource(id = R.color.chip_event_text_color),
                                selectedContainerColor = colorResource(id = R.color.event_color),
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = colorResource(id = R.color.event_color_light),
                                selectedBorderColor = colorResource(id = R.color.event_color)
                            )
                        )
                    }
                }
            }
        }
    }

    private fun setupStatusBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val isNightMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        window.statusBarColor = getColor(R.color.colorPrimary)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isNightMode

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

        // FAB AI Click -> Buka Dialog Sumber (Tampilan Baru)
        binding.fabAi.setOnClickListener {
            showSourceSelectionDialog()
        }

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

        ViewCompat.setOnApplyWindowInsetsListener(binding.fabAi) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val baseMarginBottomPx = (90 * resources.displayMetrics.density).toInt()
            val baseMarginEndPx = (26 * resources.displayMetrics.density).toInt()

            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = systemBars.bottom + baseMarginBottomPx
                rightMargin = baseMarginEndPx
            }
            insets
        }
    }

    // ==========================================
    // LOGIKA PEMANGGILAN DIALOG AI COMPOSE
    // ==========================================
    private fun showSourceSelectionDialog() {
        val composeView = ComposeView(this).apply {
            setContent {
                MaterialTheme {
                    AiOptionsComposeDialog(
                        onDismiss = { aiOptionsDialog?.dismiss() },
                        onItemClick = { optionId ->
                            aiOptionsDialog?.dismiss()
                            when (optionId) {
                                0 -> checkCameraPermission()
                                1 -> galleryLauncher.launch("image/*")
                                2 -> pdfLauncher.launch("application/pdf")
                            }
                        }
                    )
                }
            }
        }

        aiOptionsDialog = MaterialAlertDialogBuilder(this)
            .setView(composeView)
            .setBackground(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            .create()

        aiOptionsDialog?.show()
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
    private fun processSelection(uri: Uri, isPdf: Boolean) {
        showLoading(true)

        lifecycleScope.launch {
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

    private fun setupToolbar() {
        setSupportActionBar(binding.eventToolbar)
        supportActionBar?.title = "Jadwal Acara"
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
        setupComposeFilters()
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.layoutEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerViewEvents.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun showDeleteConfirmation(event: Event) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Hapus Acara")
            .setMessage("Apakah Anda yakin ingin menghapus acara \"${event.title}\"?")
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
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

// ==========================================
// 🚀 JETPACK COMPOSE UI COMPONENT (DIALOG AI)
// ==========================================

@Composable
fun AiOptionsComposeDialog(onDismiss: () -> Unit, onItemClick: (Int) -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp), // Membuat ujung membulat khas Material 3
        colors = CardDefaults.cardColors(containerColor = colorResource(id = R.color.card_background)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Pilih Sumber Data (AI)",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                color = colorResource(id = R.color.colorPrimary),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Opsi 1: Kamera
            AiOptionItem(
                iconRes = R.drawable.ic_camera,
                title = "Ambil dari Kamera",
                colorRes = R.color.task_color // Pakai warna task/oren sebagai highlight
            ) { onItemClick(0) }

            // Opsi 2: Galeri
            AiOptionItem(
                iconRes = R.drawable.ic_gallery,
                title = "Pilih dari Galeri",
                colorRes = R.color.event_color // Pakai warna event/hijau
            ) { onItemClick(1) }

            // Opsi 3: PDF
            AiOptionItem(
                iconRes = R.drawable.ic_pdf,
                title = "Dokumen PDF / File",
                colorRes = R.color.consultation_color // Pakai warna konsultasi/merah atau lainnya
            ) { onItemClick(2) }

            Spacer(modifier = Modifier.height(16.dp))

            // Tombol Batal
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = "Batal",
                    color = colorResource(id = R.color.text_secondary),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun AiOptionItem(iconRes: Int, title: String, colorRes: Int, onClick: () -> Unit) {
    val mainColor = colorResource(id = colorRes)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp)
    ) {
        // Kotak background untuk ikon dengan efek transparansi 15%
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .background(mainColor.copy(alpha = 0.15f), CircleShape)
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                tint = mainColor,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Teks Menu
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = colorResource(id = R.color.text_primary)
        )
    }
}