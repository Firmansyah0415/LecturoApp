package com.lecturo.lecturo.ui.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.lecturo.lecturo.data.db.AppDatabase
import com.lecturo.lecturo.data.repository.CalendarRepository
import com.lecturo.lecturo.data.repository.EventRepository
import com.lecturo.lecturo.data.repository.TasksRepository
import com.lecturo.lecturo.databinding.FragmentCalendarBinding

// Import Compose
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.model.CalendarDay
import com.lecturo.lecturo.data.model.CalendarEntry
import com.lecturo.lecturo.utils.EventCategories
import java.text.SimpleDateFormat
import java.util.*

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CalendarViewModel by viewModels {
        val context = requireContext()
        val database = AppDatabase.getDatabase(context)
        CalendarViewModelFactory(
            CalendarRepository(database.calendarEntryDao()),
            EventRepository(database.eventDao(), context.applicationContext),
            TasksRepository(database.tasksDao(), database.focusSessionDao(), context.applicationContext)
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.composeViewCalendar.setContent {
            MaterialTheme {
                CalendarScreen(viewModel = viewModel)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// ==========================================
// 🚀 JETPACK COMPOSE UI COMPONENTS
// ==========================================


@Composable
fun CalendarScreen(viewModel: CalendarViewModel) {
    val currentMonth by viewModel.currentMonth.observeAsState(Calendar.getInstance())
    val selectedDate by viewModel.selectedDate.observeAsState(Calendar.getInstance())
    val calendarDays by viewModel.calendarDays.observeAsState(emptyList())
    val schedules by viewModel.selectedDateSchedules.observeAsState(emptyList())

    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale("id", "ID")) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // --- KARTU KALENDER ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colorResource(R.color.card_background)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header (Panah Kiri - Bulan - Panah Kanan)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { viewModel.navigateToPreviousMonth() }) {
                        Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Prev", tint = colorResource(R.color.colorPrimary))
                    }
                    Text(
                        text = monthFormat.format(currentMonth.time),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorResource(R.color.text_primary)
                    )
                    IconButton(onClick = { viewModel.navigateToNextMonth() }) {
                        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "Next", tint = colorResource(R.color.colorPrimary))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Label Hari (SEN, SEL, dll)
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("MIN", "SEN", "SEL", "RAB", "KAM", "JUM", "SAB").forEach { day ->
                        Text(
                            text = day,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorResource(R.color.colorPrimary)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // GRID KALENDER 42 HARI
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.heightIn(max = 300.dp),
                    userScrollEnabled = false // Matikan scroll agar pas di card
                ) {
                    items(calendarDays) { day ->
                        val isSelected = dateFormat.format(selectedDate.time) == day.getFormattedDate()
                        DayCell(day, isSelected) { viewModel.selectDate(day.date) }
                    }
                }
            }
        }

        // --- DAFTAR JADWAL (RECYCLERVIEW PENGGANTI) ---
        Text(
            text = "Jadwal ${dateFormat.format(selectedDate.time)}",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = colorResource(R.color.text_primary),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (schedules.isEmpty()) {
            // Empty State
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_calendar_empty), // Pastikan ada icon ini
                    contentDescription = null,
                    tint = Color.LightGray,
                    modifier = Modifier.size(80.dp).padding(bottom = 16.dp)
                )
                Text("Tidak ada jadwal", color = Color.Gray, fontWeight = FontWeight.Bold)
            }
        } else {
            // List Jadwal
            LazyColumn(
                contentPadding = PaddingValues(bottom = 80.dp), // Jarak untuk BottomNav
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(schedules) { schedule ->
                    ScheduleItemCard(schedule)
                }
            }
        }
    }
}

@Composable
fun DayCell(day: CalendarDay, isSelected: Boolean, onClick: () -> Unit) {
    // 1. LOGIKA WARNA BACKGROUND (Latar Belakang)
    val bgColor = when {
        isSelected -> colorResource(R.color.colorPrimaryContainer)
        day.isToday -> colorResource(R.color.colorOnPrimaryContainer)
        else -> Color.Transparent
    }

    // 2. LOGIKA WARNA TEKS (Angka)
    val textColor = when {
        isSelected -> colorResource(R.color.text_primary)
        day.isToday -> colorResource(R.color.text_tertiary)
        !day.isCurrentMonth -> colorResource(R.color.text_secondary)
        else -> colorResource(R.color.text_primary)
    }

    Column(
        modifier = Modifier
            .aspectRatio(1f) // Buat kotak sempurna
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(enabled = day.isCurrentMonth, onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = day.dayNumber, color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)

        // --- LOGIKA 4 TITIK WARNA (PERBAIKAN BUG) ---
        if (day.hasSchedules() && day.isCurrentMonth) {
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 🔴 PERBAIKAN: Konversi kategori mentah menjadi Kategori Induk Utama
                val distinctParentCategories = day.scheduleCategories.mapNotNull { rawCategory ->
                    val cleanCat = rawCategory.trim().lowercase(Locale.getDefault())
                    when {
                        cleanCat == "tugas" -> "tugas"
                        cleanCat == "mengajar" -> "mengajar"
                        cleanCat == "konsultasi" -> "konsultasi"
                        EventCategories.list.contains(cleanCat) -> "acara" // Semua Rapat, Seminar, dll jadi "acara"
                        else -> null
                    }
                }.distinct() // Filter duplikat Induk Utama (1 Acara, 1 Tugas, dst.)

                // Gambar titik berdasarkan Kategori Induk yang sudah difilter
                distinctParentCategories.forEach { parentCategory ->
                    val dotColor = when (parentCategory) {
                        "tugas" -> colorResource(R.color.task_color)
                        "mengajar" -> colorResource(R.color.teaching_color)
                        "konsultasi" -> colorResource(R.color.consultation_color)
                        "acara" -> colorResource(R.color.event_color)
                        else -> Color.Transparent
                    }

                    if (dotColor != Color.Transparent) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(color = dotColor, shape = CircleShape)
                                .border(
                                    width = 1.dp,
                                    color = Color.White,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleItemCard(schedule: CalendarEntry) {
    val cleanCategory = schedule.category.trim().lowercase(Locale.getDefault())
    val categoryColor = when {
        cleanCategory == "tugas" -> colorResource(R.color.task_color)
        cleanCategory == "mengajar" -> colorResource(R.color.teaching_color)
        cleanCategory == "konsultasi" -> colorResource(R.color.consultation_color)
        EventCategories.list.contains(cleanCategory) -> colorResource(R.color.event_color)
        else -> colorResource(R.color.colorPrimary)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colorResource(R.color.card_background)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Garis indikator warna di sebelah kiri
            Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(categoryColor))

            Column(modifier = Modifier.padding(16.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = schedule.title,
                        fontWeight = FontWeight.Bold,
                        color = colorResource(R.color.colorPrimary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = schedule.time,
                        fontSize = 12.sp,
                        color = colorResource(R.color.text_secondary),
                        modifier = Modifier
                            .background(colorResource(R.color.colorBackground), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = schedule.category.uppercase(Locale.getDefault()),
                    fontSize = 10.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}