package com.lecturo.lecturo.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lecturo.lecturo.R
import com.lecturo.lecturo.databinding.FragmentHomeBinding
import com.lecturo.lecturo.di.ViewModelFactory
import com.lecturo.lecturo.ui.auth.LoginActivity
import com.lecturo.lecturo.ui.event.AddEventActivity
import com.lecturo.lecturo.ui.event.EventActivity
import com.lecturo.lecturo.ui.task.AddTasksActivity
import com.lecturo.lecturo.ui.task.TasksActivity
import com.lecturo.lecturo.ui.teaching.AddTeachingActivity
import com.lecturo.lecturo.ui.teaching.TeachingActivity
import com.lecturo.lecturo.viewmodel.main.MainViewModel
import com.lecturo.lecturo.viewmodel.profile.ProfileViewModel
import com.lecturo.lecturo.ui.consultation.ConsultationActivity
import com.lecturo.lecturo.ui.consultation.DetailConsultationActivity

import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel by activityViewModels<MainViewModel> {
        ViewModelFactory.getInstance(requireActivity())
    }

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

        observeMainViewModel()
        observeProfileViewModel()

        updateDateDisplay()

        // 1. Ambil referensi BottomAppBar dan FAB dari MainActivity
        val bottomAppBar = requireActivity().findViewById<com.google.android.material.bottomappbar.BottomAppBar>(R.id.bottomAppBar)
        val fab = requireActivity().findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab)

        // 2. Pasang pendeteksi scroll di NESTED SCROLL VIEW (Bukan RecyclerView)
        binding.nestedScrollView.setOnScrollChangeListener(androidx.core.widget.NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            val dy = scrollY - oldScrollY

            // dy > 10 berarti user scroll ke bawah (untuk baca data ke bawah)
            if (dy > 10 && fab.isOrWillBeShown) {
                fab.hide() // FAB akan mengecil & hilang
                bottomAppBar.performHide() // Navigasi meluncur ke bawah
            }
            // dy < -10 berarti user scroll ke atas (kembali ke puncak)
            else if (dy < -10 && fab.isOrWillBeHidden) {
                fab.show() // FAB akan membesar & muncul
                bottomAppBar.performShow() // Navigasi meluncur ke atas
            }
        })
    }

    override fun onResume() {
        super.onResume()
        profileViewModel.loadUserProfile()
        mainViewModel.refreshData()
    }

    private fun setupSwipeToRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            mainViewModel.refreshData()
            profileViewModel.loadUserProfile()
        }
    }

    private fun observeMainViewModel() {
        mainViewModel.getSession().observe(viewLifecycleOwner) { user ->
            if (!user.isLogin) {
                // --- [SABUK PENGAMAN ANTI PING-PONG] ---
                // Jika DataStore lokal hilang/rusak, paksa putuskan juga sesi Firebase-nya!
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()

                // Lempar ke layar Login secara bersih
                startActivity(Intent(requireContext(), LoginActivity::class.java))
                activity?.finish()
            }
        }

        mainViewModel.greetingText.observe(viewLifecycleOwner) { greeting ->
            binding.greetingTextView.text = greeting
        }

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

        mainViewModel.isRefreshing.observe(viewLifecycleOwner) { isRefreshing ->
            if (!isRefreshing) {
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }

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

        mainViewModel.fabClickEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                showAddScheduleDialog()
            }
        }
    }

    private fun observeProfileViewModel() {
        profileViewModel.currentUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                val displayName = if (user.fullName.isNotEmpty()) {
                    user.fullName
                } else {
                    user.phoneNumber
                }
                binding.nameTextView.text = displayName

                if (!isDetached && context != null) {
                    Glide.with(requireContext())
                        .load(user.photoUrl)
                        .placeholder(R.drawable.profile_logo)
                        .error(R.drawable.profile_logo)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
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
                "CONSULTATION", "Konsultasi" -> startActivity(Intent(requireContext(), ConsultationActivity::class.java))
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
            startActivity(Intent(requireContext(), ConsultationActivity::class.java))
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
                    3 -> startActivity(Intent(requireContext(), DetailConsultationActivity::class.java))
                }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}