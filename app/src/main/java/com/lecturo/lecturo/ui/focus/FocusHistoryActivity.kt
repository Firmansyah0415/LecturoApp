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
        // Ambil Data History dari ViewModel
        viewModel.getHistory().observe(this) { historyList ->
            adapter.submitList(historyList)

            // Pisahkan data yang Tuntas dan Batal
            val completedList = historyList.filter { it.status == "COMPLETED" }
            val cancelledList = historyList.filter { it.status == "CANCELLED" }

            // Hitung Statistik
            val totalMinutes = completedList.sumOf { it.durationMinutes }
            val completedCount = completedList.size
            val cancelledCount = cancelledList.size

            // Tampilkan ke UI
            binding.tvTotalFocusTime.text = "$totalMinutes Mnt"
            binding.tvCompletedCount.text = "$completedCount Sesi"
            binding.tvCancelledCount.text = "$cancelledCount Sesi"
        }
    }

    private fun setupSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = getColor(R.color.colorPrimary)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(view.paddingLeft, statusBarInsets.top, view.paddingRight, view.paddingBottom)
            insets
        }
    }
}