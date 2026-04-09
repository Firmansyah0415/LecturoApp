package com.lecturo.lecturo.ui.task

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.updateLayoutParams
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.model.Tasks
import com.lecturo.lecturo.data.repository.CalendarRepository
import com.lecturo.lecturo.data.repository.TasksRepository
import com.lecturo.lecturo.databinding.ActivityTasksBinding
import com.lecturo.lecturo.ui.focus.FocusActivity
import com.lecturo.lecturo.viewmodel.task.TasksViewModel
import com.lecturo.lecturo.viewmodel.task.TasksViewModelFactory

class TasksActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTasksBinding

    private val viewModel: TasksViewModel by viewModels {
        getViewModelFactory()
    }

    fun getViewModelFactory(): TasksViewModelFactory {
        val database = AppDatabase.getDatabase(applicationContext)
        val tasksRepository = TasksRepository(database.tasksDao(), database.focusSessionDao(), applicationContext)
        val calendarRepository = CalendarRepository(database.calendarEntryDao())
        return TasksViewModelFactory(tasksRepository, calendarRepository, application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTasksBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = getColor(R.color.colorPrimary)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(view.paddingLeft, statusBarInsets.top, view.paddingRight, view.paddingBottom)
            insets
        }

        // [SOLUSI PRO: Mendorong FAB ke atas Navigasi Sistem]
        ViewCompat.setOnApplyWindowInsetsListener(binding.fabAddTasks) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Konversi margin dasar 20dp dari XML ke satuan Pixel
            val baseMarginPx = (20 * resources.displayMetrics.density).toInt()

            // Update HANYA margin bawahnya, ditambahkan dengan tinggi navigasi sistem
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = systemBars.bottom + baseMarginPx
                rightMargin = baseMarginPx // Sesuaikan juga margin kanan agar presisi
            }

            insets
        }

        setSupportActionBar(binding.taskToolbar)
        setupToolbar()
        setupViewPager()
        setupFabActions()
        observeTabTitles()
    }

    private fun setupToolbar() {
        supportActionBar?.title = "Tugas"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.tasks_menu, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView

        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = true
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText ?: "")
                return true
            }
        })

        searchItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true
            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                viewModel.setSearchQuery("")
                return true
            }
        })
        return true
    }

    private fun setupViewPager() {
        // Pastikan TasksPagerAdapter Anda mengirim 'handleTasksAction' ke Fragment
        val pagerAdapter = TasksPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = if (position == 0) "Tugas" else "Selesai"
        }.attach()
    }

    // --- [UBAH DISINI] Fungsi yang dipanggil dari Adapter/Fragment ---
    fun handleTasksAction(tasks: Tasks, action: String) {
        when (action) {
            "delete" -> showDeleteConfirmation(tasks)
            "complete" -> viewModel.updateTasksCompletedStatus(tasks.id, true)
            "uncomplete" -> viewModel.updateTasksCompletedStatus(tasks.id, false)

            // Saat item diklik (sinyal 'edit' dari adapter), kita munculkan Dialog Pilihan dulu
            "edit" -> showTaskOptions(tasks)
        }
    }

    private fun setupFabActions() {
        binding.fabAddTasks.setOnClickListener {
            startActivity(Intent(this, AddTasksActivity::class.java))
        }
    }

    private fun observeTabTitles() {
        viewModel.pendingTasks.observe(this) { pending ->
            binding.tabLayout.getTabAt(0)?.text = "Tugas (${pending.size})"
        }
        viewModel.completedTasks.observe(this) { completed ->
            binding.tabLayout.getTabAt(1)?.text = "Selesai (${completed.size})"
        }
    }

    private fun showDeleteConfirmation(tasks: Tasks) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Hapus Tugas")
            .setMessage("Yakin ingin menghapus tugas \"${tasks.title}\"?")
            .setPositiveButton("Ya") { _, _ -> viewModel.deleteTasks(tasks.id) }
            .setNegativeButton("Batal", null)
            .show()
    }

    // --- [TAMBAHAN BARU] LOGIKA MENU PILIHAN ---
    private fun showTaskOptions(task: Tasks) {
        // 1. Inisialisasi BottomSheetDialog
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_task_options, null)
        dialog.setContentView(view)

        // 3. Set Judul Tugas di Header Bottom Sheet
        val tvTitle = view.findViewById<android.widget.TextView>(R.id.tvSheetTitle)
        tvTitle.text = task.title

        // =============================================================
        // [KODE YANG DIPINDAH KE ATAS] Ambil data dari Preferences di sini
        // =============================================================
        val prefs = com.lecturo.lecturo.utils.FocusPreferences(this)
        val activeTaskId = prefs.getActiveTaskId()
        val currentPhase = prefs.getCurrentPhase()

        // Ambil View TextView-nya
        val tvFocusTitle = view.findViewById<android.widget.TextView>(R.id.tvFocusActionTitle)
        val tvFocusSub = view.findViewById<android.widget.TextView>(R.id.tvFocusActionSubtitle)

        // Ubah teks jika tugas ini adalah tugas yang sedang berjalan di Pomodoro
        if (activeTaskId == task.id) {
            tvFocusTitle.text = if (currentPhase == "Fokus") "Lanjutkan Fokus" else "Lanjutkan Istirahat"
            tvFocusSub.text = "Sesi sedang berjalan"
        } else {
            tvFocusTitle.text = "Mulai Fokus"
            tvFocusSub.text = "Mode Pomodoro"
        }
        // =============================================================

        // 4. LOGIKA KLIK MENU
        // A. Menu Fokus (Pomodoro)
        view.findViewById<android.view.View>(R.id.layoutFocus).setOnClickListener {
            dialog.dismiss() // Tutup dialog

            // --- [PERBAIKAN BUG 1: BLOKIR JIKA ADA TUGAS LAIN JALAN] ---
            // Kita cukup panggil variabel activeTaskId yang sudah dideklarasikan di atas tadi
            if (activeTaskId != -1L && activeTaskId != task.id) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Sesi Lain Sedang Aktif")
                    .setMessage("Anda memiliki sesi fokus yang sedang berjalan/jeda di tugas lain. Harap selesaikan atau hentikan sesi tersebut terlebih dahulu.")
                    .setPositiveButton("Mengerti", null)
                    .show()
                return@setOnClickListener
            }
            // -----------------------------------------------------------

            val intent = Intent(this, FocusActivity::class.java)
            intent.putExtra("TASK_ID", task.id)
            intent.putExtra("TASK_TITLE", task.title)
            intent.putExtra("TASK_FIRESTORE_ID", task.firestoreId)
            startActivity(intent)
        }

        // B. Menu Edit
        view.findViewById<android.view.View>(R.id.layoutEdit).setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, AddTasksActivity::class.java)
            intent.putExtra("tasks_id", task.id)
            startActivity(intent)
        }

        // C. Menu Hapus
        view.findViewById<android.view.View>(R.id.layoutDelete).setOnClickListener {
            dialog.dismiss()
            showDeleteConfirmation(task) // Panggil fungsi konfirmasi hapus
        }

        // 5. Tampilkan Dialog
        dialog.show()
    }
}