package com.lecturo.lecturo.ui.teaching.class_schedule

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lecturo.lecturo.data.model.CalendarEntry
import com.lecturo.lecturo.databinding.ItemClassScheduleBinding
import java.text.SimpleDateFormat
import java.util.*

class ClassScheduleAdapter(
    private val onItemClick: (CalendarEntry) -> Unit
) : ListAdapter<CalendarEntry, ClassScheduleAdapter.ClassScheduleViewHolder>(ClassScheduleDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClassScheduleViewHolder {
        val binding = ItemClassScheduleBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ClassScheduleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ClassScheduleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ClassScheduleViewHolder(
        private val binding: ItemClassScheduleBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: CalendarEntry) {
            binding.apply {
                textTitle.text = entry.title
                textDate.text = formatDate(entry.date)
                textTime.text = entry.time
                textCategory.text = entry.category

                root.setOnClickListener {
                    onItemClick(entry)
                }
            }
        }

        private fun formatDate(dateString: String): String {
            return try {
                val inputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val outputFormat = SimpleDateFormat("EEEE, dd MMM yyyy", Locale("id", "ID"))
                val date = inputFormat.parse(dateString)
                if (date != null) outputFormat.format(date) else dateString
            } catch (e: Exception) {
                dateString
            }
        }
    }

    class ClassScheduleDiffCallback : DiffUtil.ItemCallback<CalendarEntry>() {
        override fun areItemsTheSame(oldItem: CalendarEntry, newItem: CalendarEntry): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CalendarEntry, newItem: CalendarEntry): Boolean {
            return oldItem == newItem
        }
    }
}
