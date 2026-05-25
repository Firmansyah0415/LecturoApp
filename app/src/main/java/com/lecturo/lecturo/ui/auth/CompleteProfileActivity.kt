package com.lecturo.lecturo.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.lecturo.lecturo.databinding.ActivityCompleteProfileBinding
import com.lecturo.lecturo.di.ViewModelFactory
import com.lecturo.lecturo.ui.base.BaseActivity
import com.lecturo.lecturo.ui.main.MainActivity
import com.lecturo.lecturo.utils.DataRestoreManager
import com.lecturo.lecturo.viewmodel.auth.CompleteProfileViewModel
import kotlinx.coroutines.launch

class CompleteProfileActivity : BaseActivity() {

    private lateinit var binding: ActivityCompleteProfileBinding
    private lateinit var auth: FirebaseAuth

    // Inisialisasi ViewModel
    private val viewModel by viewModels<CompleteProfileViewModel> {
        ViewModelFactory.getInstance(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCompleteProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        setupToolbar()
        setupListeners()
        setupGenderDropdown()
    }

    // TAMBAHKAN FUNGSI INI
    private fun setupGenderDropdown() {
        val genders = arrayOf("Laki-laki", "Perempuan")
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genders)
        binding.etGender.setAdapter(adapter)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarProfile)
        supportActionBar?.apply {
            title = "Lengkapi Biodata"
            // Kita matikan tombol back agar user WAJIB mengisi data ini
            setDisplayHomeAsUpEnabled(false)
        }
    }

    private fun setupListeners() {
        binding.btnSaveProfile.setOnClickListener {
            // 1. Ambil data dari Input
            val fullName = binding.etFullName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val university = binding.etUniversity.text.toString().trim()
            val faculty = binding.etFaculty.text.toString().trim()
            val major = binding.etMajor.text.toString().trim()
            val gender = binding.etGender.text.toString().trim()

            // 2. Ambil User ID dari Firebase
            val currentUser = auth.currentUser
            val uid = currentUser?.uid
            val phoneNumber = currentUser?.phoneNumber

            // Jika sesi hilang, kembalikan ke Login
            if (uid == null || phoneNumber == null) {
                Toast.makeText(this, "Sesi habis, silakan login ulang", Toast.LENGTH_SHORT).show()
                finish()
                return@setOnClickListener
            }

            // 3. Validasi Input (Wajib diisi)
            var isValid = true

            if (fullName.isEmpty()) {
                binding.tilFullName.error = "Nama lengkap wajib diisi"
                isValid = false
            } else {
                binding.tilFullName.error = null
            }

            if (gender.isEmpty()) {
                binding.tilGender.error = "Jenis kelamin wajib dipilih"
                isValid = false
            } else {
                binding.tilGender.error = null
            }

            if (university.isEmpty()) {
                binding.tilUniversity.error = "Nama kampus wajib diisi"
                isValid = false
            } else {
                binding.tilUniversity.error = null
            }

            if (faculty.isEmpty()) {
                binding.tilFaculty.error = "Fakultas wajib diisi"
                isValid = false
            } else {
                binding.tilFaculty.error = null
            }

            if (major.isEmpty()) {
                binding.tilMajor.error = "Jurusan wajib diisi"
                isValid = false
            } else {
                binding.tilMajor.error = null
            }

            // 4. Jika Valid, Kirim ke ViewModel
            if (isValid) {
                viewModel.saveProfile(
                    uid = uid,
                    phoneNumber = phoneNumber,
                    fullName = fullName,
                    gender = gender,
                    email = email,
                    university = university,
                    faculty = faculty,
                    major = major,
                    onLoading = { isLoading ->
                        showLoading(isLoading)
                    },
                    onSuccess = {
                        Toast.makeText(this, "Profil tersimpan!", Toast.LENGTH_SHORT).show()
                        navigateToHome()
                    },
                    onError = { errorMsg ->
                        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSaveProfile.isEnabled = !isLoading

        // Kunci input saat loading agar tidak diedit
        binding.etFullName.isEnabled = !isLoading
        binding.etEmail.isEnabled = !isLoading
        binding.etUniversity.isEnabled = !isLoading
        binding.etFaculty.isEnabled = !isLoading
        binding.etMajor.isEnabled = !isLoading
    }

    // === PERUBAHAN UTAMA ADA DI SINI ===
    private fun navigateToHome() {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            // Tampilkan loading (reuse fungsi showLoading yang sudah ada)
            showLoading(true)

            lifecycleScope.launch {
                // 1. Panggil Helper Restore Data
                val restoreManager = DataRestoreManager(this@CompleteProfileActivity)

                // Proses restore (walaupun user baru mungkin datanya kosong, tidak apa-apa)
                restoreManager.restoreUserData(uid)

                // 2. Matikan loading (tidak perlu UI update karena akan finish)
                // showLoading(false)

                // 3. Pindah ke MainActivity
                val intent = Intent(this@CompleteProfileActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        } else {
            // Fallback safety
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}