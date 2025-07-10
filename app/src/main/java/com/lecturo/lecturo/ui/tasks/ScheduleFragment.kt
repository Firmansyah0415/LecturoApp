package com.lecturo.lecturo.ui.tasks

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.lecturo.lecturo.AddScheduleActivity
import com.lecturo.lecturo.R
import com.lecturo.lecturo.ScheduleAdapter
import com.lecturo.lecturo.databinding.FragmentScheduleBinding

class ScheduleFragment : Fragment(R.layout.fragment_schedule) {

    private var _binding: FragmentScheduleBinding? = null
    private val binding get() = _binding!!

    private lateinit var scheduleAdapter: ScheduleAdapter
    private var isCompletedSchedules: Boolean = false

    private val viewModel: TasksViewModel by activityViewModels {
        (requireActivity() as TasksActivity).getViewModelFactory()
    }

    companion object {
        private const val IS_COMPLETED_KEY = "is_completed"
        fun newInstance(isCompleted: Boolean): ScheduleFragment {
            return ScheduleFragment().apply {
                arguments = Bundle().apply { putBoolean(IS_COMPLETED_KEY, isCompleted) }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentScheduleBinding.bind(view)

        isCompletedSchedules = arguments?.getBoolean(IS_COMPLETED_KEY) ?: false

        setupRecyclerView()
        observeSchedules()
    }

    private fun setupRecyclerView() {
        scheduleAdapter = ScheduleAdapter { schedule, action ->
            if (action == "edit") {
                val intent = Intent(requireContext(), AddScheduleActivity::class.java)
                intent.putExtra("schedule_id", schedule.id)
                startActivity(intent)
            } else {
                (activity as? TasksActivity)?.handleScheduleAction(schedule, action)
            }
        }
        binding.recyclerViewSchedules.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = scheduleAdapter
        }
    }

    private fun observeSchedules() {
        val liveDataToObserve = if (isCompletedSchedules) viewModel.completedSchedules else viewModel.pendingSchedules
        liveDataToObserve.observe(viewLifecycleOwner) { schedules ->
            scheduleAdapter.submitList(schedules)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

