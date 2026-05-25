package com.lecturo.lecturo.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.lecturo.lecturo.R
import com.lecturo.lecturo.databinding.ActivitySplashBinding
import com.lecturo.lecturo.ui.auth.LoginActivity
import com.lecturo.lecturo.ui.base.BaseActivity
import com.lecturo.lecturo.ui.main.MainActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : BaseActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Inisialisasi Firebase Auth
        auth = FirebaseAuth.getInstance()

        // 2. Setup Tampilan Full Screen
        setupFullScreen()

        // 3. Jalankan Animasi Logo
        playAnimation()

        // 4. Timer 1.5 detik sebelum pindah halaman
        Handler(Looper.getMainLooper()).postDelayed({
            checkAuthStatus()
        }, 1500)
    }

    private fun setupFullScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    )
        }
    }

    private fun playAnimation() {
        try {
            val logoAnim = AnimationUtils.loadAnimation(this, R.anim.wipe_in_up)
            binding.ivLogoMain.startAnimation(logoAnim)
        } catch (e: Exception) {
            e.printStackTrace() // Cegah crash jika animasi tidak ditemukan
        }
    }

    private fun checkAuthStatus() {
        // Cek User Firebase Langsung
        val currentUser = auth.currentUser

        val intent = if (currentUser != null) {
            // JIKA LOGIN: Ke Main Activity
            Intent(this, MainActivity::class.java)
        } else {
            // JIKA BELUM: Ke Login Activity (di package auth)
            Intent(this, LoginActivity::class.java)
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}