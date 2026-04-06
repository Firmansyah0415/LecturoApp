package com.lecturo.lecturo.ui.task

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.lecturo.lecturo.R
import com.lecturo.lecturo.databinding.FragmentTasksBinding
import com.lecturo.lecturo.viewmodel.task.TasksViewModel

class TasksFragment : Fragment(R.layout.fragment_tasks) {

    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!

    private lateinit var tasksAdapter: TasksAdapter
    private var isCompletedTasks: Boolean = false

    private val viewModel: TasksViewModel by activityViewModels {
        (requireActivity() as TasksActivity).getViewModelFactory()
    }

    companion object {
        private const val IS_COMPLETED_KEY = "is_completed"
        fun newInstance(isCompleted: Boolean): TasksFragment {
            return TasksFragment().apply {
                arguments = Bundle().apply { putBoolean(IS_COMPLETED_KEY, isCompleted) }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTasksBinding.bind(view)

        isCompletedTasks = arguments?.getBoolean(IS_COMPLETED_KEY) ?: false

        setupRecyclerView()
        observeTasks()
    }

    private fun setupRecyclerView() {
        tasksAdapter = TasksAdapter { tasks, action ->
            // PERBAIKAN:
            // Jangan filter action di sini. Langsung lempar semuanya ke Activity.
            // Biarkan Activity yang memutuskan apakah mau buka BottomSheet atau langsung hapus.
            (activity as? TasksActivity)?.handleTasksAction(tasks, action)
        }

        binding.rvTaskFragment.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = tasksAdapter
        }
    }

    private fun observeTasks() {
        val liveDataToObserve = if (isCompletedTasks) viewModel.completedTasks else viewModel.pendingTasks
        liveDataToObserve.observe(viewLifecycleOwner) { taskss ->
            tasksAdapter.submitList(taskss)
        }
    }

    // Taruh di dalam class Fragment kamu (misal: TasksFragment)
    override fun onResume() {
        super.onResume()
        // Paksa adapter untuk menggambar ulang list agar mengecek FocusPreferences terbaru.
        // (Pastikan nama 'recyclerViewTasks' sesuai dengan ID di layout fragment kamu)
        binding.rvTaskFragment.adapter?.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

