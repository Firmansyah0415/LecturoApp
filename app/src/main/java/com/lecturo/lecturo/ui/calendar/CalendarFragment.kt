package com.lecturo.lecturo.ui.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.repository.CalendarRepository
import com.lecturo.lecturo.data.repository.EventRepository
import com.lecturo.lecturo.data.repository.TasksRepository
import com.lecturo.lecturo.databinding.FragmentCalendarBinding
import com.lecturo.lecturo.utils.EventCategories
import java.text.SimpleDateFormat
import java.util.*

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CalendarViewModel by viewModels {
        CalendarViewModelFactory(
            CalendarRepository(AppDatabase.getDatabase(requireContext()).calendarEntryDao()),
            EventRepository(AppDatabase.getDatabase(requireContext()).eventDao()),
            TasksRepository(AppDatabase.getDatabase(requireContext()).tasksDao())
        )
    }

    private lateinit var calendarAdapter: CalendarDayAdapter
    private lateinit var scheduleAdapter: CalendarScheduleAdapter

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupCalendarRecyclerView()
        setupScheduleRecyclerView()
        setupFilters()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupCalendarRecyclerView() {
        calendarAdapter = CalendarDayAdapter { calendarDay ->
            if (calendarDay.isCurrentMonth) {
                viewModel.selectDate(calendarDay.date)
            }
        }

        binding.recyclerViewCalendar.apply {
            layoutManager = GridLayoutManager(requireContext(), 7)
            adapter = calendarAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupScheduleRecyclerView() {
        scheduleAdapter = CalendarScheduleAdapter { schedule ->
            // Handle klik item jadwal
        }

        binding.recyclerViewSchedules.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = scheduleAdapter
        }
    }

    private fun setupFilters() {
        // Filter Waktu
        binding.chipGroupTimeFilter.setOnCheckedStateChangeListener { group, checkedIds ->
            val selectedChipId = checkedIds.firstOrNull() ?: R.id.chipDaily
            val timeFilter = when (selectedChipId) {
                R.id.chipDaily -> CalendarViewModel.TimeFilter.DAILY
                R.id.chipWeekly -> CalendarViewModel.TimeFilter.WEEKLY
                R.id.chipMonthly -> CalendarViewModel.TimeFilter.MONTHLY
                else -> CalendarViewModel.TimeFilter.DAILY
            }
            viewModel.setTimeFilter(timeFilter)
        }

        // --- LANGKAH 2: PERBAIKI LOGIKA FILTER KATEGORI ---
        binding.chipGroupCategoryFilter.setOnCheckedStateChangeListener { _, _ ->
            val selectedCategories = mutableSetOf<String>()

            if (binding.chipTask.isChecked) selectedCategories.add("Tugas")
            if (binding.chipTeaching.isChecked) selectedCategories.add("Mengajar")
            if (binding.chipConsultation.isChecked) selectedCategories.add("Konsultasi")

            // Jika chip "Event" dicentang, tambahkan semua sub-kategorinya ke dalam set filter
            if (binding.chipEvent.isChecked) {
                // Konversi kategori dari lowercase (di list) menjadi Proper Case
                // agar cocok dengan data yang tersimpan di database ("Rapat", "Seminar", dll.)
                val eventSubCategories = EventCategories.list.map {
                    it.replaceFirstChar { char ->
                        if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
                    }
                }
                selectedCategories.addAll(eventSubCategories)
            }

            viewModel.setCategoryFilter(selectedCategories)
        }
    }

    private fun setupClickListeners() {
        binding.btnPreviousMonth.setOnClickListener {
            viewModel.navigateToPreviousMonth()
        }

        binding.btnNextMonth.setOnClickListener {
            viewModel.navigateToNextMonth()
        }
    }

    private fun observeViewModel() {
        viewModel.currentMonth.observe(viewLifecycleOwner) { calendar ->
            binding.tvCurrentMonth.text = monthYearFormat.format(calendar.time)
        }

        viewModel.calendarDays.observe(viewLifecycleOwner) { days ->
            calendarAdapter.submitList(days)
        }

        viewModel.selectedDateSchedules.observe(viewLifecycleOwner) { schedules ->
            scheduleAdapter.submitList(schedules)
            binding.tvScheduleCount.text = schedules.size.toString()
            binding.recyclerViewSchedules.visibility = if (schedules.isEmpty()) View.GONE else View.VISIBLE
            binding.layoutEmptyState.visibility = if (schedules.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.selectedDate.observe(viewLifecycleOwner) { date ->
            val formattedDate = dateFormat.format(date.time)
            binding.tvScheduleListTitle.text = "Jadwal $formattedDate"
            calendarAdapter.setSelectedDate(formattedDate)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // Handle loading state
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
