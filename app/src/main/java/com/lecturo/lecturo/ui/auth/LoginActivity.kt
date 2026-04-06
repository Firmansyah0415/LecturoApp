package com.lecturo.lecturo.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.lecturo.lecturo.databinding.ActivityLoginBinding
import com.lecturo.lecturo.di.ViewModelFactory
import com.lecturo.lecturo.ui.main.MainActivity
import com.lecturo.lecturo.utils.DataRestoreManager
import com.lecturo.lecturo.viewmodel.auth.LoginViewModel
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private val viewModel by viewModels<LoginViewModel> { ViewModelFactory.getInstance(this) }

    // Variabel untuk menyimpan nomor HP yang sedang diproses
    private var currentPhoneNumber: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Cek Session
        if (auth.currentUser != null) {
            navigateToHome()
            return
        }

        setupToolbar()
        setupListeners()
        setupBackPressHandler()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarLogin)
        supportActionBar?.apply {
            title = ""
            setDisplayHomeAsUpEnabled(true)
        }
        binding.toolbarLogin.setNavigationOnClickListener { handleBackPress() }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { handleBackPress() }
        })
    }

    private fun handleBackPress() {
        if (binding.layoutInputOtp.visibility == View.VISIBLE) {
            binding.layoutInputOtp.visibility = View.GONE
            binding.layoutInputPhone.visibility = View.VISIBLE
            binding.tvSubtitle.text = "Masuk dengan nomor HP Anda untuk melanjutkan."
            binding.otpEditText.text?.clear()
            setLoading(false)
        } else {
            finish()
        }
    }

    private fun setupListeners() {
        // TOMBOL 1: REQUEST OTP (Ke Backend Node.js)
        binding.btnSendOtp.setOnClickListener {
            val rawPhone = binding.phoneEditText.text.toString().trim()
            if (rawPhone.isEmpty()) {
                binding.phoneEditTextLayout.error = "Nomor tidak boleh kosong"
                return@setOnClickListener
            }

            // --- PERBAIKAN LOGIKA FORMAT NOMOR ---
            // Kita pastikan formatnya selalu "628..." (Tanpa +) untuk currentPhoneNumber

            // 1. Hapus karakter aneh (spasi, strip, dll)
            var cleanInput = rawPhone.replace(Regex("[^0-9]"), "")

            currentPhoneNumber = when {
                // Kasus: 0822... -> 62822...
                cleanInput.startsWith("0") -> "62" + cleanInput.substring(1)

                // Kasus: 822... -> 62822... (INI YANG KEMARIN ERROR)
                cleanInput.startsWith("8") -> "62" + cleanInput

                // Kasus: 62822... -> Biarkan (Sudah benar)
                cleanInput.startsWith("62") -> cleanInput

                // Fallback (jarang terjadi)
                else -> cleanInput
            }

            // Debug Log untuk memastikan
            Log.d("LOGIN_DEBUG", "Input: $rawPhone -> Formatted: $currentPhoneNumber")

            requestOtpToBackend(currentPhoneNumber)
        }

        // TOMBOL 2: VERIFY OTP (Ke Backend Node.js -> Dapat Token -> Login Firebase)
        binding.btnLogin.setOnClickListener {
            val code = binding.otpEditText.text.toString().trim()
            if (code.isEmpty() || code.length < 4) { // OTP kita 4 digit
                binding.otpEditTextLayout.error = "Kode minimal 4 digit"
                return@setOnClickListener
            }
            verifyOtpToBackend(code)
        }

        // TOMBOL 3: RESEND OTP
        binding.btnResendOtp.setOnClickListener {
            requestOtpToBackend(currentPhoneNumber)
        }
    }

    // --- LOGIKA BARU: Request OTP ke API ---
    private fun requestOtpToBackend(phone: String) {
        setLoading(true)
        binding.phoneEditTextLayout.error = null

        viewModel.requestOtp(phone,
            onSuccess = {
                setLoading(false)
                // Pindah UI ke Input OTP
                binding.layoutInputPhone.visibility = View.GONE
                binding.layoutInputOtp.visibility = View.VISIBLE
                binding.tvSubtitle.text = "Masukkan kode OTP yang dikirim oleh Bot WhatsApp."
                binding.tvOtpSentTo.text = "Kode dikirim ke +$phone"
                Toast.makeText(this, "OTP Terkirim ke WhatsApp!", Toast.LENGTH_SHORT).show()
            },
            onError = { msg ->
                setLoading(false)
                Toast.makeText(this, "Gagal: $msg", Toast.LENGTH_LONG).show()
            }
        )
    }

    // --- LOGIKA BARU: Verify OTP ke API & Login Firebase ---
    private fun verifyOtpToBackend(code: String) {
        setLoading(true)
        binding.otpEditTextLayout.error = null

        viewModel.verifyOtp(currentPhoneNumber, code,
            onSuccess = { customToken ->
                // KITA DAPAT TIKET VIP (CUSTOM TOKEN)
                // SEKARANG TUKARKAN TIKET ITU KE FIREBASE AUTH
                signInWithCustomToken(customToken)
            },
            onError = { msg ->
                setLoading(false)
                binding.otpEditTextLayout.error = msg
            }
        )
    }

    // --- FINAL STEP: Login Resmi ke Firebase ---
    private fun signInWithCustomToken(token: String) {
        Log.d("LOGIN", "Mencoba login dengan Custom Token...")
        auth.signInWithCustomToken(token)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("LOGIN", "Login Firebase Berhasil!")
                    val user = auth.currentUser
                    val uid = user?.uid ?: ""
                    // Gunakan nomor HP yang kita punya karena custom token kadang tidak return phone di object user langsung
                    val finalPhone = "+$currentPhoneNumber"

                    // Lanjut ke Sync User (Logika lama Anda)
                    viewModel.handleLoginSuccess(
                        firebaseUid = uid,
                        phoneNumber = finalPhone,
                        onSuccess = { isProfileComplete ->
                            if (isProfileComplete) navigateToHome() else navigateToCompleteProfile()
                        },
                        onError = { errorMsg ->
                            setLoading(false)
                            Toast.makeText(baseContext, errorMsg, Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    setLoading(false)
                    Log.e("LOGIN", "Firebase Auth Failed", task.exception)
                    Toast.makeText(this, "Gagal otentikasi: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun navigateToHome() {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            setLoading(true)
            lifecycleScope.launch {
                val restoreManager = DataRestoreManager(this@LoginActivity)
                val result = restoreManager.restoreUserData(uid)
                setLoading(false)
                if (result.isSuccess) Toast.makeText(this@LoginActivity, "Data dipulihkan!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this@LoginActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
    }

    private fun navigateToCompleteProfile() {
        val intent = Intent(this, CompleteProfileActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSendOtp.isEnabled = !isLoading
        binding.btnLogin.isEnabled = !isLoading
        binding.btnResendOtp.isEnabled = !isLoading
        binding.phoneEditText.isEnabled = !isLoading
        binding.otpEditText.isEnabled = !isLoading
    }
}