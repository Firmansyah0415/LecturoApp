package com.lecturo.lecturo.ui.consultation.pattern

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import com.lecturo.lecturo.R
import com.lecturo.lecturo.databinding.ActivityConsultationPatternBinding
import com.lecturo.lecturo.ui.consultation.pattern.ManagePatternActivity
import com.lecturo.lecturo.viewmodel.consultation.ConsultationViewModel

class ConsultationPatternActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConsultationPatternBinding
    private val viewModel: ConsultationViewModel by viewModels()
    private lateinit var adapter: PatternListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConsultationPatternBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // bikin status bar transparan sekali untuk semua activity
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // atur warna status bar
        window.statusBarColor = getColor(R.color.colorPrimary)

        // atur warna teks/icon status bar → true = icon gelap (hitam), false = icon terang (putih)
        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = true

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
        ViewCompat.setOnApplyWindowInsetsListener(binding.fabAddPattern) { view, insets ->
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

        binding.fabAddPattern.setOnClickListener {
            startActivity(Intent(this, ManagePatternActivity::class.java))
        }

        observeData()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Template Rutin"
    }

    private fun setupRecyclerView() {
        adapter = PatternListAdapter(
            onItemClick = { pattern ->
                // Klik Item -> Buka Edit Mode
                val intent = Intent(this, ManagePatternActivity::class.java)
                intent.putExtra("PATTERN_DATA", pattern)
                startActivity(intent)
            },
            onSwitchToggle = { pattern, isChecked ->
                // Klik Switch -> Update Status Aktif/Nonaktif
                viewModel.togglePatternStatus(pattern, isChecked)
            }
        )

        binding.rvPatterns.apply {
            layoutManager = LinearLayoutManager(this@ConsultationPatternActivity)
            this.adapter = this@ConsultationPatternActivity.adapter
        }
    }

    private fun observeData() {
        viewModel.allPatterns.observe(this) { patterns ->
            adapter.submitList(patterns)

            if (patterns.isEmpty()) {
                binding.tvEmptyState.visibility = View.VISIBLE
                binding.rvPatterns.visibility = View.GONE
            } else {
                binding.tvEmptyState.visibility = View.GONE
                binding.rvPatterns.visibility = View.VISIBLE
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}