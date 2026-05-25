package com.lecturo.lecturo.ui.event

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.updateLayoutParams
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.model.Event
import com.lecturo.lecturo.databinding.ActivityEventBinding
import com.lecturo.lecturo.di.ViewModelFactory
import com.lecturo.lecturo.utils.AiExtractionHelper
import com.lecturo.lecturo.viewmodel.event.EventViewModel
import com.lecturo.lecturo.ui.components.EventListContent // Komponen Compose Baru
import com.lecturo.lecturo.ui.components.AiOptionsComposeDialog // Komponen Dialog AI
import kotlinx.coroutines.launch
import java.io.File
import androidx.core.graphics.drawable.toDrawable
import com.lecturo.lecturo.ui.base.BaseActivity

class EventActivity : BaseActivity() {

    private lateinit var binding: ActivityEventBinding
    private lateinit var aiHelper: AiExtractionHelper
    private lateinit var loadingDialog: AlertDialog

    private var tempImageUri: Uri? = null
    private var aiOptionsDialog: androidx.appcompat.app.AlertDialog? = null

    private val viewModel: EventViewModel by viewModels {
        ViewModelFactory.getInstance(this)
    }

    // --- LAUNCHERS ---
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) tempImageUri?.let { processSelection(it, isPdf = false) }
        else Toast.makeText(this, "Gagal mengambil gambar (Uri Kosong)", Toast.LENGTH_SHORT).show()
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { processSelection(it, isPdf = false) }
    }

    private val pdfLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { processSelection(it, isPdf = true) }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) openCamera() else Toast.makeText(this, "Izin kamera ditolak", Toast.LENGTH_SHORT).show()
    }

    private val addEventLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Data akan otomatis refresh via LiveData
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
        setupSearchView()
        setupFab()
        observeViewModel()
    }

    private fun setupStatusBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val isNightMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isNightMode

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(view.paddingLeft, statusBarInsets.top, view.paddingRight, view.paddingBottom)
            insets
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.eventToolbar)
        supportActionBar?.title = "Jadwal Acara"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
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

    private fun setupFab() {
        binding.fabAddEvent.setOnClickListener {
            startActivity(Intent(this, AddEventActivity::class.java))
        }

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
    // HUBUNGKAN DATA KE JETPACK COMPOSE
    // ==========================================
    private fun observeViewModel() {
        viewModel.filteredEvents.observe(this) { events ->
            binding.composeViewContent.setContent {
                val categories by viewModel.categories.observeAsState(emptyList())
                val activeCategory by viewModel.categoryFilter.observeAsState("")
                val isSortNewest by viewModel.isSortNewest.observeAsState(true)

                MaterialTheme {
                    EventListContent(
                        events = events ?: emptyList(),
                        categories = categories,
                        activeCategory = activeCategory,
                        isSortNewest = isSortNewest,
                        onCategoryClick = { viewModel.setCategoryFilter(it) },
                        onEdit = { event ->
                            val intent = Intent(this@EventActivity, AddEventActivity::class.java)
                            intent.putExtra("event_id", event.id)
                            addEventLauncher.launch(intent)
                        },
                        onDelete = { event -> showDeleteConfirmation(event) },
                        onStatusToggle = { event ->
                            viewModel.updateCompletedStatus(event.id, !event.isCompleted)
                        }
                    )
                }
            }
        }
    }

    // ==========================================
    // LOGIKA DIALOG AI
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
            .setBackground(android.graphics.Color.TRANSPARENT.toDrawable())
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

    // ==========================================
    // MENU APPBAR (SORT & FILTER)
    // ==========================================
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.event_menu, menu)

        // Update ikon urutan saat state berubah
        val sortItem = menu.findItem(R.id.action_sort)
        viewModel.isSortNewest.observe(this) { isNewest ->
            sortItem?.setIcon(if (isNewest) R.drawable.ic_clock_arrow_down else R.drawable.ic_clock_arrow_up)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_sort -> {
                viewModel.toggleSort()
                val isNewest = viewModel.isSortNewest.value ?: true
                val msg = if (isNewest) "Urut: Terbaru - Terlama" else "Urut: Terlama - Terbaru"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_filter_belum -> {
                viewModel.setStatusFilter("Belum")
                Toast.makeText(this, "Menampilkan acara belum selesai", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_filter_selesai -> {
                viewModel.setStatusFilter("Selesai")
                Toast.makeText(this, "Menampilkan acara selesai", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_clear_filters -> {
                viewModel.clearFilters()
                binding.etSearch.text?.clear()
                Toast.makeText(this, "Filter dibersihkan", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}