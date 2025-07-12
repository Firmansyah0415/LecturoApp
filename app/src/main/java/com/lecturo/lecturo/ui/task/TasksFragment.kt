package com.lecturo.lecturo.ui.task

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.lecturo.lecturo.ui.task.AddTasksActivity
import com.lecturo.lecturo.R
import com.lecturo.lecturo.databinding.FragmentTasksBinding

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
            if (action == "edit") {
                val intent = Intent(requireContext(), AddTasksActivity::class.java)
                intent.putExtra("tasks_id", tasks.id)
                startActivity(intent)
            } else {
                (activity as? TasksActivity)?.handleTasksAction(tasks, action)
            }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

