package com.lecturo.lecturo.ui.calendar

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.model.CalendarEntry
import com.lecturo.lecturo.databinding.ItemScheduleCalendarBinding
import java.util.*
import com.lecturo.lecturo.utils.EventCategories


class CalendarScheduleAdapter(
    private val onScheduleClick: (CalendarEntry) -> Unit
) : ListAdapter<CalendarEntry, CalendarScheduleAdapter.ScheduleViewHolder>(ScheduleDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
        val binding = ItemScheduleCalendarBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ScheduleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ScheduleViewHolder(
        private val binding: ItemScheduleCalendarBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(schedule: CalendarEntry) {
            with(binding) {
                tvScheduleTitle.text = schedule.title
                tvScheduleTime.text = schedule.time
                tvScheduleCategory.text = schedule.category.uppercase()

                val context = root.context

                // Bersihkan kategori dari spasi dan ubah ke huruf kecil
                val cleanCategory = schedule.category.trim().lowercase(Locale.getDefault())

                // --- LANGKAH 2: Perbarui logika 'when' ---
                // Sekarang kita periksa apakah kategori ada di dalam daftar master.
                val colorRes = when {
                    cleanCategory == "tugas" -> R.color.task_color
                    cleanCategory == "mengajar" -> R.color.teaching_color
                    cleanCategory == "konsultasi" -> R.color.consultation_color
                    EventCategories.list.contains(cleanCategory) -> R.color.event_color
                    else -> R.color.colorPrimary
                }

                viewCategoryIndicator.setBackgroundColor(
                    ContextCompat.getColor(context, colorRes)
                )

                root.setOnClickListener {
                    onScheduleClick(schedule)
                }

                btnScheduleAction.setOnClickListener {
                    onScheduleClick(schedule)
                }
            }
        }
    }

    private class ScheduleDiffCallback : DiffUtil.ItemCallback<CalendarEntry>() {
        override fun areItemsTheSame(oldItem: CalendarEntry, newItem: CalendarEntry): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CalendarEntry, newItem: CalendarEntry): Boolean {
            return oldItem == newItem
        }
    }
}
