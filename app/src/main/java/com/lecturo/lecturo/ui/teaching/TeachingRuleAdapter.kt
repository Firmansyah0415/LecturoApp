package com.lecturo.lecturo.ui.teaching

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lecturo.lecturo.data.model.TeachingRule
import com.lecturo.lecturo.databinding.ItemTeachingRuleBinding

class TeachingRuleAdapter(
    private val onDeleteClick: (TeachingRule) -> Unit,
    private val onItemClick: (TeachingRule) -> Unit
) : ListAdapter<TeachingRule, TeachingRuleAdapter.TeachingRuleViewHolder>(TeachingRuleDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeachingRuleViewHolder {
        val binding = ItemTeachingRuleBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TeachingRuleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TeachingRuleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TeachingRuleViewHolder(
        private val binding: ItemTeachingRuleBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        // --- FUNGSI BIND YANG DIPERBAIKI ---
        fun bind(rule: TeachingRule) {
            binding.apply {
                textCourseName.text = rule.courseName
                textClassName.text = rule.className
                textDayOfWeek.text = rule.dayOfWeek
                textTimeRange.text = "${rule.startTime} - ${rule.endTime}"
                textLocation.text = rule.location
                textStudentCount.text = "${rule.studentCount} Mahasiswa"

                // PERBAIKAN: Logika untuk menampilkan periode semester
                val semesterPeriodText = if (rule.repetitionType == "DATE") {
                    "Semester: ${rule.semesterStartDate} - ${rule.repetitionValue}"
                } else {
                    "Semester: Mulai ${rule.semesterStartDate} (${rule.repetitionValue} pertemuan)"
                }
                textSemesterPeriod.text = semesterPeriodText

                buttonDelete.setOnClickListener {
                    onDeleteClick(rule)
                }

                root.setOnClickListener {
                    onItemClick(rule)
                }
            }
        }
    }

    class TeachingRuleDiffCallback : DiffUtil.ItemCallback<TeachingRule>() {
        override fun areItemsTheSame(oldItem: TeachingRule, newItem: TeachingRule): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TeachingRule, newItem: TeachingRule): Boolean {
            return oldItem == newItem
        }
    }
}
