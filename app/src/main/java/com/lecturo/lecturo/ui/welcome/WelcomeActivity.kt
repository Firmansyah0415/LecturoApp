package com.lecturo.lecturo.ui.welcome

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.lecturo.lecturo.databinding.ActivityWelcomeBinding
import com.lecturo.lecturo.ui.login.LoginActivity
import com.lecturo.lecturo.ui.signup.SignupActivity

class WelcomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWelcomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAction()
        playAnimation()
    }

    private fun setupAction() {
        binding.loginButton.isEnabled = true
        binding.btSignup.isEnabled = true

        binding.loginButton.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        binding.btSignup.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }

    private fun playAnimation() {
        ObjectAnimator.ofFloat(binding.imageView, View.TRANSLATION_X, -30f, 30f).apply {
            duration = 6000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        }.start()

        val title = ObjectAnimator.ofFloat(binding.titleTextView, View.ALPHA, 1f).setDuration(200)
        val desc = ObjectAnimator.ofFloat(binding.descTextView, View.ALPHA, 1f).setDuration(200)
        val login = ObjectAnimator.ofFloat(binding.loginButton, View.ALPHA, 1f).setDuration(200)
        val signup = ObjectAnimator.ofFloat(binding.btSignup, View.ALPHA, 1f).setDuration(200)
        val orLine = ObjectAnimator.ofFloat(binding.orDivider, View.ALPHA, 1f).setDuration(200)
        val loginGoogle = ObjectAnimator.ofFloat(binding.bLoginGoogle, View.ALPHA, 1f).setDuration(200)

        // Gabungkan animasi tombol login dan signup untuk berjalan bersamaan
        val together = AnimatorSet().apply {
            playTogether(login, signup)
        }

        // Atur urutan animasi secara keseluruhan
        AnimatorSet().apply {
            playSequentially(title, desc, together, orLine, loginGoogle)
            start()
        }
    }
}
