package com.lecturo.lecturo.ui.main

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.lecturo.lecturo.R
import com.lecturo.lecturo.databinding.ActivityMainBinding
import com.lecturo.lecturo.di.ViewModelFactory
import com.lecturo.lecturo.ui.event.EventActivity
import com.lecturo.lecturo.ui.settings.NotificationSettingsActivity
import com.lecturo.lecturo.ui.task.TasksActivity
import com.lecturo.lecturo.ui.teaching.TeachingActivity
import com.lecturo.lecturo.ui.welcome.WelcomeActivity
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val viewModel by viewModels<MainViewModel> {
        ViewModelFactory.getInstance(this)
    }
    private lateinit var binding: ActivityMainBinding

    // Launcher untuk meminta izin notifikasi
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Izin diberikan, bagus!
            } else {
                Toast.makeText(this, "Notifikasi tidak akan muncul tanpa izin.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        askNotificationPermission() // Panggil fungsi ini
        checkExactAlarmPermission() // Panggil fungsi baru ini

        setSupportActionBar(binding.topAppBar)
        supportActionBar?.title = "Dashboard"

        viewModel.getSession().observe(this) { user ->
            if (!user.isLogin) {
                startActivity(Intent(this, WelcomeActivity::class.java))
                finish()
            }
        }

        val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID"))
        val currentDate = dateFormat.format(Date())
        binding.dateTextView.text = currentDate

        binding.teachingCard.setOnClickListener {
            startActivity(Intent(this, TeachingActivity::class.java))
        }

        binding.tasksCard.setOnClickListener {
            startActivity(Intent(this, TasksActivity::class.java))
        }

        binding.eventCard.setOnClickListener {
            startActivity(Intent(this, EventActivity::class.java))
        }

        setupUpcomingEvents()
    }

    // Fungsi baru untuk memeriksa izin alarm
    private fun checkExactAlarmPermission() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                // Jika izin tidak ada, tampilkan dialog untuk mengarahkan pengguna
                AlertDialog.Builder(this)
                    .setTitle("Izin Diperlukan")
                    .setMessage("Aplikasi ini memerlukan izin untuk menjadwalkan alarm yang tepat waktu agar notifikasi bisa berfungsi dengan benar. Ketuk 'Buka Pengaturan' untuk mengaktifkannya.")
                    .setPositiveButton("Buka Pengaturan") { _, _ ->
                        Intent().also { intent ->
                            intent.action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                            startActivity(intent)
                        }
                    }
                    .setNegativeButton("Nanti", null)
                    .show()
            }
        }
    }

    // Fungsi untuk meminta izin
    private fun askNotificationPermission() {
        // Hanya berlaku untuk Android 13 (Tiramisu) ke atas
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                // Jika izin belum diberikan, minta kepada pengguna
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                viewModel.logout()
                true
            }
            R.id.action_settings -> {
                val intent = Intent(this, NotificationSettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupUpcomingEvents() {
        // Logika ini akan kita perbaiki nanti agar dinamis dari database
        val events = listOf(
            "08:00 - Kelas Pemrograman Mobile (Ruang 301)",
            "10:30 - Rapat Departemen",
            "13:00 - Jam Konsultasi Mahasiswa",
            "15:30 - Deadline Penilaian Tugas"
        )
        binding.event1TextView.text = events[0]
        binding.event2TextView.text = events[1]
        binding.event3TextView.text = events[2]
        binding.event4TextView.text = events[3]
    }
}
