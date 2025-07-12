package com.lecturo.lecturo.ui.task

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lecturo.lecturo.data.model.Tasks
import com.lecturo.lecturo.databinding.ItemTasksBinding

class TasksAdapter(
    private val onActionClick: (Tasks, String) -> Unit
) : ListAdapter<Tasks, TasksAdapter.TasksViewHolder>(TasksDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TasksViewHolder {
        val binding = ItemTasksBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TasksViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TasksViewHolder, position: Int) {
        val tasks = getItem(position)
        holder.bind(tasks)
    }

    inner class TasksViewHolder(private val binding: ItemTasksBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(tasks: Tasks) {
            binding.textTitle.text = tasks.title
            binding.textDate.text = tasks.date
            binding.textTime.text = tasks.time
            binding.textLocation.text = tasks.location
            binding.textDescription.text = tasks.description

            // Contoh: Menukar status checkbox berdasarkan data
            binding.checkboxCompleted.isChecked = tasks.completed

            // Listener untuk checkbox
            binding.checkboxCompleted.setOnClickListener {
                val action = if (binding.checkboxCompleted.isChecked) "complete" else "uncomplete"
                onActionClick(tasks, action)
            }

            // Listener untuk keseluruhan item (untuk edit)
            itemView.setOnClickListener {
                onActionClick(tasks, "edit")
            }

            // Listener untuk ikon delete
            binding.buttonDelete.setOnClickListener {
                onActionClick(tasks, "delete")
            }
        }
    }

    class TasksDiffCallback : DiffUtil.ItemCallback<Tasks>() {
        override fun areItemsTheSame(oldItem: Tasks, newItem: Tasks): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Tasks, newItem: Tasks): Boolean {
            return oldItem == newItem
        }
    }
}