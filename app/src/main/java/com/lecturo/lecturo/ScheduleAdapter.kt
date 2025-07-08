package com.lecturo.lecturo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ScheduleAdapter(
    private var schedules: MutableList<Schedule>,  // ubah jadi var dan mutable
    private val onItemAction: (Schedule, String) -> Unit
) : RecyclerView.Adapter<ScheduleAdapter.ScheduleViewHolder>() {

    class ScheduleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleText: TextView = itemView.findViewById(R.id.textTitle)
        val dateText: TextView = itemView.findViewById(R.id.textDate)
        val timeText: TextView = itemView.findViewById(R.id.textTime)
        val locationText: TextView = itemView.findViewById(R.id.textLocation)
        val descriptionText: TextView = itemView.findViewById(R.id.textDescription)
        val editButton: ImageButton = itemView.findViewById(R.id.buttonEdit)
        val deleteButton: ImageButton = itemView.findViewById(R.id.buttonDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule, parent, false)
        return ScheduleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        val schedule = schedules[position]

        holder.titleText.text = schedule.title
        holder.dateText.text = schedule.date
        holder.timeText.text = schedule.time
        holder.locationText.text = schedule.location
        holder.descriptionText.text = schedule.description

        holder.descriptionText.visibility =
            if (schedule.description.isEmpty()) View.GONE else View.VISIBLE

        holder.editButton.setOnClickListener {
            onItemAction(schedule, "edit")
        }

        holder.deleteButton.setOnClickListener {
            onItemAction(schedule, "delete")
        }
    }

    override fun getItemCount() = schedules.size

    // Tambahkan fungsi updateData
    fun updateData(newSchedules: List<Schedule>) {
        schedules.clear()
        schedules.addAll(newSchedules)
        notifyDataSetChanged()
    }
}
