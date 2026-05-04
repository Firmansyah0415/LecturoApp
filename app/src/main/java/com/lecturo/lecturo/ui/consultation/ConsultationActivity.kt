package com.lecturo.lecturo.ui.consultation

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import android.view.ViewGroup
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.model.ConsultationSchedule
import com.lecturo.lecturo.databinding.ActivityConsultationBinding
import com.lecturo.lecturo.databinding.BottomSheetChoosePatternBinding
import com.lecturo.lecturo.databinding.BottomSheetConsultationActionBinding
import com.lecturo.lecturo.ui.consultation.pattern.ConsultationPatternActivity
import com.lecturo.lecturo.utils.DateHelper
import com.lecturo.lecturo.viewmodel.consultation.ConsultationViewModel
import com.lecturo.lecturo.viewmodel.consultation.FilterType

class ConsultationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConsultationBinding
    private lateinit var adapter: ConsultationAdapter
    private val viewModel: ConsultationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConsultationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // bikin status bar transparan sekali untuk semua activity
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 1. Cek apakah aplikasi sedang di Mode Gelap
        val isNightMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

        // 2. atur warna status bar
        window.statusBarColor = getColor(R.color.colorPrimary)

        // 3. atur warna teks/icon status bar
        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = !isNightMode

        // otomatis kasih padding top di root view sesuai status bar
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(
                view.paddingLeft,
                statusBarInsets.top,
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }

        // [SOLUSI PRO: EDGE-TO-EDGE FAB]
        ViewCompat.setOnApplyWindowInsetsListener(binding.fabAdd) { view, insets ->
            // Dapatkan ukuran tinggi navigasi sistem di bawah layar (3 tombol atau gesture)
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Konversi 16dp margin dasar ke dalam ukuran Pixel (karena Insets menggunakan Pixel)
            val baseMarginPx = (16 * resources.displayMetrics.density).toInt()

            // Update tata letak FAB
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                // Tambahkan tinggi navigasi sistem dengan margin dasar
                bottomMargin = systemBars.bottom + baseMarginPx
                rightMargin = baseMarginPx
            }

            insets
        }

        setupToolbar()
        setupRecyclerView()
        setupFab()
        setupSearchAndFilter()
        observeData()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnPatternSettings.setOnClickListener {
            // Update intent ke Activity yang baru dibuat
            startActivity(Intent(this, ConsultationPatternActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        adapter = ConsultationAdapter { schedule ->
            // SAAT ITEM DIKLIK -> TAMPILKAN OPSI AKSI
            showActionDialog(schedule)
        }

        binding.rvConsultations.apply {
            layoutManager = LinearLayoutManager(this@ConsultationActivity)
            this.adapter = this@ConsultationActivity.adapter
        }
    }

    // --- FITUR BARU: BOTTOM SHEET AKSI ---
    private fun showActionDialog(schedule: ConsultationSchedule) {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetConsultationActionBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        // Atur visibilitas tombol berdasarkan status saat ini
        if (schedule.status == "COMPLETED" || schedule.status == "CANCELLED") {
            sheetBinding.btnComplete.visibility = View.GONE
            sheetBinding.btnCancel.visibility = View.GONE
            sheetBinding.btnEdit.visibility = View.GONE
        }

        // 1. EDIT
        sheetBinding.btnEdit.setOnClickListener {
            val intent = Intent(this, DetailConsultationActivity::class.java)
            intent.putExtra("SCHEDULE_ID", schedule.id)
            startActivity(intent)
            dialog.dismiss()
        }

        // 2. SELESAI
        sheetBinding.btnComplete.setOnClickListener {
            val updatedSchedule = schedule.copy(status = "COMPLETED")
            viewModel.updateSchedule(updatedSchedule)
            Toast.makeText(this, "Jadwal ditandai selesai", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        // 3. BATALKAN
        sheetBinding.btnCancel.setOnClickListener {
            val updatedSchedule = schedule.copy(status = "CANCELLED")
            viewModel.updateSchedule(updatedSchedule)
            Toast.makeText(this, "Jadwal dibatalkan", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        // 4. HAPUS
        sheetBinding.btnDelete.setOnClickListener {
            // Konfirmasi Hapus
            MaterialAlertDialogBuilder(this)
                .setTitle("Hapus Jadwal?")
                .setMessage("Jadwal ini akan dihapus permanen dari data lokal.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Hapus") { _, _ ->
                    viewModel.deleteSchedule(schedule)
                    Toast.makeText(this, "Jadwal berhasil dihapus", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .show()
        }

        dialog.show()
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            showCreateOptionsDialog()
        }
    }

    private fun showCreateOptionsDialog() {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetChoosePatternBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        val patternAdapter = PatternAdapter { pattern ->
            // 1. Ambil tanggal asli dari helper (biasanya yyyy-MM-dd)
            val nextDateRaw = DateHelper.getNextDateForDay(pattern.dayOfWeek)

            // 2. [PERBAIKAN BUG 15] Cegat dan ubah secara paksa menjadi dd/MM/yyyy
            val formattedNextDate = try {
                val inFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                val outFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                val dateObj = inFormat.parse(nextDateRaw)
                outFormat.format(dateObj!!)
            } catch (e: Exception) {
                nextDateRaw // Fallback jika gagal
            }

            // 3. Kirim tanggal yang sudah cantik ke halaman Detail
            val intent = Intent(this, DetailConsultationActivity::class.java).apply {
                putExtra("IS_FROM_TEMPLATE", true)
                putExtra("TEMPLATE_ID", pattern.id.toString())
                putExtra("PREFILL_TITLE", pattern.titleTemplate)
                putExtra("PREFILL_DATE", formattedNextDate) // <--- Gunakan yang sudah dikonversi
                putExtra("PREFILL_START", pattern.startTime)
                putExtra("PREFILL_END", pattern.endTime)
                putExtra("PREFILL_LOCATION", pattern.locationDefault)
            }
            startActivity(intent)
            dialog.dismiss()
        }

        sheetBinding.rvPatterns.layoutManager = LinearLayoutManager(this)
        sheetBinding.rvPatterns.adapter = patternAdapter

        viewModel.activePatterns.observe(this) { patterns ->
            patternAdapter.submitList(patterns)
            if (patterns.isEmpty()) {
                sheetBinding.tvTemplateHeader.visibility = View.GONE
                sheetBinding.rvPatterns.visibility = View.GONE
            } else {
                sheetBinding.tvTemplateHeader.visibility = View.VISIBLE
                sheetBinding.rvPatterns.visibility = View.VISIBLE
            }
        }

        sheetBinding.btnCreateManual.setOnClickListener {
            val intent = Intent(this, DetailConsultationActivity::class.java)
            startActivity(intent)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun observeData() {
        viewModel.schedules.observe(this) { listJadwal ->
            adapter.submitList(listJadwal)

            if (listJadwal.isEmpty()) {
                binding.layoutEmptyState.visibility = View.VISIBLE
                binding.rvConsultations.visibility = View.GONE
            } else {
                binding.layoutEmptyState.visibility = View.GONE
                binding.rvConsultations.visibility = View.VISIBLE
            }
        }
    }

    private fun setupSearchAndFilter() {
        binding.chipGroupFilter.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                // [PERBAIKAN] Sekarang chipAll benar-benar memanggil ALL, bukan UPCOMING
                binding.chipAll.id -> viewModel.setFilter(FilterType.ALL)
                binding.chipUpcoming.id -> viewModel.setFilter(FilterType.UPCOMING)
                binding.chipToday.id -> viewModel.setFilter(FilterType.TODAY)
                binding.chipHistory.id -> viewModel.setFilter(FilterType.HISTORY)
            }
        }

        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                viewModel.search(s?.toString() ?: "")
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }
}