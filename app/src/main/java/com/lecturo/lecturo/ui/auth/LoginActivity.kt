package com.lecturo.lecturo.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
        setupPhoneFormatting() // Fitur spasi otomatis (UX Baru)
        setupListeners()
        setupBackPressHandler()
    }

    // --- UX BARU 1: Auto-Formatting Spasi (Mirip Telegram) ---
    // --- UX BARU 1: Auto-Formatting Spasi & Koreksi Otomatis (Gaya Telegram) ---
    private fun setupPhoneFormatting() {
        binding.phoneEditText.addTextChangedListener(object : TextWatcher {
            private var isUpdating = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isUpdating) return
                isUpdating = true

                // 1. Ambil teks murni (hapus semua spasi dari ketikan sebelumnya)
                var rawText = s.toString().replace(" ", "")

                // 2. UX AJAIB: Koreksi Kesalahan Ketik Otomatis!
                // Jika user iseng ketik '0' di awal -> Langsung buang!
                // Jika user copas '6282...' atau '+6282...' -> Langsung sesuaikan!
                when {
                    rawText.startsWith("0") -> rawText = rawText.substring(1)
                    rawText.startsWith("62") -> rawText = rawText.substring(2)
                    rawText.startsWith("+62") -> rawText = rawText.substring(3)
                }

                // 3. Batasi maksimal 12 angka (Panjang maksimal nomor HP di Indonesia setelah +62)
                if (rawText.length > 12) {
                    rawText = rawText.substring(0, 12)
                }

                // 4. Format ulang dengan spasi (Pola: 3 - 4 - 4/5)
                // Pola ini lebih natural untuk orang Indonesia membaca nomor HP
                val formattedString = java.lang.StringBuilder()
                for (i in rawText.indices) {
                    // Beri spasi setelah angka ke-3 dan ke-7
                    // Contoh: 812 3456 7890 atau 813 1234 56789
                    if (i == 3 || i == 7) {
                        formattedString.append(" ")
                    }
                    formattedString.append(rawText[i])
                }

                // 5. Perbarui tampilan layar dan paksa kursor selalu berada di posisi paling kanan
                binding.phoneEditText.setText(formattedString.toString())
                binding.phoneEditText.setSelection(formattedString.length)

                isUpdating = false
            }
        })
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
        // TOMBOL 1: REQUEST OTP DENGAN DIALOG KONFIRMASI (UX Baru)
        binding.btnSendOtp.setOnClickListener {
            // Ambil nomor yang diketik user (masih ada spasinya)
            val rawPhoneWithSpaces = binding.phoneEditText.text.toString().trim()

            if (rawPhoneWithSpaces.isEmpty()) {
                binding.phoneEditTextLayout.error = "Nomor tidak boleh kosong"
                return@setOnClickListener
            }

            binding.phoneEditTextLayout.error = null // Bersihkan error jika ada

            // --- UX BARU 2: Dialog Konfirmasi Telegram-style ---
            MaterialAlertDialogBuilder(this)
                .setTitle("Apakah nomor ini sudah benar?")
                .setMessage("+62 $rawPhoneWithSpaces")
                .setNegativeButton("Edit") { dialog, _ ->
                    // Kembali ke halaman untuk diedit (tutup dialog saja)
                    dialog.dismiss()
                }
                .setPositiveButton("Ya") { dialog, _ ->
                    dialog.dismiss()
                    // Lanjutkan proses format logic dan kirim ke backend
                    processAndSendOtp(rawPhoneWithSpaces)
                }
                .show()
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

    // --- FUNGSI BARU: Memisahkan Logika Pemrosesan Nomor dari Tombol ---
    private fun processAndSendOtp(rawPhoneWithSpaces: String) {
        // 1. Hapus karakter aneh (spasi dari auto-format, strip, dll)
        val cleanInput = rawPhoneWithSpaces.replace(Regex("[^0-9]"), "")

        // 2. Pastikan formatnya selalu "628..."
        currentPhoneNumber = when {
            cleanInput.startsWith("0") -> "62" + cleanInput.substring(1)
            cleanInput.startsWith("8") -> "62" + cleanInput
            cleanInput.startsWith("62") -> cleanInput
            else -> cleanInput
        }

        Log.d("LOGIN_DEBUG", "Input: $rawPhoneWithSpaces -> Formatted: $currentPhoneNumber")

        // 3. Tembak API
        requestOtpToBackend(currentPhoneNumber)
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

                    val finalPhone = "+$currentPhoneNumber"

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