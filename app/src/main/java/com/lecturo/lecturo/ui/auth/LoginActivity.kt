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
import com.lecturo.lecturo.R
import com.lecturo.lecturo.databinding.ActivityLoginBinding
import com.lecturo.lecturo.di.ViewModelFactory
import com.lecturo.lecturo.ui.main.MainActivity
import com.lecturo.lecturo.utils.DataRestoreManager
import com.lecturo.lecturo.viewmodel.auth.LoginViewModel
import kotlinx.coroutines.launch

// --- IMPORT COMPOSE ---
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lecturo.lecturo.ui.base.BaseActivity

class LoginActivity : BaseActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private val viewModel by viewModels<LoginViewModel> { ViewModelFactory.getInstance(this) }

    // Variabel untuk menyimpan nomor HP yang sedang diproses
    private var currentPhoneNumber: String = ""

    // Variabel penampung dialog konfirmasi
    private var confirmDialog: androidx.appcompat.app.AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        if (auth.currentUser != null) {
            navigateToHome()
            return
        }

        setupToolbar()
        setupPhoneFormatting()
        setupListeners()
        setupBackPressHandler()
    }

    private fun setupPhoneFormatting() {
        binding.phoneEditText.addTextChangedListener(object : TextWatcher {
            private var isUpdating = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isUpdating) return
                isUpdating = true

                var rawText = s.toString().replace(" ", "")

                when {
                    rawText.startsWith("0") -> rawText = rawText.substring(1)
                    rawText.startsWith("62") -> rawText = rawText.substring(2)
                    rawText.startsWith("+62") -> rawText = rawText.substring(3)
                }

                if (rawText.length > 12) {
                    rawText = rawText.substring(0, 12)
                }

                val formattedString = java.lang.StringBuilder()
                for (i in rawText.indices) {
                    if (i == 3 || i == 7) {
                        formattedString.append(" ")
                    }
                    formattedString.append(rawText[i])
                }

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
        binding.btnSendOtp.setOnClickListener {
            val rawPhoneWithSpaces = binding.phoneEditText.text.toString().trim()

            if (rawPhoneWithSpaces.isEmpty()) {
                binding.phoneEditTextLayout.error = "Nomor tidak boleh kosong"
                return@setOnClickListener
            }

            binding.phoneEditTextLayout.error = null

            // PANGGIL DIALOG COMPOSE BARU
            showConfirmPhoneDialog(rawPhoneWithSpaces)
        }

        binding.btnLogin.setOnClickListener {
            val code = binding.otpEditText.text.toString().trim()
            if (code.isEmpty() || code.length < 4) {
                binding.otpEditTextLayout.error = "Kode minimal 4 digit"
                return@setOnClickListener
            }
            verifyOtpToBackend(code)
        }

        binding.btnResendOtp.setOnClickListener {
            requestOtpToBackend(currentPhoneNumber)
        }
    }

    // ==========================================
    // LOGIKA PEMANGGILAN DIALOG COMPOSE
    // ==========================================
    private fun showConfirmPhoneDialog(rawPhoneWithSpaces: String) {
        val composeView = ComposeView(this).apply {
            setContent {
                MaterialTheme {
                    ConfirmPhoneComposeDialog(
                        phoneNumber = "+62 $rawPhoneWithSpaces",
                        onEdit = { confirmDialog?.dismiss() },
                        onConfirm = {
                            confirmDialog?.dismiss()
                            processAndSendOtp(rawPhoneWithSpaces)
                        }
                    )
                }
            }
        }

        confirmDialog = MaterialAlertDialogBuilder(this)
            .setView(composeView)
            .setBackground(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            .create()

        confirmDialog?.show()
    }

    private fun processAndSendOtp(rawPhoneWithSpaces: String) {
        val cleanInput = rawPhoneWithSpaces.replace(Regex("[^0-9]"), "")

        currentPhoneNumber = when {
            cleanInput.startsWith("0") -> "62" + cleanInput.substring(1)
            cleanInput.startsWith("8") -> "62" + cleanInput
            cleanInput.startsWith("62") -> cleanInput
            else -> cleanInput
        }

        Log.d("LOGIN_DEBUG", "Input: $rawPhoneWithSpaces -> Formatted: $currentPhoneNumber")
        requestOtpToBackend(currentPhoneNumber)
    }

    private fun requestOtpToBackend(phone: String) {
        setLoading(true)
        binding.phoneEditTextLayout.error = null

        viewModel.requestOtp(phone,
            onSuccess = {
                setLoading(false)
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

    private fun signInWithCustomToken(token: String) {
        Log.d("LOGIN", "Mencoba login dengan Custom Token...")
        auth.signInWithCustomToken(token)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
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

// ==========================================
// 🚀 JETPACK COMPOSE UI COMPONENT (DIALOG)
// ==========================================
@Composable
fun ConfirmPhoneComposeDialog(phoneNumber: String, onEdit: () -> Unit, onConfirm: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colorResource(id = R.color.card_background)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Konfirmasi Nomor",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                color = colorResource(id = R.color.colorPrimary),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = "Pastikan nomor WhatsApp ini aktif dan sudah benar untuk menerima OTP.",
                fontSize = 14.sp,
                color = colorResource(id = R.color.text_primary),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // KOTAK PENONJOL NOMOR HP (Highlighter)
            Surface(
                color = colorResource(id = R.color.colorPrimaryContainer).copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            ) {
                Text(
                    text = phoneNumber,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    letterSpacing = 1.sp, // Memberi jarak antar angka agar lebih mudah dibaca
                    color = colorResource(id = R.color.colorPrimary),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Tombol Edit
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Edit Nomor",
                        color = colorResource(id = R.color.text_secondary),
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Tombol Lanjut
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = colorResource(id = R.color.colorPrimary)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Ya, Lanjut",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}