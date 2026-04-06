package com.lecturo.lecturo.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.preference.PreferenceManager
import com.lecturo.lecturo.R
import com.lecturo.lecturo.databinding.ActivityNotificationSettingsBinding

class NotificationSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationSettingsBinding
    private lateinit var sharedPreferences: SharedPreferences

    private val notificationOptions = arrayOf(
        "Tidak ada notifikasi",
        "Tepat waktu",
        "5 menit sebelumnya",
        "15 menit sebelumnya",
        "30 menit sebelumnya",
        "1 jam sebelumnya",
        "2 jam sebelumnya",
        "1 hari sebelumnya"
    )

    private val notificationValues = arrayOf(-1, 0, 5, 15, 30, 60, 120, 1440)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

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

        // [SOLUSI PRO: Mendorong btnSave ke atas Navigasi Sistem]
        ViewCompat.setOnApplyWindowInsetsListener(binding.buttonSave) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Konversi margin dasar 16dp dari XML ke satuan Pixel
            val baseMarginPx = (16 * resources.displayMetrics.density).toInt()

            // Update HANYA margin bawahnya, margin lain biarkan sesuai XML
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = systemBars.bottom + baseMarginPx
            }

            insets
        }

        setupToolbar()
        setupDropdowns()
        loadCurrentSettings()
        setupSaveButton()

        setupSwitchListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarSetting)
        supportActionBar?.apply {
            title = "Notification Settings"
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun setupDropdowns() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, notificationOptions)

        binding.autoCompleteTeaching.setAdapter(adapter)
        binding.autoCompleteEvent.setAdapter(adapter)
        binding.autoCompleteTask.setAdapter(adapter)
        binding.autoCompleteConsultation.setAdapter(adapter)
    }

    // --- FUNGSI BARU UNTUK MENGATUR INTERAKSI ANTAR SAKLAR ---
    private fun setupSwitchListeners() {
        binding.switchGlobalNotification.setOnCheckedChangeListener { _, isChecked ->
            // Saat saklar global diubah, aktifkan/nonaktifkan saklar lainnya
            updateSubSwitchStates(isChecked)
        }
    }

    private fun updateSubSwitchStates(isGlobalEnabled: Boolean) {
        binding.switchSound.isEnabled = isGlobalEnabled
        binding.switchVibration.isEnabled = isGlobalEnabled
    }

    private fun loadCurrentSettings() {
        val teachingDefault = sharedPreferences.getInt("default_notification_teaching", 15)
        val eventDefault = sharedPreferences.getInt("default_notification_event", 15)
        val taskDefault = sharedPreferences.getInt("default_notification_task", 60)
        val consultationDefault = sharedPreferences.getInt("default_notification_consultation", 30)

        binding.autoCompleteTeaching.setText(getOptionText(teachingDefault), false)
        binding.autoCompleteEvent.setText(getOptionText(eventDefault), false)
        binding.autoCompleteTask.setText(getOptionText(taskDefault), false)
        binding.autoCompleteConsultation.setText(getOptionText(consultationDefault), false)

        val isGlobalEnabled = sharedPreferences.getBoolean("global_notification_enabled", true)
        binding.switchGlobalNotification.isChecked = isGlobalEnabled
        binding.switchSound.isChecked = sharedPreferences.getBoolean("notification_sound_enabled", false) // Default 'false'
        binding.switchVibration.isChecked = sharedPreferences.getBoolean("notification_vibration_enabled", true)

        // DIUBAH: Atur status awal dari saklar anak
        updateSubSwitchStates(isGlobalEnabled)
    }

    private fun getOptionText(value: Int): String {
        val index = notificationValues.indexOf(value)
        return if (index >= 0) notificationOptions[index] else notificationOptions[3] // Default 15 menit
    }

    private fun getOptionValue(text: String): Int {
        val index = notificationOptions.indexOf(text)
        return if (index >= 0) notificationValues[index] else 15 // Default 15 menit
    }

    private fun setupSaveButton() {
        binding.buttonSave.setOnClickListener {
            saveSettings()
        }
    }

    private fun saveSettings() {
        val teachingValue = getOptionValue(binding.autoCompleteTeaching.text.toString())
        val eventValue = getOptionValue(binding.autoCompleteEvent.text.toString())
        val taskValue = getOptionValue(binding.autoCompleteTask.text.toString())
        val consultationValue = getOptionValue(binding.autoCompleteConsultation.text.toString())

        sharedPreferences.edit().apply {
            putInt("default_notification_teaching", teachingValue)
            putInt("default_notification_event", eventValue)
            putInt("default_notification_task", taskValue)
            putInt("default_notification_consultation", consultationValue)
            // Simpan status semua saklar
            putBoolean("global_notification_enabled", binding.switchGlobalNotification.isChecked)
            putBoolean("notification_sound_enabled", binding.switchSound.isChecked)
            putBoolean("notification_vibration_enabled", binding.switchVibration.isChecked)
            apply()
        }

        Toast.makeText(this, "Pengaturan notifikasi berhasil disimpan", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
