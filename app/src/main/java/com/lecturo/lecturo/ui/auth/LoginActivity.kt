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
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.lecturo.lecturo.databinding.ActivityLoginBinding
import com.lecturo.lecturo.di.ViewModelFactory
import com.lecturo.lecturo.ui.main.MainActivity
import com.lecturo.lecturo.utils.DataRestoreManager
import com.lecturo.lecturo.viewmodel.auth.LoginViewModel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    // Inisialisasi ViewModel
    private val viewModel by viewModels<LoginViewModel> {
        ViewModelFactory.getInstance(this)
    }

    private var storedVerificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // 1. Cek User Login (Session Persistence)
        if (auth.currentUser != null) {
            // Kita asumsikan user yang sudah login session-nya valid dan profil sudah ada
            // Jika ingin lebih ketat, bisa cek ke database lagi, tapi untuk awalan ini cukup.
            navigateToHome()
            return // Stop eksekusi onCreate agar tidak setup listener yang tidak perlu
        }

        setupToolbar()
        setupListeners()
        setupBackPressHandler() // <-- FUNGSI BARU UNTUK HANDLE BACK BUTTON
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarLogin)
        supportActionBar?.apply {
            title = "" // Judul kosong agar rapi
            setDisplayHomeAsUpEnabled(true)
        }

        // Handle klik tombol panah Back di pojok kiri atas
        binding.toolbarLogin.setNavigationOnClickListener {
            handleBackPress()
        }
    }

    // --- LOGIKA TOMBOL BACK (PENTING) ---
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })
    }

    private fun handleBackPress() {
        // Jika sedang di tampilan OTP (Visible), kembalikan ke tampilan Nomor HP
        if (binding.layoutInputOtp.visibility == View.VISIBLE) {
            binding.layoutInputOtp.visibility = View.GONE
            binding.layoutInputPhone.visibility = View.VISIBLE

            // Reset UI
            binding.tvSubtitle.text = "Masuk dengan nomor HP Anda untuk melanjutkan."
            binding.otpEditText.text?.clear()

            // Reset state verifikasi (opsional, tergantung kebutuhan)
            setLoading(false)
        } else {
            // Jika sedang di tampilan awal, tutup aplikasi (Exit)
            finish()
        }
    }

    private fun setupListeners() {
        // Tombol Kirim OTP
        binding.btnSendOtp.setOnClickListener {
            val rawPhone = binding.phoneEditText.text.toString().trim()
            if (rawPhone.isEmpty()) {
                binding.phoneEditTextLayout.error = "Nomor tidak boleh kosong"
                return@setOnClickListener
            }

            // Format nomor: +62
            val phoneNumber = if (rawPhone.startsWith("0")) {
                "+62" + rawPhone.substring(1)
            } else {
                "+62$rawPhone"
            }

            sendVerificationCode(phoneNumber)
        }

        // Tombol Verifikasi OTP
        binding.btnLogin.setOnClickListener {
            val code = binding.otpEditText.text.toString().trim()
            if (code.isEmpty() || code.length < 6) {
                binding.otpEditTextLayout.error = "Kode tidak valid"
                return@setOnClickListener
            }

            if (storedVerificationId != null) {
                verifyCode(code)
            } else {
                Toast.makeText(this, "ID Verifikasi hilang, silakan kirim ulang OTP", Toast.LENGTH_SHORT).show()
            }
        }

        // Tombol Kirim Ulang
        binding.btnResendOtp.setOnClickListener {
            val phoneText = binding.tvOtpSentTo.text.toString()
            // Ambil nomor dari teks "Kode dikirim ke +62..."
            if (phoneText.contains("+62")) {
                val phone = phoneText.replace("Kode dikirim ke ", "").trim()
                sendVerificationCode(phone)
            } else {
                // Fallback jika parsing gagal, user suruh input ulang
                Toast.makeText(this, "Nomor tidak valid, silakan input ulang.", Toast.LENGTH_SHORT).show()
                handleBackPress()
            }
        }
    }

    private fun sendVerificationCode(phoneNumber: String) {
        setLoading(true)
        binding.phoneEditTextLayout.error = null // Clear error sebelumnya

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)

        // Simpan nomor sementara di teks agar bisa dipakai untuk Resend
        binding.tvOtpSentTo.text = "Kode dikirim ke $phoneNumber"
    }

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            // Verifikasi otomatis (jarang terjadi di beberapa device/provider, tapi bagus jika ada)
            Log.d("LOGIN", "Auto verification success")
            signInWithPhoneAuthCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            setLoading(false)
            Log.e("LOGIN", "Gagal kirim OTP", e)

            // Tampilkan pesan error yang user-friendly
            val msg = if (e is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
                "Format nomor telepon salah."
            } else {
                e.message ?: "Gagal mengirim kode."
            }
            Toast.makeText(baseContext, msg, Toast.LENGTH_LONG).show()
        }

        override fun onCodeSent(
            verificationId: String,
            token: PhoneAuthProvider.ForceResendingToken
        ) {
            setLoading(false)
            Log.d("LOGIN", "OTP Terkirim: $verificationId")

            storedVerificationId = verificationId
            resendToken = token

            // --- ANIMASI PERPINDAHAN VIEW (FLIP VIEW) ---
            binding.layoutInputPhone.visibility = View.GONE
            binding.layoutInputOtp.visibility = View.VISIBLE

            // Ubah subtitle agar user tahu konteks
            binding.tvSubtitle.text = "Masukkan 6 digit kode yang kami kirim via SMS/WhatsApp."

            Toast.makeText(baseContext, "Kode OTP terkirim!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun verifyCode(code: String) {
        setLoading(true)
        binding.otpEditTextLayout.error = null // Clear error

        try {
            val credential = PhoneAuthProvider.getCredential(storedVerificationId!!, code)
            signInWithPhoneAuthCredential(credential)
        } catch (e: Exception) {
            setLoading(false)
            binding.otpEditTextLayout.error = "Format kode salah"
        }
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = task.result?.user
                    val uid = user?.uid ?: ""
                    val phone = user?.phoneNumber ?: ""

                    // Panggil ViewModel untuk Sync ke Backend
                    viewModel.handleLoginSuccess(
                        firebaseUid = uid,
                        phoneNumber = phone,
                        onSuccess = { isProfileComplete ->
                            if (isProfileComplete) {
                                Log.d("LOGIN", "User Lama -> Home")
                                navigateToHome()
                            } else {
                                Log.d("LOGIN", "User Baru -> Complete Profile")
                                navigateToCompleteProfile()
                            }
                        },
                        onError = { errorMsg: String ->
                            setLoading(false)
                            Toast.makeText(baseContext, errorMsg, Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    setLoading(false)
                    val message = task.exception?.message ?: "Verifikasi gagal"

                    if (task.exception is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
                        binding.otpEditTextLayout.error = "Kode OTP salah."
                    } else {
                        binding.otpEditTextLayout.error = message
                    }
                }
            }
    }

    // === PERUBAHAN UTAMA ADA DI SINI ===
    private fun navigateToHome() {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            // Pastikan loading tampil saat proses restore
            setLoading(true)

            lifecycleScope.launch {
                // 1. Panggil Helper Restore Data
                val restoreManager = DataRestoreManager(this@LoginActivity)
                val result = restoreManager.restoreUserData(uid)

                // 2. Selesai restore, matikan loading
                setLoading(false)

                // 3. Beri feedback ke user
                if (result.isSuccess) {
                    Toast.makeText(this@LoginActivity, "Data berhasil dipulihkan!", Toast.LENGTH_SHORT).show()
                } else {
                    // Jika gagal (misal offline), tetap lanjut masuk Home tapi toast error
                    Toast.makeText(this@LoginActivity, "Offline Mode: Gagal memulihkan data lama.", Toast.LENGTH_SHORT).show()
                }

                // 4. Pindah ke MainActivity
                val intent = Intent(this@LoginActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        } else {
            // Fallback jika UID null (harusnya tidak terjadi)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun navigateToCompleteProfile() {
        val intent = Intent(this, com.lecturo.lecturo.ui.auth.CompleteProfileActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE

        // Disable semua tombol saat loading agar tidak double click
        binding.btnSendOtp.isEnabled = !isLoading
        binding.btnLogin.isEnabled = !isLoading
        binding.btnResendOtp.isEnabled = !isLoading
        binding.phoneEditText.isEnabled = !isLoading
        binding.otpEditText.isEnabled = !isLoading
    }
}