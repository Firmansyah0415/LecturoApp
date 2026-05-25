package com.lecturo.lecturo.ui.task

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.updateLayoutParams
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.model.Tasks
import com.lecturo.lecturo.data.repository.CalendarRepository
import com.lecturo.lecturo.data.repository.TasksRepository
import com.lecturo.lecturo.databinding.ActivityTasksBinding
import com.lecturo.lecturo.ui.base.BaseActivity
import com.lecturo.lecturo.ui.focus.FocusActivity
import com.lecturo.lecturo.viewmodel.task.TasksViewModel
import com.lecturo.lecturo.viewmodel.task.TasksViewModelFactory

class TasksActivity : BaseActivity() {
    private lateinit var binding: ActivityTasksBinding

    // --- VARIABEL UNTUK EMPTY STATE ---
    private var currentTab = 0
    private var pendingCount = 0
    private var completedCount = 0

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
        val isNightMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

        window.statusBarColor = getColor(R.color.colorPrimary)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isNightMode

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(view.paddingLeft, statusBarInsets.top, view.paddingRight, view.paddingBottom)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.fabAddTasks) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val baseMarginPx = (20 * resources.displayMetrics.density).toInt()
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = systemBars.bottom + baseMarginPx
                rightMargin = baseMarginPx
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
        supportActionBar?.title = "Jadwal Tugas"
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

        // Ubah ikon sort saat state berubah
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
                // 1. Jalankan fungsi toggle sortir
                viewModel.toggleSort()

                // 2. Ambil nilai terbaru setelah di-toggle
                val isNewest = viewModel.isSortNewest.value ?: true

                // 3. Tentukan pesan berdasarkan status
                val message = if (isNewest) "Urut: Terbaru - Terlama" else "Urut: Terlama - Terbaru"

                // 4. Tampilkan Toast
                android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupViewPager() {
        val pagerAdapter = TasksPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = if (position == 0) "Tugas" else "Selesai"
        }.attach()

        // --- LISTENER PERPINDAHAN TAB UNTUK EMPTY STATE ---
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentTab = position
                checkEmptyState()
            }
        })
    }

    fun handleTasksAction(tasks: Tasks, action: String) {
        when (action) {
            "delete" -> showDeleteConfirmation(tasks)
            "complete" -> viewModel.updateTasksCompletedStatus(tasks.id, true)
            "uncomplete" -> viewModel.updateTasksCompletedStatus(tasks.id, false)
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
            pendingCount = pending.size
            binding.tabLayout.getTabAt(0)?.text = "Tugas ($pendingCount)"
            checkEmptyState() // Cek apakah perlu memunculkan Empty State
        }
        viewModel.completedTasks.observe(this) { completed ->
            completedCount = completed.size
            binding.tabLayout.getTabAt(1)?.text = "Selesai ($completedCount)"
            checkEmptyState() // Cek apakah perlu memunculkan Empty State
        }
    }

    // --- LOGIKA UTAMA EMPTY STATE ---
    private fun checkEmptyState() {
        // Cek tab mana yang aktif, lalu lihat jumlahnya
        val isEmpty = if (currentTab == 0) pendingCount == 0 else completedCount == 0

        if (isEmpty) {
            binding.layoutEmptyState.visibility = View.VISIBLE
            // Ganti teks secara dinamis berdasarkan tab yang dibuka
            if (currentTab == 0) {
                binding.tvEmptyTitle.text = "Belum ada jadwal tugas"
                binding.tvEmptySubtitle.text = "Tap tombol + untuk tambah jadwal tugas baru"
            } else {
                binding.tvEmptyTitle.text = "Belum ada tugas selesai"
                binding.tvEmptySubtitle.text = "Tugas yang telah selesai akan muncul di sini"
            }
        } else {
            binding.layoutEmptyState.visibility = View.GONE
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

    private fun showTaskOptions(task: Tasks) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_task_options, null)
        dialog.setContentView(view)

        val tvTitle = view.findViewById<android.widget.TextView>(R.id.tvSheetTitle)
        tvTitle.text = task.title

        val prefs = com.lecturo.lecturo.utils.FocusPreferences(this)
        val activeTaskId = prefs.getActiveTaskId()
        val currentPhase = prefs.getCurrentPhase()

        val tvFocusTitle = view.findViewById<android.widget.TextView>(R.id.tvFocusActionTitle)
        val tvFocusSub = view.findViewById<android.widget.TextView>(R.id.tvFocusActionSubtitle)

        if (activeTaskId == task.id) {
            tvFocusTitle.text = if (currentPhase == "Fokus") "Lanjutkan Fokus" else "Lanjutkan Istirahat"
            tvFocusSub.text = "Sesi sedang berjalan"
        } else {
            tvFocusTitle.text = "Mulai Fokus"
            tvFocusSub.text = "Mode Pomodoro"
        }

        view.findViewById<android.view.View>(R.id.layoutFocus).setOnClickListener {
            dialog.dismiss()

            if (activeTaskId != -1L && activeTaskId != task.id) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Sesi Lain Sedang Aktif")
                    .setMessage("Anda memiliki sesi fokus yang sedang berjalan/jeda di tugas lain. Harap selesaikan atau hentikan sesi tersebut terlebih dahulu.")
                    .setPositiveButton("Mengerti", null)
                    .show()
                return@setOnClickListener
            }

            val intent = Intent(this, FocusActivity::class.java)
            intent.putExtra("TASK_ID", task.id)
            intent.putExtra("TASK_TITLE", task.title)
            intent.putExtra("TASK_FIRESTORE_ID", task.firestoreId)
            startActivity(intent)
        }

        view.findViewById<android.view.View>(R.id.layoutEdit).setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, AddTasksActivity::class.java)
            intent.putExtra("tasks_id", task.id)
            startActivity(intent)
        }

        view.findViewById<android.view.View>(R.id.layoutDelete).setOnClickListener {
            dialog.dismiss()
            showDeleteConfirmation(task)
        }

        dialog.show()
    }
}