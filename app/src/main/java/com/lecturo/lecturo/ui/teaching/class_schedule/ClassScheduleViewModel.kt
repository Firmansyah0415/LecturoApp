package com.lecturo.lecturo.ui.teaching.class_schedule

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.lecturo.lecturo.data.model.CalendarEntry
import com.lecturo.lecturo.data.repository.CalendarRepository
import java.text.SimpleDateFormat
import java.util.*

class ClassScheduleViewModel(private val repository: CalendarRepository) : ViewModel() {

    // Ambil data mentah dari repository yang kategorinya "Mengajar"
    private val rawTeachingEntries: LiveData<List<CalendarEntry>> = repository.getEntriesByCategory("Mengajar")

    // --- PERBAIKAN DI SINI ---
    // LiveData ini sekarang akan mengeluarkan daftar DisplayableClassSchedule yang sudah siap tampil
    val displayableSchedules: LiveData<List<DisplayableClassSchedule>> = rawTeachingEntries.map { entries ->
        // 1. Kelompokkan semua entri berdasarkan ID aturan mengajarnya (sourceFeatureId)
        val groupedByRule = entries.groupBy { it.sourceFeatureId }

        val displayableList = mutableListOf<DisplayableClassSchedule>()

        // 2. Untuk setiap grup (setiap mata kuliah)...
        groupedByRule.forEach { (_, entriesForRule) ->
            // Urutkan jadwal untuk mata kuliah ini secara kronologis
            val sortedEntries = entriesForRule.sortedWith(compareBy { entry ->
                parseDate(entry.date, entry.time)
            })

            // 3. Beri nomor urut (1, 2, 3, ...) untuk setiap jadwal dalam grup ini
            sortedEntries.forEachIndexed { index, entry ->
                displayableList.add(
                    DisplayableClassSchedule(
                        entry = entry,
                        meetingNumber = index + 1 // Nomor pertemuan dimulai dari 1
                    )
                )
            }
        }

        // 4. Terakhir, urutkan daftar gabungan secara kronologis untuk tampilan "Semua"
        displayableList.sortedWith(compareBy { displayableItem ->
            parseDate(displayableItem.entry.date, displayableItem.entry.time)
        })
    }

    // Fungsi bantuan untuk parsing tanggal agar bisa diurutkan
    private fun parseDate(dateStr: String, timeStr: String): Date {
        return try {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).parse("$dateStr $timeStr") ?: Date(0)
        } catch (e: Exception) {
            Date(0)
        }
    }
}
