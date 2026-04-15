package com.lecturo.lecturo.ui.calendar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.model.CalendarDay
import com.lecturo.lecturo.databinding.ItemCalendarDayBinding
import java.util.*
import com.lecturo.lecturo.utils.EventCategories

class CalendarDayAdapter(
    private val onDayClick: (CalendarDay) -> Unit
) : ListAdapter<CalendarDay, CalendarDayAdapter.CalendarDayViewHolder>(CalendarDayDiffCallback()) {

    private var selectedDateString: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarDayViewHolder {
        val binding = ItemCalendarDayBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CalendarDayViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CalendarDayViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun setSelectedDate(date: String) {
        selectedDateString = date
        notifyDataSetChanged()
    }

    inner class CalendarDayViewHolder(
        private val binding: ItemCalendarDayBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(calendarDay: CalendarDay) {
            val context = binding.root.context
            binding.tvDayNumber.text = calendarDay.dayNumber

            if (calendarDay.isCurrentMonth) {
                binding.root.alpha = 1.0f
                binding.tvDayNumber.setTextColor(ContextCompat.getColor(context, R.color.text_primary))

                val isSelected = selectedDateString == calendarDay.getFormattedDate()
                when {
                    isSelected -> {
                        binding.cardDayBackground.setCardBackgroundColor(
                            ContextCompat.getColor(context, R.color.colorOnPrimaryContainer)
                        )
                        binding.tvDayNumber.setTextColor(
                            ContextCompat.getColor(context, R.color.text_tertiary)
                        )
                    }
                    calendarDay.isToday -> {
                        binding.cardDayBackground.setCardBackgroundColor(
                            ContextCompat.getColor(context, R.color.colorOnPrimaryContainer)
                        )
                        binding.tvDayNumber.setTextColor(
                            ContextCompat.getColor(context, R.color.text_tertiary)
                        )
                    }
                    else -> {
                        binding.cardDayBackground.setCardBackgroundColor(
                            ContextCompat.getColor(context, android.R.color.transparent)
                        )
                        binding.tvDayNumber.setTextColor(
                            ContextCompat.getColor(context, R.color.text_primary)
                        )
                    }
                }

                binding.root.setOnClickListener {
                    onDayClick(calendarDay)
                }

            } else {
                binding.root.alpha = 0.4f
                binding.tvDayNumber.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                binding.cardDayBackground.setCardBackgroundColor(
                    ContextCompat.getColor(context, android.R.color.transparent)
                )
                binding.root.setOnClickListener(null)
            }

            showScheduleIndicators(calendarDay.scheduleCategories)
        }

        // --- LANGKAH 2: Perbaiki fungsi ini ---
        private fun showScheduleIndicators(categories: Set<String>) {
            with(binding) {
                // Sembunyikan semua indikator terlebih dahulu
                indicatorTask.visibility = View.GONE
                indicatorTeaching.visibility = View.GONE
                indicatorEvent.visibility = View.GONE
                indicatorConsultation.visibility = View.GONE

                // Loop melalui setiap kategori yang ada pada hari itu
                categories.forEach { category ->
                    // Bersihkan kategori untuk perbandingan yang andal
                    val cleanCategory = category.trim().lowercase(Locale.getDefault())

                    // Gunakan logika if/else if yang lebih fleksibel
                    if (cleanCategory == "tugas") {
                        indicatorTask.visibility = View.VISIBLE
                    } else if (cleanCategory == "mengajar") {
                        indicatorTeaching.visibility = View.VISIBLE
                    } else if (cleanCategory == "konsultasi") {
                        indicatorConsultation.visibility = View.VISIBLE
                    } else if (EventCategories.list.contains(cleanCategory)) {
                        // Jika kategori ada di dalam daftar event, tampilkan indikator event
                        indicatorEvent.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private class CalendarDayDiffCallback : DiffUtil.ItemCallback<CalendarDay>() {
        override fun areItemsTheSame(oldItem: CalendarDay, newItem: CalendarDay): Boolean {
            return oldItem.date.timeInMillis == newItem.date.timeInMillis
        }

        override fun areContentsTheSame(oldItem: CalendarDay, newItem: CalendarDay): Boolean {
            return oldItem == newItem
        }
    }
}
