package com.lecturo.lecturo

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lecturo.lecturo.databinding.ItemScheduleBinding

class ScheduleAdapter(
    private val onActionClick: (Schedule, String) -> Unit
) : ListAdapter<Schedule, ScheduleAdapter.ScheduleViewHolder>(ScheduleDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
        val binding = ItemScheduleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ScheduleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        val schedule = getItem(position)
        holder.bind(schedule)
    }

    inner class ScheduleViewHolder(private val binding: ItemScheduleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(schedule: Schedule) {
            binding.textTitle.text = schedule.title
            binding.textDate.text = schedule.date
            binding.textTime.text = schedule.time
            binding.textLocation.text = schedule.location
            binding.textDescription.text = schedule.description

            // Contoh: Menukar status checkbox berdasarkan data
            binding.checkboxCompleted.isChecked = schedule.completed

            // Listener untuk checkbox
            binding.checkboxCompleted.setOnClickListener {
                val action = if (binding.checkboxCompleted.isChecked) "complete" else "uncomplete"
                onActionClick(schedule, action)
            }

            // Listener untuk keseluruhan item (untuk edit)
            itemView.setOnClickListener {
                onActionClick(schedule, "edit")
            }

            // Listener untuk ikon delete
            binding.buttonDelete.setOnClickListener {
                onActionClick(schedule, "delete")
            }
        }
    }

    class ScheduleDiffCallback : DiffUtil.ItemCallback<Schedule>() {
        override fun areItemsTheSame(oldItem: Schedule, newItem: Schedule): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Schedule, newItem: Schedule): Boolean {
            return oldItem == newItem
        }
    }
}