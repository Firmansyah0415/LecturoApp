package com.lecturo.lecturo.ui.main

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.lecturo.lecturo.R
import com.lecturo.lecturo.databinding.ActivityMainBinding
import com.lecturo.lecturo.di.ViewModelFactory
import com.lecturo.lecturo.viewmodel.main.MainViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val viewModel by viewModels<MainViewModel> {
        ViewModelFactory.getInstance(this)
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (!isGranted) {
                Toast.makeText(this, "Notifikasi tidak akan muncul tanpa izin.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyStoredTheme()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // bikin status bar transparan sekali untuk semua activity
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // atur warna status bar
        window.statusBarColor = getColor(R.color.colorPrimary)

        // atur warna teks/icon status bar → true = icon gelap (hitam), false = icon terang (putih)
        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = true

        // otomatis kasih padding di root view sesuai status bar & navigasi sistem
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                systemBars.top,
                view.paddingRight,
                systemBars.bottom // Terapkan bottom padding untuk mencegah tabrakan dengan 3-tombol navigasi
            )
            // KUNCI RAHASIA: Konsumsi insets di sini agar tidak diteruskan ke BottomAppBar
            // Ini akan mencegah BottomAppBar melar ke atas
            WindowInsetsCompat.CONSUMED
        }

        askNotificationPermission()
        checkExactAlarmPermission()

        setupNavigation()
        setupFab()
    }

    private fun applyStoredTheme() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val isDarkMode = sharedPreferences.getBoolean("dark_mode_enabled", false)

        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNavigationView.setupWithNavController(navController)

        // Handle navigasi manual jika diperlukan (misal untuk item yang belum ada di graph)
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    navController.navigate(R.id.nav_home)
                    true
                }
                R.id.nav_calendar -> {
                    navController.navigate(R.id.nav_calendar)
                    true
                }
                R.id.nav_settings -> {
                    navController.navigate(R.id.nav_settings)
                    true
                }
                R.id.nav_profile -> {
                    navController.navigate(R.id.nav_profile)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupFab() {
        binding.fab.setOnClickListener {
            // Memicu event di ViewModel, yang akan ditangkap oleh HomeFragment
            viewModel.onFabClicked()
        }
    }

    private fun checkExactAlarmPermission() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                AlertDialog.Builder(this)
                    .setTitle("Izin Diperlukan")
                    .setMessage("Aplikasi ini memerlukan izin untuk menjadwalkan alarm yang tepat waktu agar notifikasi bisa berfungsi dengan benar. Ketuk 'Buka Pengaturan' untuk mengaktifkannya.")
                    .setPositiveButton("Buka Pengaturan") { _, _ ->
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).also {
                            startActivity(it)
                        }
                    }
                    .setNegativeButton("Nanti", null)
                    .show()
            }
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
