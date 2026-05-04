package com.lecturo.lecturo.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.databinding.FragmentProfileBinding
import com.lecturo.lecturo.di.ViewModelFactory
import com.lecturo.lecturo.ui.auth.LoginActivity
import com.lecturo.lecturo.viewmodel.main.MainViewModel
import com.lecturo.lecturo.viewmodel.profile.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel by viewModels<MainViewModel> {
        ViewModelFactory.getInstance(requireContext())
    }

    private val profileViewModel by viewModels<ProfileViewModel> {
        ViewModelFactory.getInstance(requireContext())
    }

    // Variabel penampung dialog logout
    private var logoutDialog: androidx.appcompat.app.AlertDialog? = null

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

        if (profileViewModel.currentUser.value == null) {
            profileViewModel.loadUserProfile()
        }
    }

    private fun setupObserver() {
        profileViewModel.currentUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                binding.tvUserName.text = if (user.fullName.isNotEmpty()) user.fullName else user.phoneNumber

                if (!isDetached && context != null) {
                    Glide.with(requireContext())
                        .load(user.photoUrl)
                        .placeholder(R.drawable.ic_profile_placeholder)
                        .error(R.drawable.ic_profile_placeholder)
                        .circleCrop()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(binding.ivProfileImage)
                }
            }
        }

        mainViewModel.getSession().observe(viewLifecycleOwner) { userModel ->
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

    // ==========================================
    // LOGIKA PEMANGGILAN DIALOG COMPOSE
    // ==========================================
    private fun showLogoutConfirmation() {
        // Membuat "Jembatan" (Bridge) dari Fragment ke Compose
        val composeView = ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    LogoutComposeDialog(
                        onDismiss = { logoutDialog?.dismiss() },
                        onConfirm = {
                            logoutDialog?.dismiss()
                            performLogout()
                        }
                    )
                }
            }
        }

        // Membungkus ComposeView ke dalam Material Dialog standar
        logoutDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(composeView)
            // Background transparan agar sudut melengkung dari Compose terlihat
            .setBackground(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            .create()

        logoutDialog?.show()
    }

    private fun performLogout() {
        lifecycleScope.launch(Dispatchers.IO) {
            val context = requireContext()
            AppDatabase.getDatabase(context).clearAllTables()

            withContext(Dispatchers.Main) {
                mainViewModel.logout {
                    navigateToLogin()
                }
            }
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

// ==========================================
// 🚀 JETPACK COMPOSE UI COMPONENT (DIALOG)
// ==========================================

@Composable
fun LogoutComposeDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp), // Sudut melengkung halus
        colors = CardDefaults.cardColors(containerColor = colorResource(id = R.color.card_background)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally // Agar isi rata tengah
        ) {
            Text(
                text = "Konfirmasi Keluar",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                color = colorResource(id = R.color.colorPrimary),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = "Apakah Anda yakin ingin keluar dari akun? Semua jadwal lokal Anda akan disinkronisasi ulang saat login kembali.",
                fontSize = 15.sp,
                color = colorResource(id = R.color.text_primary),
                textAlign = TextAlign.Center, // Teks rata tengah agar elegan
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly // Membagi tombol kiri & kanan seimbang
            ) {
                // Tombol Batal (Outlined/Text)
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Batal",
                        color = colorResource(id = R.color.text_secondary),
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Tombol Keluar (Solid Button)
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = colorResource(id = R.color.task_color)), // Memakai warna dari sistemmu (bisa diganti merah jika ada R.color.error)
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Keluar",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}