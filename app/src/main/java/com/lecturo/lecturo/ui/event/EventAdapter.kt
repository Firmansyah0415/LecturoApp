package com.lecturo.lecturo.ui.event

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lecturo.lecturo.data.model.Event
import com.lecturo.lecturo.databinding.ItemEventBinding

class EventAdapter(
    private val onCompletedChanged: (Event, Boolean) -> Unit,
    private val onDeleteClick: (Event) -> Unit,
    private val onItemClick: (Event) -> Unit
) : ListAdapter<Event, EventAdapter.EventViewHolder>(EventDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ItemEventBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return EventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class EventViewHolder(
        private val binding: ItemEventBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(event: Event) {
            binding.apply {
                // Set data
                textTitle.text = event.title
                chipCategory.text = event.category
                textDate.text = event.date
                textTime.text = event.time
                textLocation.text = event.location
                textDescription.text = event.description ?: ""

                // Hide description if empty
                textDescription.visibility = if (event.description.isNullOrEmpty()) {
                    android.view.View.GONE
                } else {
                    android.view.View.VISIBLE
                }

                // Set checkbox
                checkboxCompleted.isChecked = event.isCompleted

                // Apply strikethrough effect if completed
                if (event.isCompleted) {
                    textTitle.paintFlags = textTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    root.alpha = 0.6f
                } else {
                    textTitle.paintFlags = textTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    root.alpha = 1.0f
                }

                // Set listeners
                checkboxCompleted.setOnCheckedChangeListener { _, isChecked ->
                    onCompletedChanged(event, isChecked)
                }

                buttonDelete.setOnClickListener {
                    onDeleteClick(event)
                }

                root.setOnClickListener {
                    onItemClick(event)
                }
            }
        }
    }

    class EventDiffCallback : DiffUtil.ItemCallback<Event>() {
        override fun areItemsTheSame(oldItem: Event, newItem: Event): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Event, newItem: Event): Boolean {
            return oldItem == newItem
        }
    }
}