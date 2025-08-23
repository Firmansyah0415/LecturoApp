package com.lecturo.lecturo.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lecturo.lecturo.R
import com.lecturo.lecturo.databinding.FragmentHomeBinding
import com.lecturo.lecturo.di.ViewModelFactory
import com.lecturo.lecturo.ui.event.AddEventActivity
import com.lecturo.lecturo.ui.event.EventActivity
import com.lecturo.lecturo.ui.task.AddTasksActivity
import com.lecturo.lecturo.ui.task.TasksActivity
import com.lecturo.lecturo.ui.teaching.AddTeachingActivity
import com.lecturo.lecturo.ui.teaching.TeachingActivity
import com.lecturo.lecturo.ui.welcome.WelcomeActivity
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Gunakan activityViewModels agar ViewModel sama dengan yang di Activity
    private val viewModel by activityViewModels<MainViewModel> {
        ViewModelFactory.getInstance(requireActivity())
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

        // Pindahkan semua setup dari MainActivity ke sini
        //setupToolbar()
        setupRecyclerViews()
        setupClickListeners()
        observeViewModel()
        setupSwipeToRefresh()
        updateDateDisplay()
    }

//    private fun setupToolbar() {
//        // Fragment perlu memberi tahu Activity bahwa ia memiliki menu sendiri
//        setHasOptionsMenu(true)
//        (activity as? AppCompatActivity)?.setSupportActionBar(binding.topAppBar)
//        (activity as? AppCompatActivity)?.supportActionBar?.title = "Dashboard"
//    }

    private fun setupSwipeToRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshData()
        }
    }

    private fun setupRecyclerViews() {
        dateAdapter = DateAdapter { selectedDate ->
            viewModel.loadAgendaForDate(selectedDate)
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
        // Click listener untuk FAB akan ditangani di MainActivity baru
    }

    private fun observeViewModel() {
        viewModel.getSession().observe(viewLifecycleOwner) { user ->
            if (!user.isLogin) {
                startActivity(Intent(requireContext(), WelcomeActivity::class.java))
                activity?.finish()
            }
        }
        viewModel.userName.observe(viewLifecycleOwner) { name ->
            binding.nameTextView.text = name
        }
        viewModel.greetingText.observe(viewLifecycleOwner) { greeting ->
            binding.greetingTextView.text = greeting
        }
        viewModel.taskCount.observe(viewLifecycleOwner) { count ->
            binding.tasksCountTextView.text = count.toString()
        }
        viewModel.eventCount.observe(viewLifecycleOwner) { count ->
            binding.eventCountTextView.text = count.toString()
        }
        viewModel.teachingRuleCount.observe(viewLifecycleOwner) { count ->
            binding.teachingCountTextView.text = count.toString()
        }
        viewModel.consultationCount.observe(viewLifecycleOwner) { count ->
            binding.consultationCountTextView.text = count.toString()
        }
        viewModel.isRefreshing.observe(viewLifecycleOwner) { isRefreshing ->
            binding.swipeRefreshLayout.isRefreshing = isRefreshing
        }
        viewModel.todaysAgenda.observe(viewLifecycleOwner) { agenda ->
            if (agenda.isEmpty()) {
                binding.agendaRecyclerView.visibility = View.GONE
                binding.emptyAgendaTextView.visibility = View.VISIBLE
            } else {
                binding.agendaRecyclerView.visibility = View.VISIBLE
                binding.emptyAgendaTextView.visibility = View.GONE
                agendaAdapter.submitList(agenda)
            }
        }
        viewModel.selectedDate.observe(viewLifecycleOwner) { selectedDate ->
            updateDateSelection(selectedDate)
        }

        // Mengamati event klik FAB dari ViewModel
        viewModel.fabClickEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                showAddScheduleDialog()
            }
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

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.main_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                viewModel.logout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
