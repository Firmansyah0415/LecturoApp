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

            binding.tvSessionType.text = "$startStr - $endStr"

            // [PERBAIKAN FORMAT WAKTU ASLI]
            val durationMillis = session.endTime - session.startTime
            val totalSeconds = durationMillis / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60

            val durationText = when {
                minutes > 0 && seconds > 0 -> "$minutes mnt $seconds dtk"
                minutes > 0 -> "$minutes mnt"
                else -> "$seconds dtk"
            }

            if (session.status == "COMPLETED") {
                binding.ivStatusIcon.setImageResource(R.drawable.ic_check_circle)
                binding.ivStatusIcon.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
                binding.tvSessionTime.text = "$dateStr • $durationText • Tuntas"
            } else {
                binding.ivStatusIcon.setImageResource(R.drawable.ic_cancle_circle) // pastikan nama file benar
                binding.ivStatusIcon.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F44336"))
                binding.tvSessionTime.text = "$dateStr • $durationText • Batal"
            }

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