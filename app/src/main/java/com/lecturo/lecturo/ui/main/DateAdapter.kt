package com.lecturo.lecturo.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lecturo.lecturo.R
import java.text.SimpleDateFormat
import java.util.*

data class DateItem(
    val date: Date,
    val isSelected: Boolean = false
)

class DateAdapter(
    private val onDateClick: (Date) -> Unit
) : ListAdapter<DateItem, DateAdapter.DateViewHolder>(DateDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DateViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_date, parent, false)
        return DateViewHolder(view)
    }

    override fun onBindViewHolder(holder: DateViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DateViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dayTextView: TextView = itemView.findViewById(R.id.dayTextView)
        private val dateTextView: TextView = itemView.findViewById(R.id.dateTextView)

        fun bind(dateItem: DateItem) {
            val dayFormat = SimpleDateFormat("EEE", Locale("id", "ID"))
            val dateFormat = SimpleDateFormat("dd", Locale.getDefault())

            dayTextView.text = dayFormat.format(dateItem.date).uppercase()
            dateTextView.text = dateFormat.format(dateItem.date)

            // Highlight selected date
            if (dateItem.isSelected) {
                itemView.setBackgroundResource(R.drawable.selected_date_background)
                dayTextView.setTextColor(itemView.context.getColor(android.R.color.white))
                dateTextView.setTextColor(itemView.context.getColor(android.R.color.white))
            } else {
                itemView.setBackgroundResource(R.drawable.default_date_background)
                dayTextView.setTextColor(itemView.context.getColor(R.color.colorPrimary))
                dateTextView.setTextColor(itemView.context.getColor(R.color.colorPrimary))
            }

            itemView.setOnClickListener {
                onDateClick(dateItem.date)
            }
        }
    }

    class DateDiffCallback : DiffUtil.ItemCallback<DateItem>() {
        override fun areItemsTheSame(oldItem: DateItem, newItem: DateItem): Boolean {
            return oldItem.date.time == newItem.date.time
        }

        override fun areContentsTheSame(oldItem: DateItem, newItem: DateItem): Boolean {
            return oldItem == newItem
        }
    }
}
