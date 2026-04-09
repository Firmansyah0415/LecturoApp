package com.lecturo.lecturo.ui.consultation

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lecturo.lecturo.data.model.ConsultationSchedule
import com.lecturo.lecturo.databinding.ItemConsultationScheduleBinding
import java.text.SimpleDateFormat
import java.util.Locale

class ConsultationAdapter(
    private val onItemClick: (ConsultationSchedule) -> Unit
) : ListAdapter<ConsultationSchedule, ConsultationAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ItemConsultationScheduleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ConsultationSchedule) {
            binding.tvTitle.text = item.title

            // --- [PERBAIKAN BUG 15] ALAT BACA TANGGAL ---
            // Jadikan dd/MM/yyyy sebagai format utama yang diharapkan
            val dateInputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val dateOutputFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID"))
            try {
                val dateObj = dateInputFormat.parse(item.date)
                binding.tvDate.text = dateOutputFormat.format(dateObj!!)
            } catch (e: Exception) {
                // Fallback: Jika ternyata ada sisa data lama yang masih pakai yyyy-MM-dd
                val fallbackInput = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                try {
                    val fallbackDate = fallbackInput.parse(item.date)
                    binding.tvDate.text = dateOutputFormat.format(fallbackDate!!)
                } catch (ex: Exception) {
                    // Jika hancur semua, tampilkan apa adanya
                    binding.tvDate.text = item.date
                }
            }

            binding.tvTime.text = "${item.startTime} - ${item.endTime}"
            binding.tvLocation.text = item.location ?: "Lokasi tidak ditentukan"

            // --- LOGIKA STATUS & WARNA UI (ELEGAN) ---
            val statusText: String
            val textColorHex: String
            val bgColorHex: String

            when (item.status.uppercase(Locale.getDefault())) {
                "SCHEDULED" -> {
                    statusText = "TERJADWAL"
                    textColorHex = "#1976D2" // Biru Material
                    bgColorHex = "#E3F2FD"   // Biru Sangat Muda
                }
                "COMPLETED" -> {
                    statusText = "SELESAI"
                    textColorHex = "#2E7D32" // Hijau Material
                    bgColorHex = "#E8F5E9"   // Hijau Sangat Muda
                }
                "CANCELLED" -> {
                    statusText = "BATAL"
                    textColorHex = "#D32F2F" // Merah Material
                    bgColorHex = "#FFEBEE"   // Merah Sangat Muda
                }
                else -> {
                    statusText = item.status.uppercase()
                    textColorHex = "#616161" // Abu-abu
                    bgColorHex = "#F5F5F5"   // Abu-abu Sangat Muda
                }
            }

            // Terapkan ke Custom TextView Badge
            binding.tvStatusBadge.text = statusText
            binding.tvStatusBadge.setTextColor(Color.parseColor(textColorHex))
            binding.tvStatusBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor(bgColorHex))

            // Klik item
            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemConsultationScheduleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<ConsultationSchedule>() {
        override fun areItemsTheSame(oldItem: ConsultationSchedule, newItem: ConsultationSchedule) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ConsultationSchedule, newItem: ConsultationSchedule) =
            oldItem == newItem
    }
}