package com.lecturo.lecturo.ui.focus

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.repository.FocusRepository
import com.lecturo.lecturo.databinding.ActivityFocusHistoryBinding
import com.lecturo.lecturo.viewmodel.focus.FocusViewModel
import com.lecturo.lecturo.viewmodel.focus.FocusViewModelFactory

class FocusHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFocusHistoryBinding
    private lateinit var adapter: FocusHistoryAdapter

    // Kita pakai ViewModel yang sama karena logic getHistory sudah ada di sana
    private val viewModel: FocusViewModel by viewModels {
        val db = AppDatabase.getDatabase(this)
        val repo = FocusRepository(db.focusSessionDao(), db.tasksDao(), applicationContext
        )
        FocusViewModelFactory(repo)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFocusHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSystemUI()

        // Ambil Task ID & Task Title dari Intent
        val taskId = intent.getLongExtra("TASK_ID", -1L)
        val taskTitle = intent.getStringExtra("TASK_TITLE") ?: "Detail Tugas" // <--- Tangkap judulnya

        if (taskId != -1L) {
            viewModel.currentTaskId = taskId
        }

        setupToolbar(taskTitle)
        setupRecyclerView()
        observeData()
    }

    // Ubah fungsi ini untuk menerima parameter taskTitle
    private fun setupToolbar(taskTitle: String) {
        // Set Subtitle agar terlihat sangat elegan dan profesional
        binding.toolbarHistory.subtitle = taskTitle

        binding.toolbarHistory.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = FocusHistoryAdapter { session ->
            // Logic Hapus
            MaterialAlertDialogBuilder(this)
                .setTitle("Hapus Riwayat?")
                .setMessage("Data sesi ini akan dihapus permanen.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Hapus") { _, _ ->

                    // [KODE BARU] Panggil fungsi delete di ViewModel
                    viewModel.deleteSession(session)

                    // Opsional: Feedback ke user
                    Toast.makeText(this, "Sesi dihapus", Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter
    }

    private fun observeData() {
        viewModel.getHistory().observe(this) { historyList ->
            adapter.submitList(historyList)

            val completedList = historyList.filter { it.status == "COMPLETED" }
            val cancelledList = historyList.filter { it.status == "CANCELLED" }

            // [PERBAIKAN FORMAT TOTAL JAM]
            // Hitung total dari selisih waktu asli agar super akurat
            val totalMillis = completedList.sumOf { it.endTime - it.startTime }
            val totalMinutes = (totalMillis / 60000).toInt()

            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60

            val timeFormatted = when {
                hours > 0 && minutes > 0 -> "$hours Jam $minutes Mnt"
                hours > 0 -> "$hours Jam"
                else -> "$totalMinutes Mnt"
            }

            val completedCount = completedList.size
            val cancelledCount = cancelledList.size

            // Tampilkan ke UI
            binding.tvTotalFocusTime.text = timeFormatted
            binding.tvCompletedCount.text = "$completedCount Sesi"
            binding.tvCancelledCount.text = "$cancelledCount Sesi"
        }
    }

    private fun setupSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // 1. Cek apakah aplikasi sedang di Mode Gelap
        val isNightMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        // 2. atur warna status bar
        window.statusBarColor = getColor(R.color.colorPrimary)
        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = !isNightMode
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(view.paddingLeft, statusBarInsets.top, view.paddingRight, view.paddingBottom)
            insets
        }
    }
}