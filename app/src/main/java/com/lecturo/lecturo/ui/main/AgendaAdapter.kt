package com.lecturo.lecturo.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.model.CalendarEntry

class AgendaAdapter(
    private val onItemClick: (CalendarEntry) -> Unit
) : ListAdapter<CalendarEntry, AgendaAdapter.AgendaViewHolder>(AgendaDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AgendaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_agenda, parent, false)
        return AgendaViewHolder(view)
    }

    override fun onBindViewHolder(holder: AgendaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AgendaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconImageView: ImageView = itemView.findViewById(R.id.iconImageView)
        private val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
        private val categoryTextView: TextView = itemView.findViewById(R.id.categoryTextView)
        private val timeTextView: TextView = itemView.findViewById(R.id.timeTextView)

        fun bind(entry: CalendarEntry) {
            titleTextView.text = entry.title
            categoryTextView.text = entry.category
            timeTextView.text = entry.time

            // Set icon based on category
            val iconRes = when (entry.category) {
                "Class" -> R.drawable.ic_class
                "Event" -> R.drawable.ic_event
                "Task" -> R.drawable.ic_task
                "consultation" -> R.drawable.ic_consultant
                else -> R.drawable.ic_event
            }
            iconImageView.setImageResource(iconRes)

            // Set category color
            val categoryColor = when (entry.category) {
                "Class" -> R.color.colorPrimary
                "Event" -> R.color.colorAccent
                "Task" -> R.color.error_color
                "consultation" -> R.color.success_color
                else -> R.color.neutral_color
            }
            categoryTextView.setTextColor(itemView.context.getColor(categoryColor))

            itemView.setOnClickListener {
                onItemClick(entry)
            }
        }
    }

    class AgendaDiffCallback : DiffUtil.ItemCallback<CalendarEntry>() {
        override fun areItemsTheSame(oldItem: CalendarEntry, newItem: CalendarEntry): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CalendarEntry, newItem: CalendarEntry): Boolean {
            return oldItem == newItem
        }
    }
}
