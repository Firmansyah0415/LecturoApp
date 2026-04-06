package com.lecturo.lecturo.ui.consultation

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lecturo.lecturo.data.model.ConsultationPattern
import com.lecturo.lecturo.databinding.ItemConsultationPatternMiniBinding
import com.lecturo.lecturo.utils.DateHelper

class PatternAdapter(
    private val onPatternClick: (ConsultationPattern) -> Unit
) : ListAdapter<ConsultationPattern, PatternAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ItemConsultationPatternMiniBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ConsultationPattern) {
            binding.tvPatternTitle.text = item.titleTemplate

            // Format: "Every Monday • 09:00 - 11:00"
            val dayName = DateHelper.getDayName(item.dayOfWeek)
            binding.tvPatternDetail.text = "Every $dayName • ${item.startTime} - ${item.endTime}"

            binding.root.setOnClickListener {
                onPatternClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemConsultationPatternMiniBinding.inflate(
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