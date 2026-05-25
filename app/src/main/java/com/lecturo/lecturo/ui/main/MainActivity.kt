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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lecturo.lecturo.R
import com.lecturo.lecturo.databinding.ActivityMainBinding
import com.lecturo.lecturo.di.ViewModelFactory
import com.lecturo.lecturo.ui.consultation.DetailConsultationActivity
import com.lecturo.lecturo.ui.event.AddEventActivity
import com.lecturo.lecturo.ui.task.AddTasksActivity
import com.lecturo.lecturo.ui.teaching.AddTeachingActivity
import com.lecturo.lecturo.viewmodel.main.MainViewModel

// --- IMPORT COMPOSE ---
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lecturo.lecturo.ui.base.BaseActivity

class MainActivity : BaseActivity() {

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

        // 1. Cek apakah aplikasi sedang di Mode Gelap
        val isNightMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

        // 2. atur warna status bar
        window.statusBarColor = getColor(R.color.colorPrimary)

        // 3. atur warna teks/icon status bar
        // Jika isNightMode = true, maka kita butuh teks terang (false)
        // Jika isNightMode = false, maka kita butuh teks gelap (true)
        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = !isNightMode

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

        viewModel.refreshData()
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
            // Langsung panggil dialog dari Activity
            showAddScheduleDialog()
        }
    }

    // 1. Variabel penampung dialog
    private var customAlertDialog: androidx.appcompat.app.AlertDialog? = null

    // 2. Fungsi pemanggil Compose Dialog
    private fun showAddScheduleDialog() {
        // Membuat "Jembatan" (Bridge) dari XML ke Compose
        val composeView = ComposeView(this).apply {
            setContent {
                MaterialTheme {
                    AddScheduleComposeDialog(
                        onDismiss = { customAlertDialog?.dismiss() },
                        onItemClick = { routeId ->
                            customAlertDialog?.dismiss()
                            navigateToAddSchedule(routeId)
                        }
                    )
                }
            }
        }

        // Membungkus ComposeView ke dalam Material Dialog standar
        customAlertDialog = MaterialAlertDialogBuilder(this)
            .setView(composeView)
            // Penting: Buat background transparan agar sudut melengkung dari Compose terlihat sempurna!
            .setBackground(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            .create()

        customAlertDialog?.show()
    }

    // 3. Logika Pindah Halaman
    private fun navigateToAddSchedule(routeId: Int) {
        when (routeId) {
            0 -> startActivity(Intent(this, AddTasksActivity::class.java))
            1 -> startActivity(Intent(this, AddEventActivity::class.java))
            2 -> startActivity(Intent(this, AddTeachingActivity::class.java))
            3 -> startActivity(Intent(this, DetailConsultationActivity::class.java))
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


// ==========================================
// 🚀 JETPACK COMPOSE UI COMPONENT (DIALOG)
// ==========================================

@Composable
fun AddScheduleComposeDialog(onDismiss: () -> Unit, onItemClick: (Int) -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp), // Sudut sangat melengkung khas Material 3
        colors = CardDefaults.cardColors(containerColor = colorResource(id = R.color.card_background)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Tambah Jadwal Baru",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                color = colorResource(id = R.color.colorPrimary),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // List Item Kustom
            ScheduleOptionItem(
                iconRes = R.drawable.ic_task,
                title = "Tugas Akademik",
                colorRes = R.color.task_color
            ) { onItemClick(0) }

            ScheduleOptionItem(
                iconRes = R.drawable.ic_event_2,
                title = "Acara / Agenda",
                colorRes = R.color.event_color
            ) { onItemClick(1) }

            ScheduleOptionItem(
                iconRes = R.drawable.ic_class,
                title = "Jadwal Mengajar",
                colorRes = R.color.teaching_color
            ) { onItemClick(2) }

            ScheduleOptionItem(
                iconRes = R.drawable.ic_consultant,
                title = "Sesi Bimbingan",
                colorRes = R.color.consultation_color
            ) { onItemClick(3) }

            Spacer(modifier = Modifier.height(16.dp))

            // Tombol Batal
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = "Batal",
                    color = colorResource(id = R.color.text_secondary),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ScheduleOptionItem(iconRes: Int, title: String, colorRes: Int, onClick: () -> Unit) {
    val mainColor = colorResource(id = colorRes)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp)
    ) {
        // Ikon dengan Latar Belakang Transparan berwarna
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .background(mainColor.copy(alpha = 0.15f), CircleShape) // Efek soft background
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                tint = mainColor,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Teks Judul
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = colorResource(id = R.color.text_primary)
        )
    }
}