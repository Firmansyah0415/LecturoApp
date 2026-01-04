package com.lecturo.lecturo.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels // Tambahkan ini
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide // Wajib untuk gambar
import com.bumptech.glide.load.engine.DiskCacheStrategy // Wajib untuk cache
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lecturo.lecturo.R
import com.lecturo.lecturo.databinding.FragmentHomeBinding
import com.lecturo.lecturo.di.ViewModelFactory
import com.lecturo.lecturo.ui.auth.LoginActivity // Arahkan ke Login jika sesi habis
import com.lecturo.lecturo.ui.event.AddEventActivity
import com.lecturo.lecturo.ui.event.EventActivity
import com.lecturo.lecturo.ui.task.AddTasksActivity
import com.lecturo.lecturo.ui.task.TasksActivity
import com.lecturo.lecturo.ui.teaching.AddTeachingActivity
import com.lecturo.lecturo.ui.teaching.TeachingActivity
import com.lecturo.lecturo.viewmodel.main.MainViewModel
import com.lecturo.lecturo.viewmodel.profile.ProfileViewModel // Tambahkan ini
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // 1. MainViewModel untuk Data Dashboard (Jadwal, Statistik)
    private val mainViewModel by activityViewModels<MainViewModel> {
        ViewModelFactory.getInstance(requireActivity())
    }

    // 2. ProfileViewModel untuk Data Header (Nama, Foto Terbaru)
    private val profileViewModel by viewModels<ProfileViewModel> {
        ViewModelFactory.getInstance(requireContext())
    }

    private lateinit var dateAdapter: DateAdapter
    private lateinit var agendaAdapter: AgendaAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        setupClickListeners()
        setupSwipeToRefresh()

        // Setup Observer
        observeMainViewModel()
        observeProfileViewModel()

        updateDateDisplay()
    }

    // PENTING: Panggil loadUserProfile setiap kali halaman tampil (pulang dari edit profile)
    override fun onResume() {
        super.onResume()
        profileViewModel.loadUserProfile()
        mainViewModel.refreshData() // Refresh jadwal juga
    }

    private fun setupSwipeToRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            mainViewModel.refreshData()
            profileViewModel.loadUserProfile() // Refresh profil juga saat swipe
        }
    }

    private fun observeMainViewModel() {
        // Cek Sesi Login
        mainViewModel.getSession().observe(viewLifecycleOwner) { user ->
            if (!user.isLogin) {
                // PERBAIKAN: Jika tidak login, ke LoginActivity, BUKAN WelcomeActivity
                startActivity(Intent(requireContext(), LoginActivity::class.java))
                activity?.finish()
            }
        }

        mainViewModel.greetingText.observe(viewLifecycleOwner) { greeting ->
            binding.greetingTextView.text = greeting
        }

        // Statistik
        mainViewModel.taskCount.observe(viewLifecycleOwner) { count ->
            binding.tasksCountTextView.text = count.toString()
        }
        mainViewModel.eventCount.observe(viewLifecycleOwner) { count ->
            binding.eventCountTextView.text = count.toString()
        }
        mainViewModel.teachingRuleCount.observe(viewLifecycleOwner) { count ->
            binding.teachingCountTextView.text = count.toString()
        }
        mainViewModel.consultationCount.observe(viewLifecycleOwner) { count ->
            binding.consultationCountTextView.text = count.toString()
        }

        // Loading State SwipeRefresh
        mainViewModel.isRefreshing.observe(viewLifecycleOwner) { isRefreshing ->
            // Pastikan swipe berhenti jika profile juga sudah selesai (bisa ditambah logika complex, tapi ini cukup)
            if (!isRefreshing) {
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }

        // Agenda
        mainViewModel.todaysAgenda.observe(viewLifecycleOwner) { agenda ->
            if (agenda.isEmpty()) {
                binding.agendaRecyclerView.visibility = View.GONE
                binding.emptyAgendaTextView.visibility = View.VISIBLE
            } else {
                binding.agendaRecyclerView.visibility = View.VISIBLE
                binding.emptyAgendaTextView.visibility = View.GONE
                agendaAdapter.submitList(agenda)
            }
        }

        mainViewModel.selectedDate.observe(viewLifecycleOwner) { selectedDate ->
            updateDateSelection(selectedDate)
        }

        // FAB Click
        mainViewModel.fabClickEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                showAddScheduleDialog()
            }
        }
    }

    private fun observeProfileViewModel() {
        // PERBAIKAN LOGIKA HEADER (NAMA & FOTO)
        profileViewModel.currentUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                // 1. LOGIKA NAMA vs NO HP
                // Jika nama ada isinya, pakai nama. Jika kosong, pakai nomor HP.
                val displayName = if (user.fullName.isNotEmpty()) {
                    user.fullName
                } else {
                    user.phoneNumber
                }
                binding.nameTextView.text = displayName

                // 2. LOGIKA FOTO PROFIL (GLIDE)
                if (!isDetached && context != null) {
                    Glide.with(requireContext())
                        .load(user.photoUrl)
                        .placeholder(R.drawable.profile_logo)
                        .error(R.drawable.profile_logo)
                        .diskCacheStrategy(DiskCacheStrategy.NONE) // Agar foto langsung update
                        .skipMemoryCache(true)
                        .into(binding.profileImageView)
                }
            }
        }
    }

    private fun setupRecyclerViews() {
        dateAdapter = DateAdapter { selectedDate ->
            mainViewModel.loadAgendaForDate(selectedDate)
        }
        binding.datesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = dateAdapter
        }

        agendaAdapter = AgendaAdapter { calendarEntry ->
            when (calendarEntry.sourceFeatureType) {
                "TEACHING_RULE" -> startActivity(Intent(requireContext(), TeachingActivity::class.java))
                "EVENT" -> startActivity(Intent(requireContext(), EventActivity::class.java))
                "TASK" -> startActivity(Intent(requireContext(), TasksActivity::class.java))
            }
        }
        binding.agendaRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = agendaAdapter
        }

        generateCalendarDates()
    }

    private fun setupClickListeners() {
        binding.teachingCard.setOnClickListener {
            startActivity(Intent(requireContext(), TeachingActivity::class.java))
        }
        binding.tasksCard.setOnClickListener {
            startActivity(Intent(requireContext(), TasksActivity::class.java))
        }
        binding.eventCard.setOnClickListener {
            startActivity(Intent(requireContext(), EventActivity::class.java))
        }
        binding.consultationCard.setOnClickListener {
            Toast.makeText(requireContext(), "Fitur Konsultasi akan segera hadir", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateCalendarDates() {
        val calendar = Calendar.getInstance()
        val today = Date()
        val dates = mutableListOf<DateItem>()
        calendar.add(Calendar.DAY_OF_MONTH, -7)
        repeat(15) {
            val date = calendar.time
            dates.add(DateItem(date, isSameDay(date, today)))
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        dateAdapter.submitList(dates)
        binding.datesRecyclerView.scrollToPosition(7)
    }

    private fun updateDateSelection(selectedDate: Date) {
        val currentList = dateAdapter.currentList.toMutableList()
        val updatedList = currentList.map { dateItem ->
            dateItem.copy(isSelected = isSameDay(dateItem.date, selectedDate))
        }
        dateAdapter.submitList(updatedList)
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun updateDateDisplay() {
        val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID"))
        val currentDate = dateFormat.format(Date())
        binding.dateTextView.text = currentDate
    }

    private fun showAddScheduleDialog() {
        val options = arrayOf("Tugas", "Event/Rapat", "Jadwal Mengajar", "Konsultasi")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Tambah Jadwal")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(requireContext(), AddTasksActivity::class.java))
                    1 -> startActivity(Intent(requireContext(), AddEventActivity::class.java))
                    2 -> startActivity(Intent(requireContext(), AddTeachingActivity::class.java))
                    3 -> Toast.makeText(requireContext(), "Fitur Konsultasi akan segera hadir", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}