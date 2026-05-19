package com.lecturo.lecturo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.model.TeachingSchedule
import com.lecturo.lecturo.viewmodel.teaching.TeachingViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeachingListContent(
    allSchedules: List<TeachingSchedule>,
    viewModel: TeachingViewModel, // Digunakan untuk memantau Search & Sort
    onEdit: (TeachingSchedule) -> Unit,
    onDelete: (TeachingSchedule) -> Unit,
    onStatusToggle: (TeachingSchedule) -> Unit
) {
    var selectedDay by remember { mutableStateOf("Semua") }
    var statusFilter by remember { mutableStateOf("Semua") } // Walaupun "Semua" secara logika, UI akan hanya menampilkan Belum & Selesai
    var selectedScheduleForSheet by remember { mutableStateOf<TeachingSchedule?>(null) }

    val searchQuery by viewModel.searchQuery.observeAsState("")
    val isSortNewest by viewModel.isSortNewest.observeAsState(true)

    val days = listOf("Semua", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
    val inFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    val processedList = allSchedules.filter {
        val matchDay = if (selectedDay == "Semua") true else it.dayOfWeek.equals(selectedDay, ignoreCase = true)
        val matchSearch = it.courseName.contains(searchQuery, ignoreCase = true) || it.classCode.contains(searchQuery, ignoreCase = true)
        val matchStatus = when (statusFilter) {
            "Selesai" -> it.isCompleted
            "Belum" -> !it.isCompleted
            else -> true
        }
        matchDay && matchSearch && matchStatus
    }.sortedWith { s1, s2 ->
        val d1 = try { inFormat.parse(s1.date) } catch (e: Exception) { Date(0) }
        val d2 = try { inFormat.parse(s2.date) } catch (e: Exception) { Date(0) }
        if (isSortNewest) d2.compareTo(d1) else d1.compareTo(d2)
    }

    val listState = rememberLazyListState()

    // Animasikan ke indeks 0 apabila sortir berubah
//    LaunchedEffect(isSortNewest) {
//        if (processedList.isNotEmpty()) {
//            listState.animateScrollToItem(0)
//        }
//    }

//    LaunchedEffect(isSortNewest) {
//        if (processedList.isNotEmpty()) {
//            // Trik Optimasi: Lompat dulu jika posisi jauh di bawah
//            if (listState.firstVisibleItemIndex > 5) {
//                listState.scrollToItem(5)
//            }
//
//            // Jeda 1 frame agar Compose tidak ngos-ngosan
//            kotlinx.coroutines.yield()
//
//            // Eksekusi animasi sisa jarak pendeknya
//            listState.animateScrollToItem(0)
//        }
//    }

    LaunchedEffect(isSortNewest) {
        if (processedList.isNotEmpty()) {
            listState.scrollToItem(0) // Langsung pindah ke 0 tanpa animasi
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // 🔴 FILTER BELUM DAN SELESAI
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val chipColors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = colorResource(R.color.teaching_color),
                selectedLabelColor = Color.White
            )

            FilterChip(
                selected = statusFilter == "Belum",
                onClick = { statusFilter = if (statusFilter == "Belum") "Semua" else "Belum" },
                label = { Text("Belum") },
                colors = chipColors,
                modifier = Modifier.padding(end = 8.dp)
            )

            FilterChip(
                selected = statusFilter == "Selesai",
                onClick = { statusFilter = if (statusFilter == "Selesai") "Semua" else "Selesai" },
                label = { Text("Selesai") },
                colors = chipColors
            )
        }

        // 🔴 FILTER HARI
        ScrollableTabRow(
            selectedTabIndex = days.indexOf(selectedDay),
            edgePadding = 16.dp,
            containerColor = Color.Transparent,
            contentColor = colorResource(R.color.teaching_color),
            indicator = {}
        ) {
            days.forEach { day ->
                val isSelected = selectedDay == day
                Tab(
                    selected = isSelected,
                    onClick = { selectedDay = day },
                    modifier = Modifier
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) colorResource(R.color.teaching_color) else Color.Transparent)
                ) {
                    Text(
                        text = day,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        color = if (isSelected) Color.White else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Tampilan List Utama
        if (processedList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Belum ada jadwal mengajar yang sesuai.", color = Color.Gray, fontSize = 14.sp)
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 90.dp)) {
                items(processedList, key = { it.localId }) { item ->
                    TeachingScheduleCard(
                        schedule = item,
                        onClick = { selectedScheduleForSheet = item },
                        onStatusToggle = { onStatusToggle(item) }
                    )
                }
            }
        }
    }

    // Modal Bottom Sheet Pemicu Aksi
    selectedScheduleForSheet?.let { schedule ->
        TeachingActionBottomSheet(
            schedule = schedule,
            onDismiss = { selectedScheduleForSheet = null },
            onEdit = { onEdit(schedule) },
            onDelete = { onDelete(schedule) },
            onStatusToggle = { onStatusToggle(schedule) }
        )
    }
}

