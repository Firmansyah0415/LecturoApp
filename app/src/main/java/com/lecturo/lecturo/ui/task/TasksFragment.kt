package com.lecturo.lecturo.ui.task

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.lecturo.lecturo.R
import com.lecturo.lecturo.databinding.FragmentTasksBinding
import com.lecturo.lecturo.ui.components.TaskListItemCompose
import com.lecturo.lecturo.viewmodel.task.TasksViewModel

class TasksFragment : Fragment(R.layout.fragment_tasks) {

    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!

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

        val liveDataToObserve = if (isCompletedTasks) viewModel.completedTasks else viewModel.pendingTasks

        binding.composeViewTasks.setContent {
            MaterialTheme {
                val tasksList by liveDataToObserve.observeAsState(emptyList())
                val isSortNewest by viewModel.isSortNewest.observeAsState(true)

                // 🔴 SOLUSI BUG: Memaksa list kembali ke atas (index 0) setiap kali filter urutan ditekan
                val listState = rememberLazyListState()
                LaunchedEffect(isSortNewest) {
                    if (tasksList.isNotEmpty()) {
                        listState.animateScrollToItem(0)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 90.dp) // Jarak agar tidak tertutup FAB
                ) {
                    items(tasksList, key = { it.task.id }) { item ->
                        TaskListItemCompose(
                            item = item,
                            onActionClick = { task, action ->
                                (activity as? TasksActivity)?.handleTasksAction(task, action)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Compose otomatis mendeteksi perubahan state, tidak perlu notifyDataSetChanged lagi!
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}