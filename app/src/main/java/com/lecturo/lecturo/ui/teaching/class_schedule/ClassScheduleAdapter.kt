package com.lecturo.lecturo.ui.teaching.class_schedule

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lecturo.lecturo.databinding.ItemClassScheduleBinding
import java.text.SimpleDateFormat
import java.util.*

// PERBAIKAN: Adapter sekarang bekerja dengan DisplayableClassSchedule
class ClassScheduleAdapter(
    private val onItemClick: (DisplayableClassSchedule) -> Unit
) : ListAdapter<DisplayableClassSchedule, ClassScheduleAdapter.ClassScheduleViewHolder>(ClassScheduleDiffCallback()) {

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

        // PERBAIKAN: Fungsi bind sekarang menerima DisplayableClassSchedule
        fun bind(item: DisplayableClassSchedule) {
            val entry = item.entry
            binding.apply {
                // Tampilkan nomor pertemuan yang sudah dihitung dari ViewModel
                textMeetingNumber.text = "P${item.meetingNumber}"

                textTitle.text = entry.title
                textDate.text = formatDate(entry.date)
                textTime.text = entry.time
                textCategory.text = entry.category

                root.setOnClickListener {
                    onItemClick(item)
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

    // PERBAIKAN: DiffUtil sekarang membandingkan DisplayableClassSchedule
    class ClassScheduleDiffCallback : DiffUtil.ItemCallback<DisplayableClassSchedule>() {
        override fun areItemsTheSame(oldItem: DisplayableClassSchedule, newItem: DisplayableClassSchedule): Boolean {
            // Kita bandingkan berdasarkan ID entri kalender yang unik
            return oldItem.entry.id == newItem.entry.id
        }

        override fun areContentsTheSame(oldItem: DisplayableClassSchedule, newItem: DisplayableClassSchedule): Boolean {
            return oldItem == newItem
        }
    }
}
