package com.lecturo.lecturo.ui.main

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.model.CalendarEntry
import java.util.Locale

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
        private val iconFrame: View = itemView.findViewById(R.id.iconFrame)
        private val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
        private val categoryTextView: TextView = itemView.findViewById(R.id.categoryTextView)
        private val priorityTextView: TextView = itemView.findViewById(R.id.priorityTextView)
        private val timeTextView: TextView = itemView.findViewById(R.id.timeTextView)

        fun bind(entry: CalendarEntry) {
            val context = itemView.context
            titleTextView.text = entry.title
            timeTextView.text = entry.time
            categoryTextView.text = entry.category

            val cleanCategory = entry.category.trim().lowercase(Locale.getDefault())
            val cleanPriority = entry.priority.trim().lowercase(Locale.getDefault())

            // 1. SETUP IKON & WARNA KATEGORI
            val iconRes: Int
            val categoryColorRes: Int

            when {
                cleanCategory == "mengajar" -> {
                    iconRes = R.drawable.ic_class
                    categoryColorRes = R.color.teaching_color
                }
                cleanCategory == "tugas" -> {
                    iconRes = R.drawable.ic_task
                    categoryColorRes = R.color.task_color
                }
                cleanCategory == "konsultasi" -> {
                    iconRes = R.drawable.ic_consultant
                    categoryColorRes = R.color.consultation_color
                }
                else -> { // Event & Lainya
                    iconRes = R.drawable.ic_event_2
                    categoryColorRes = R.color.event_color
                }
            }

            val resolvedCategoryColor = ContextCompat.getColor(context, categoryColorRes)
            iconImageView.setImageResource(iconRes)
            iconImageView.setColorFilter(Color.WHITE)
            iconFrame.backgroundTintList = ColorStateList.valueOf(resolvedCategoryColor)

            val neutralTextColor = ContextCompat.getColor(context, R.color.text_secondary)
            categoryTextView.setTextColor(neutralTextColor)

            // 2. SETUP BADGE PRIORITAS & PENERJEMAH OTOMATIS (PERBAIKAN BUG)
            val priorityTextColorRes: Int
            val priorityBgColorRes: Int
            val displayPriorityText: String // <--- Variabel baru untuk teks UI

            when (cleanPriority) {
                "tinggi", "high", "hight", "urgent" -> {
                    priorityTextColorRes = R.color.high_priority
                    priorityBgColorRes = R.color.high_priority_bg
                    displayPriorityText = "TINGGI" // Paksa jadi bahasa Indonesia
                }
                "rendah", "low" -> {
                    priorityTextColorRes = R.color.low_priority
                    priorityBgColorRes = R.color.low_priority_bg
                    displayPriorityText = "RENDAH" // Paksa jadi bahasa Indonesia
                }
                else -> { // Sedang / Medium
                    priorityTextColorRes = R.color.medium_priority
                    priorityBgColorRes = R.color.medium_priority_bg
                    displayPriorityText = "SEDANG" // Paksa jadi bahasa Indonesia
                }
            }

            // Terapkan Teks yang sudah diterjemahkan
            priorityTextView.text = displayPriorityText

            // Menerapkan warna prioritas
            val resolvedPriorityTextColor = ContextCompat.getColor(context, priorityTextColorRes)
            val resolvedPriorityBgColor = ContextCompat.getColor(context, priorityBgColorRes)

            priorityTextView.setTextColor(resolvedPriorityTextColor)
            priorityTextView.backgroundTintList = ColorStateList.valueOf(resolvedPriorityBgColor)

            // 3. LOGIKA KLIK
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