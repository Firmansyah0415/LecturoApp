package com.lecturo.lecturo.ui.base

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity

// Tambahkan kata "open" agar class ini bisa diwariskan ke Activity lain
open class BaseActivity : AppCompatActivity() {

    // Fungsi ini dipanggil sistem SEBELUM tampilan (UI) Activity dibuat
    override fun attachBaseContext(newBase: Context) {
        // 1. Ambil konfigurasi bawaan dari HP (termasuk font besar Pak Mus)
        val newOverride = Configuration(newBase.resources.configuration)

        // 2. KUNCI PAKSA ukuran font menjadi skala 1.0 (Ukuran Normal)
        newOverride.fontScale = 1.0f

        // 3. Terapkan konfigurasi yang sudah dimodifikasi ini ke aplikasi
        val context = newBase.createConfigurationContext(newOverride)
        super.attachBaseContext(context)
    }
}