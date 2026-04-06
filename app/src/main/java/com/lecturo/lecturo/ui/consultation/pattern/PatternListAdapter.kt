package com.lecturo.lecturo.ui.consultation.pattern

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lecturo.lecturo.data.model.ConsultationPattern
import com.lecturo.lecturo.databinding.ItemConsultationPatternBinding
import com.lecturo.lecturo.utils.DateHelper

class PatternListAdapter(
    private val onItemClick: (ConsultationPattern) -> Unit,
    private val onSwitchToggle: (ConsultationPattern, Boolean) -> Unit
) : ListAdapter<ConsultationPattern, PatternListAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ItemConsultationPatternBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ConsultationPattern) {
            binding.tvPatternTitle.text = item.titleTemplate
            val dayName = DateHelper.getDayName(item.dayOfWeek) // Pakai helper yang sudah dibuat
            // Translate dayName to Indo manually or ensure helper returns Indo
            // Asumsi helper return English, kita bisa map sederhana:
            val hariIndo = mapDayToIndo(dayName)

            binding.tvPatternDetail.text = "Setiap $hariIndo • ${item.startTime} - ${item.endTime}"

            // Hindari trigger listener saat setting initial state
            binding.switchActive.setOnCheckedChangeListener(null)
            binding.switchActive.isChecked = item.isActive

            binding.switchActive.setOnCheckedChangeListener { _, isChecked ->
                onSwitchToggle(item, isChecked)
            }

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    private fun mapDayToIndo(englishDay: String): String {
        return when(englishDay) {
            "Sunday" -> "Minggu"
            "Monday" -> "Senin"
            "Tuesday" -> "Selasa"
            "Wednesday" -> "Rabu"
            "Thursday" -> "Kamis"
            "Friday" -> "Jumat"
            "Saturday" -> "Sabtu"
            else -> englishDay
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemConsultationPatternBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<ConsultationPattern>() {
        override fun areItemsTheSame(oldItem: ConsultationPattern, newItem: ConsultationPattern) =
            oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ConsultationPattern, newItem: ConsultationPattern) =
            oldItem == newItem
    }
}