// ... Sisanya tetap sama untuk TeachingScheduleCard & BottomSheet
@Composable
fun TeachingScheduleCard(
    schedule: TeachingSchedule,
    onClick: () -> Unit,
    onStatusToggle: () -> Unit
) {
    val themeColor = colorResource(R.color.teaching_color)
    val inputFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val outputFormat = remember { SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID")) }

    val cleanDisplayDate = remember(schedule.date) {
        try {
            val parsedDate = inputFormat.parse(schedule.date)
            if (parsedDate != null) outputFormat.format(parsedDate) else schedule.date
        } catch (e: Exception) {
            schedule.date
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colorResource(R.color.card_background)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(42.dp).background(themeColor, CircleShape)
            ) {
                Text("P${schedule.meetingNumber}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = schedule.courseName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(R.color.text_primary),
                    textDecoration = if (schedule.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )
                Text(
                    text = "Kelas ${schedule.classCode} • ${schedule.studentCount} Mhs",
                    fontSize = 13.sp,
                    color = themeColor,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_date), null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = cleanDisplayDate, fontSize = 13.sp, color = Color.Gray)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(painterResource(R.drawable.ic_time), null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "${schedule.startTime} - ${schedule.endTime}", fontSize = 13.sp, color = Color.Gray)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(painterResource(R.drawable.ic_location), null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = schedule.classroom, fontSize = 13.sp, color = Color.Gray)
                }
            }

            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (schedule.isCompleted) Color(0xFF4CAF50) else Color.Transparent)
                    .border(2.dp, if (schedule.isCompleted) Color(0xFF4CAF50) else Color.LightGray, CircleShape)
                    .clickable { onStatusToggle() },
                contentAlignment = Alignment.Center
            ) {
                if (schedule.isCompleted) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeachingActionBottomSheet(
    schedule: TeachingSchedule,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onStatusToggle: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colorResource(R.color.card_background),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(width = 40.dp, height = 4.dp) }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 36.dp)) {
            Text(
                text = schedule.courseName,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = colorResource(R.color.text_primary)
            )

            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f), thickness = 1.dp, modifier = Modifier.padding(bottom = 8.dp))

            BottomSheetItemRow(
                iconRes = if (schedule.isCompleted) R.drawable.ic_clear else R.drawable.ic_check_circle,
                title = if (schedule.isCompleted) "Tandai Belum Selesai" else "Tandai Selesai Mengajar",
                color = Color(0xFF4CAF50),
                onClick = { onStatusToggle(); onDismiss() }
            )

            BottomSheetItemRow(
                iconRes = R.drawable.ic_edit_calendar,
                title = "Edit Rincian Pertemuan",
                color = colorResource(id = R.color.text_primary),
                backgroundColor = colorResource(id = R.color.icon_placeholder),
                onClick = { onEdit(); onDismiss() }
            )

            BottomSheetItemRow(
                iconRes = R.drawable.ic_delete_fill,
                title = "Hapus Jadwal Permanen",
                color = Color(0xFFD32F2F),
                backgroundColor = Color(0xFFFFEBEE),
                onClick = { onDelete(); onDismiss() }
            )
        }
    }
}