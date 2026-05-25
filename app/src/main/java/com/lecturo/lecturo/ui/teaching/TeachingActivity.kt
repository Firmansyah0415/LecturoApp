package com.lecturo.lecturo.ui.teaching

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.compose.material3.MaterialTheme
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.model.TeachingSchedule
import com.lecturo.lecturo.data.repository.TeachingRepository
import com.lecturo.lecturo.databinding.ActivityTeachingBinding
import com.lecturo.lecturo.ui.base.BaseActivity
import com.lecturo.lecturo.ui.components.TeachingListContent
import com.lecturo.lecturo.viewmodel.teaching.TeachingViewModel
import com.lecturo.lecturo.viewmodel.teaching.TeachingViewModelFactory

class TeachingActivity : BaseActivity() {

    private lateinit var binding: ActivityTeachingBinding

    private val viewModel: TeachingViewModel by viewModels {
        val database = AppDatabase.getDatabase(this)
        val repository = TeachingRepository(
            database.teachingScheduleDao(),
            database.calendarEntryDao(),
            applicationContext
        )
        TeachingViewModelFactory(repository, application)
    }

    private val addTeachingLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTeachingBinding.inflate(layoutInflater)
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

        ViewCompat.setOnApplyWindowInsetsListener(binding.fabAddTeaching) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val baseMarginPx = (20 * resources.displayMetrics.density).toInt()
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = systemBars.bottom + baseMarginPx
                rightMargin = baseMarginPx
            }
            insets
        }

        setSupportActionBar(binding.teachingToolbar)
        supportActionBar?.title = "Jadwal Mengajar"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.fabAddTeaching.setOnClickListener {
            val intent = Intent(this, AddTeachingActivity::class.java)
            addTeachingLauncher.launch(intent)
        }

        // --- MENGHUBUNGKAN DATA BASE KE COMPOSE CONTENT STREAM ---
        viewModel.teachingSchedules.observe(this) { schedules ->
            binding.composeViewContent.setContent {
                MaterialTheme {
                    TeachingListContent(
                        allSchedules = schedules ?: emptyList(),
                        viewModel = viewModel, // Teruskan ViewModel untuk sinkronisasi state
                        onEdit = { schedule ->
                            val intent = Intent(this@TeachingActivity, AddTeachingActivity::class.java)
                            intent.putExtra("schedule_id", schedule.localId)
                            addTeachingLauncher.launch(intent)
                        },
                        onDelete = { schedule ->
                            showDeleteConfirmation(schedule)
                        },
                        onStatusToggle = { schedule ->
                            val invertedStatus = !schedule.isCompleted
                            val updatedSchedule = schedule.copy(isCompleted = invertedStatus)
                            viewModel.updateTeachingSchedule(updatedSchedule)
                        }
                    )
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.teaching_menu, menu)
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

    private fun showDeleteConfirmation(schedule: TeachingSchedule) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Hapus Jadwal")
            .setMessage("Yakin ingin menghapus jadwal pertemuan ke-${schedule.meetingNumber} untuk \"${schedule.courseName}\"?")
            .setPositiveButton("Hapus") { _, _ ->
                viewModel.deleteTeachingSchedule(schedule.localId)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}