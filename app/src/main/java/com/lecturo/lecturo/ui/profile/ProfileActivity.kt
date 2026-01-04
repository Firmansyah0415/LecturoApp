package com.lecturo.lecturo.ui.profile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.model.User
import com.lecturo.lecturo.databinding.ActivityProfileBinding // Pastikan nama file XML Anda activity_profile.xml
import com.lecturo.lecturo.di.ViewModelFactory
import com.lecturo.lecturo.viewmodel.profile.ProfileViewModel

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    // Gunakan ViewModelFactory (Gaya Lecturo)
    private val viewModel by viewModels<ProfileViewModel> {
        ViewModelFactory.getInstance(this)
    }

    private var currentUser: User? = null
    private var selectedImageUri: Uri? = null
    private var isEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        setupToolbar()
        setupUI()
        observeViewModel()

        // Mulai dengan Mode View (Baca Saja)
        setEditMode(false)

        // Load data user
        viewModel.loadUserProfile()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false) // Judul diatur custom di XML

        // Tombol Back di Toolbar
        binding.toolbar.setNavigationOnClickListener {
            handleBackPress()
        }

        // Handle Back System (Gestur / Tombol Fisik)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })
    }

    private fun handleBackPress() {
        if (isEditMode) {
            // Jika sedang edit, batalkan edit dan kembalikan data asli
            setEditMode(false)
            currentUser?.let { displayUserProfile(it) }
        } else {
            finish()
        }
    }

    private fun setupUI() {
        binding.apply {
            // Tombol Edit (Icon Pencil di Toolbar)
            btnEditProfileIcon.setOnClickListener {
                setEditMode(true)
            }

            // Tombol Ganti Foto
            btnChangePhoto.setOnClickListener { checkPermissionsAndPickImage() }

            // Tombol Simpan
            btnSave.setOnClickListener { saveProfile() }

            // Tombol Batal
            btnCancel.setOnClickListener {
                setEditMode(false)
                // Reset data ke awal
                currentUser?.let { displayUserProfile(it) }
            }
        }
    }

    // --- LOGIKA UTAMA: EDIT MODE TOGGLE (Adaptasi dari Tara) ---
    private fun setEditMode(enable: Boolean) {
        isEditMode = enable
        binding.apply {
            // 1. Visibilitas Tombol Aksi Bawah
            val visibility = if (enable) View.VISIBLE else View.GONE
            btnSave.visibility = visibility
            btnCancel.visibility = visibility

            // 2. Visibilitas Ganti Foto
            btnChangePhoto.visibility = visibility

            // 3. Visibilitas Icon Edit di Toolbar
            btnEditProfileIcon.visibility = if (enable) View.GONE else View.VISIBLE

            // 4. Enable/Disable EditText
            // Note: etPhone biasanya READ ONLY karena itu ID Login, jadi kita bisa keep false atau true
            val textInputs = listOf(etName, etEmail, etUniversity, etFaculty, etMajor)
            textInputs.forEach { it.isEnabled = enable }

            // Khusus Phone: Biarkan disabled agar user tidak ganti nomor sembarangan (keamanan)
            etPhone.isEnabled = false

            if (enable) {
                etName.requestFocus()
            }
        }
    }

    private fun observeViewModel() {
        // Observer Data User
        viewModel.currentUser.observe(this) { user ->
            if (user != null) {
                displayUserProfile(user)
            }
        }

        // Observer Loading
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            // Disable tombol saat loading
            binding.btnSave.isEnabled = !isLoading
            binding.btnEditProfileIcon.isEnabled = !isLoading
        }

        // Observer Pesan (Toast)
        viewModel.message.observe(this) { msg ->
            if (!msg.isNullOrEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayUserProfile(user: User) {
        currentUser = user
        binding.apply {
            etName.setText(user.fullName)
            etPhone.setText(user.phoneNumber)
            etEmail.setText(user.email)
            etUniversity.setText(user.university)
            etFaculty.setText(user.faculty)
            etMajor.setText(user.major)

            // Load Foto dengan Glide
            if (!isDestroyed) {
                Glide.with(this@ProfileActivity)
                    .load(user.photoUrl) // Pastikan field ini ada di Model User
                    .placeholder(R.drawable.profile_logo)
                    .error(R.drawable.profile_logo)
                    .circleCrop()
                    // Tambahkan ini agar Glide tidak menyimpan cache gambar profil lama
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .into(ivProfilePhoto)
            }
        }
    }

    // --- LOGIKA IMAGE PICKER (Sama persis dengan Tara) ---
    private val pickMediaLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            // Beri izin permanen
            val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
            try {
                contentResolver.takePersistableUriPermission(it, flag)
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
            Glide.with(this).load(it).circleCrop().into(binding.ivProfilePhoto)
        }
    }

    private val galleryLauncherLegacy = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            Glide.with(this).load(it).circleCrop().into(binding.ivProfilePhoto)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            galleryLauncherLegacy.launch("image/*")
        } else {
            Toast.makeText(this, "Izin akses galeri dibutuhkan", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissionsAndPickImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else {
            val permission = Manifest.permission.READ_EXTERNAL_STORAGE
            when {
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                    galleryLauncherLegacy.launch("image/*")
                }
                shouldShowRequestPermissionRationale(permission) -> {
                    Toast.makeText(this, "Izinkan akses galeri untuk mengganti foto profil", Toast.LENGTH_LONG).show()
                    permissionLauncher.launch(permission)
                }
                else -> {
                    permissionLauncher.launch(permission)
                }
            }
        }
    }

    // --- LOGIKA SIMPAN ---
    private fun saveProfile() {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val uni = binding.etUniversity.text.toString().trim()
        val faculty = binding.etFaculty.text.toString().trim()
        val major = binding.etMajor.text.toString().trim()

        // Validasi Sederhana
        if (name.isEmpty()) {
            binding.etName.error = "Nama tidak boleh kosong"
            return
        }

        // Panggil ViewModel
        viewModel.updateProfile(
            name = name,
            email = email,
            phone = phone,
            university = uni,
            faculty = faculty,
            major = major,
            photoUri = selectedImageUri,
            onSuccess = {
                // Jika sukses, matikan mode edit
                setEditMode(false)
            }
        )
    }
}