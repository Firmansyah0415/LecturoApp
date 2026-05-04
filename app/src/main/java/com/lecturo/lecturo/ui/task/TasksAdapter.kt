package com.lecturo.lecturo.ui.task

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.model.TaskWithFocusStats
import com.lecturo.lecturo.data.model.Tasks
import com.lecturo.lecturo.databinding.ItemTasksBinding
import com.lecturo.lecturo.utils.FocusPreferences
import com.lecturo.lecturo.utils.toReadableDate

class TasksAdapter(
    private val onActionClick: (Tasks, String) -> Unit
) : ListAdapter<TaskWithFocusStats, TasksAdapter.TasksViewHolder>(TasksDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TasksViewHolder {
        val binding = ItemTasksBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TasksViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TasksViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class TasksViewHolder(private val binding: ItemTasksBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TaskWithFocusStats) {
            val tasks = item.task

            binding.textTitle.text = tasks.title

            val timeDisplay = if (tasks.endTime.isNotEmpty()) {
                "${tasks.time} - ${tasks.endTime}"
            } else {
                tasks.time
            }
            binding.textDateTime.text = "${tasks.date.toReadableDate()} • $timeDisplay"
            // ----------------------------------------------------

            binding.textLocation.text = tasks.location ?: "Tidak ada lokasi"

            if (tasks.description.isNullOrEmpty()) {
                binding.textDescription.visibility = View.GONE
            } else {
                binding.textDescription.visibility = View.VISIBLE
                binding.textDescription.text = tasks.description
            }

            binding.checkboxCompleted.isChecked = tasks.isCompleted

            // --- WARNA PRIORITAS ---
            val priorityText = tasks.priority ?: "Sedang"
            binding.tvPriorityBadge.text = priorityText.uppercase()

            val priorityTextColorRes: Int
            val priorityBgColorRes: Int

            when (priorityText.lowercase()) {
                "tinggi", "high", "hight", "urgent" -> {
                    priorityTextColorRes = R.color.high_priority
                    priorityBgColorRes = R.color.high_priority_bg
                }
                "rendah", "low" -> {
                    priorityTextColorRes = R.color.low_priority
                    priorityBgColorRes = R.color.low_priority_bg
                }
                else -> {
                    priorityTextColorRes = R.color.medium_priority
                    priorityBgColorRes = R.color.medium_priority_bg
                }
            }

            val context = itemView.context
            val resolvedTextColor = ContextCompat.getColor(context, priorityTextColorRes)
            val resolvedBgColor = ContextCompat.getColor(context, priorityBgColorRes)

            binding.tvPriorityBadge.setTextColor(resolvedTextColor)
            binding.tvPriorityBadge.backgroundTintList = ColorStateList.valueOf(resolvedBgColor)

            // --- STATISTIK POMODORO ---
            if (item.completedSessionsCount > 0) {
                binding.tvFocusStatsBadge.visibility = View.VISIBLE

                val totalMinutes = item.totalFocusMinutes ?: 0
                val hours = totalMinutes / 60
                val minutes = totalMinutes % 60

                val timeFormatted = when {
                    hours > 0 && minutes > 0 -> "$hours Jam $minutes mnt"
                    hours > 0 -> "$hours Jam"
                    else -> "$totalMinutes mnt"
                }

                binding.tvFocusStatsBadge.text = "${item.completedSessionsCount} Sesi ($timeFormatted)"
            } else {
                binding.tvFocusStatsBadge.visibility = View.GONE
            }

            // --- LOGIKA TANDA TUGAS AKTIF ---
            val prefs = FocusPreferences(context)
            val activeTaskId = prefs.getActiveTaskId()

            if (tasks.id == activeTaskId) {
                val currentPhase = prefs.getCurrentPhase()
                val isFocusing = currentPhase == "Fokus"

                binding.indicatorActiveFocus.visibility = View.VISIBLE
                binding.indicatorActiveFocus.text = if (isFocusing) "SEDANG FOKUS" else "SEDANG ISTIRAHAT"

                val activeColorRes = if (isFocusing) R.color.colorPrimary else android.R.color.holo_orange_dark
                val resolvedActiveColor = ContextCompat.getColor(context, activeColorRes)

                binding.indicatorActiveFocus.backgroundTintList = ColorStateList.valueOf(resolvedActiveColor)
                (binding.root as com.google.android.material.card.MaterialCardView).strokeColor = resolvedActiveColor
                (binding.root as com.google.android.material.card.MaterialCardView).strokeWidth = 4
            } else {
                binding.indicatorActiveFocus.visibility = View.GONE
                (binding.root as com.google.android.material.card.MaterialCardView).strokeColor =
                    ContextCompat.getColor(context, com.google.android.material.R.color.m3_sys_color_light_outline_variant)
                (binding.root as com.google.android.material.card.MaterialCardView).strokeWidth = 2
            }

            // --- AKSI KLIK ---
            binding.checkboxCompleted.setOnClickListener {
                val action = if (binding.checkboxCompleted.isChecked) "complete" else "uncomplete"
                onActionClick(tasks, action)
            }

            itemView.setOnClickListener {
                onActionClick(tasks, "edit")
            }
        }
    }

    class TasksDiffCallback : DiffUtil.ItemCallback<TaskWithFocusStats>() {
        override fun areItemsTheSame(oldItem: TaskWithFocusStats, newItem: TaskWithFocusStats): Boolean {
            return oldItem.task.id == newItem.task.id
        }

        override fun areContentsTheSame(oldItem: TaskWithFocusStats, newItem: TaskWithFocusStats): Boolean {
            return oldItem == newItem
        }
    }
}