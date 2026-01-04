package com.lecturo.lecturo.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide // Import Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy // Import Cache Strategy
import com.lecturo.lecturo.R
import com.lecturo.lecturo.databinding.FragmentProfileBinding
import com.lecturo.lecturo.di.ViewModelFactory
import com.lecturo.lecturo.ui.auth.LoginActivity
import com.lecturo.lecturo.viewmodel.main.MainViewModel
import com.lecturo.lecturo.viewmodel.profile.ProfileViewModel // Import ProfileViewModel

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    // 1. ViewModel untuk Logout (Tetap pakai MainViewModel)
    private val mainViewModel by viewModels<MainViewModel> {
        ViewModelFactory.getInstance(requireContext())
    }

    // 2. ViewModel untuk Load Data Profil (Pakai ProfileViewModel yang sudah canggih)
    private val profileViewModel by viewModels<ProfileViewModel> {
        ViewModelFactory.getInstance(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupObserver()
        setupActions()
    }

    // --- PENTING: Panggil Load Data saat fragment muncul kembali ---
    override fun onResume() {
        super.onResume()
        // Ini akan mengambil data terbaru dari Firestore setiap kali halaman ini tampil
        profileViewModel.loadUserProfile()
    }

    private fun setupObserver() {
        // A. Observer untuk Data User LENGKAP dari Firestore (via ProfileViewModel)
        profileViewModel.currentUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                // Tampilkan Nama Lengkap (bukan cuma dari email)
                binding.tvUserName.text = if (user.fullName.isNotEmpty()) user.fullName else user.phoneNumber

                // Tampilkan Foto Profil dengan Glide
                if (!isDetached && context != null) {
                    Glide.with(requireContext())
                        .load(user.photoUrl)
                        .placeholder(R.drawable.profile_logo) // Gambar default
                        .error(R.drawable.profile_logo)
                        .circleCrop()
                        // Trik agar foto langsung berubah (Skip Cache Memory)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .into(binding.ivProfileImage)
                }
            }
        }

        // B. Observer untuk Session (via MainViewModel) - Opsional, untuk backup
        mainViewModel.getSession().observe(viewLifecycleOwner) { userModel ->
            // Kita gunakan ini hanya untuk memastikan user masih login
            if (!userModel.isLogin) {
                navigateToLogin()
            }
        }
    }

    private fun setupActions() {
        binding.btnMyProfile.setOnClickListener {
            val intent = Intent(requireContext(), ProfileActivity::class.java)
            startActivity(intent)
        }

        binding.btnAbout.setOnClickListener {
            Toast.makeText(requireContext(), "Aplikasi Skripsi Lecturo v1.0", Toast.LENGTH_SHORT).show()
        }

        binding.btnLogout.setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Konfirmasi Keluar")
            .setMessage("Apakah Anda yakin ingin keluar dari akun?")
            .setPositiveButton("Ya, Keluar") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun performLogout() {
        mainViewModel.logout {
            navigateToLogin()
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}