package com.lecturo.lecturo.ui.focus

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.model.FocusSession
import com.lecturo.lecturo.databinding.ItemFocusHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FocusHistoryAdapter(
    private val onDeleteClick: (FocusSession) -> Unit
) : ListAdapter<FocusSession, FocusHistoryAdapter.HistoryViewHolder>(DIFF_CALLBACK) {

    inner class HistoryViewHolder(private val binding: ItemFocusHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(session: FocusSession) {
            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            val dateStr = dateFormat.format(Date(session.startTime))
            val startStr = timeFormat.format(Date(session.startTime))
            val endStr = timeFormat.format(Date(session.endTime))

            // 1. Judul Utama adalah Rentang Waktu (Bukan Tulisan "Sesi ke-...")
            binding.tvSessionType.text = "$startStr - $endStr"

            // 2. Format Warna, Ikon, dan Sub-Judul berdasarkan status
            if (session.status == "COMPLETED") {
                binding.ivStatusIcon.setImageResource(R.drawable.ic_check_circle)
                binding.ivStatusIcon.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50")) // Hijau

                binding.tvSessionTime.text = "$dateStr • ${session.durationMinutes} mnt • Tuntas"
            } else {
                binding.ivStatusIcon.setImageResource(R.drawable.ic_cancle_circle) // Pastikan nama drawable ini benar
                binding.ivStatusIcon.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F44336")) // Merah

                binding.tvSessionTime.text = "$dateStr • ${session.durationMinutes} mnt • Batal"
            }

            // 3. Tombol Hapus
            binding.btnDeleteSession.setOnClickListener {
                onDeleteClick(session)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemFocusHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<FocusSession>() {
            override fun areItemsTheSame(oldItem: FocusSession, newItem: FocusSession): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: FocusSession, newItem: FocusSession): Boolean {
                return oldItem == newItem
            }
        }
    }
}