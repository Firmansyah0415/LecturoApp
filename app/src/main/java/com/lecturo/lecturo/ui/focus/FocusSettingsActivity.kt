package com.lecturo.lecturo.ui.focus

import android.os.Bundle
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lecturo.lecturo.R
import com.lecturo.lecturo.databinding.ActivityFocusSettingsBinding
import com.lecturo.lecturo.ui.base.BaseActivity
import com.lecturo.lecturo.utils.FocusPreferences

class FocusSettingsActivity : BaseActivity() {

    private lateinit var binding: ActivityFocusSettingsBinding
    private lateinit var focusPrefs: FocusPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFocusSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // bikin status bar transparan sekali untuk semua activity
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 1. Cek apakah aplikasi sedang di Mode Gelap
        val isNightMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

        // 2. atur warna status bar
        window.statusBarColor = getColor(R.color.colorPrimary)
        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = !isNightMode

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
        ViewCompat.setOnApplyWindowInsetsListener(binding.btnSave) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Konversi margin dasar 16dp dari XML ke satuan Pixel
            val baseMarginPx = (16 * resources.displayMetrics.density).toInt()

            // Update HANYA margin bawahnya, margin lain biarkan sesuai XML
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = systemBars.bottom + baseMarginPx
            }

            insets
        }

        focusPrefs = FocusPreferences(this)

        setupToolbar()
        loadCurrentData()
        setupListeners()
        setupSaveButton()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun loadCurrentData() {
        // Load data dari SharedPreferences ke UI
        val focus = focusPrefs.getFocusDuration()
        val shortBreak = focusPrefs.getShortBreakDuration()
        val longBreak = focusPrefs.getLongBreakDuration()

        binding.seekFocus.progress = focus
        binding.tvFocusVal.text = "$focus min"

        binding.seekShortBreak.progress = shortBreak
        binding.tvShortBreakVal.text = "$shortBreak min"

        binding.seekLongBreak.progress = longBreak
        binding.tvLongBreakVal.text = "$longBreak min"

        binding.switchSound.isChecked = focusPrefs.isSoundEnabled()
        binding.switchVibration.isChecked = focusPrefs.isVibrationEnabled()
    }

    private fun setupListeners() {
        // Listener Slider Fokus
        binding.seekFocus.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Minimal 1 menit
                val value = if (progress < 1) 1 else progress
                binding.tvFocusVal.text = "$value min"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Listener Slider Short Break
        binding.seekShortBreak.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = if (progress < 1) 1 else progress
                binding.tvShortBreakVal.text = "$value min"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Listener Slider Long Break
        binding.seekLongBreak.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = if (progress < 1) 1 else progress
                binding.tvLongBreakVal.text = "$value min"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            // Ambil value terakhir (handle minimal 1 menit)
            var focus = binding.seekFocus.progress
            if (focus < 1) focus = 1

            var shortBreak = binding.seekShortBreak.progress
            if (shortBreak < 1) shortBreak = 1

            var longBreak = binding.seekLongBreak.progress
            if (longBreak < 1) longBreak = 1

            val isSound = binding.switchSound.isChecked
            val isVibrate = binding.switchVibration.isChecked

            // Simpan ke Prefs
            focusPrefs.saveSettings(focus, shortBreak, longBreak, isSound, isVibrate)

            // Kembali ke Activity sebelumnya
            setResult(RESULT_OK)
            finish()
        }
    }
}