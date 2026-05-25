package com.lecturo.lecturo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.model.Event
import com.lecturo.lecturo.utils.toReadableDate
import kotlinx.coroutines.yield

// ==========================================
// 1. LIST EVENT & KATEGORI
// ==========================================
@Composable
fun EventListContent(
    events: List<Event>,
    categories: List<String>,
    activeCategory: String,
    isSortNewest: Boolean,
    onCategoryClick: (String) -> Unit,
    onEdit: (Event) -> Unit,
    onDelete: (Event) -> Unit,
    onStatusToggle: (Event) -> Unit
) {
    var selectedEventForSheet by remember { mutableStateOf<Event?>(null) }
    val allFilters = listOf("Semua") + categories
//    val listState = rememberLazyListState()
//
//    LaunchedEffect(isSortNewest) {
//        if (events.isNotEmpty()) {
//            listState.animateScrollToItem(0)
//        }
//    }

    val listState = rememberLazyListState()

    // 🔴 PERBAIKAN: Gunakan logika scroll langsung agar konsisten dengan Teaching
    LaunchedEffect(isSortNewest, activeCategory) {
        if (events.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Baris Filter Kategori (Mendatar)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(allFilters) { category ->
                val isSelected = if (category == "Semua") activeCategory == "" else activeCategory == category
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (category == "Semua") onCategoryClick("") else onCategoryClick(category)
                    },
                    label = { Text(text = category, fontWeight = FontWeight.Bold) },
                    leadingIcon = if (isSelected) {
                        {
                            val iconRes = when (category.lowercase(java.util.Locale.getDefault())) {
                                "rapat" -> R.drawable.ic_meet
                                "seminar" -> R.drawable.ic_seminar
                                "webinar" -> R.drawable.ic_webinar
                                "workshop", "lokakarya" -> R.drawable.ic_workshop
                                "penelitian" -> R.drawable.ic_research
                                "pengabdian masyarakat" -> R.drawable.ic_community
                                else -> R.drawable.ic_event_available
                            }
                            Icon(painterResource(id = iconRes), null, modifier = Modifier.size(FilterChipDefaults.IconSize))
                        }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = colorResource(R.color.event_color_light),
                        labelColor = colorResource(R.color.chip_event_text_color),
                        selectedContainerColor = colorResource(R.color.event_color),
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true, selected = isSelected,
                        borderColor = colorResource(R.color.event_color_light),
                        selectedBorderColor = colorResource(R.color.event_color)
                    )
                )
            }
        }

        // Tampilan List Acara
        if (events.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Tidak ada acara yang sesuai.", color = Color.Gray, fontSize = 14.sp)
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 90.dp)) {
                items(events, key = { it.id }) { item ->
                    EventScheduleCard(
                        event = item,
                        onClick = { selectedEventForSheet = item },
                        onStatusToggle = { onStatusToggle(item) }
                    )
                }
            }
        }
    }

    selectedEventForSheet?.let { event ->
        EventActionBottomSheet(
            event = event,
            onDismiss = { selectedEventForSheet = null },
            onEdit = { onEdit(event) },
            onDelete = { onDelete(event) },
            onStatusToggle = { onStatusToggle(event) }
        )
    }
}

// ==========================================
// 2. KARTU ITEM EVENT (MODERN)
// ==========================================
@Composable
fun EventScheduleCard(
    event: Event,
    onClick: () -> Unit,
    onStatusToggle: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colorResource(R.color.card_background)),
        border = BorderStroke(1.dp, colorResource(com.google.android.material.R.color.m3_sys_color_light_outline_variant)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {

            // Tombol Checkbox Lingkaran Hijau
            Box(
                modifier = Modifier.size(26.dp).clip(CircleShape)
                    .background(if (event.isCompleted) Color(0xFF4CAF50) else Color.Transparent)
                    .border(2.dp, if (event.isCompleted) Color(0xFF4CAF50) else Color.LightGray, CircleShape)
                    .clickable { onStatusToggle() },
                contentAlignment = Alignment.Center
            ) {
                if (event.isCompleted) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colorResource(R.color.text_primary),
                    textDecoration = if (event.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )

                // Chip Kategori Kecil di bawah judul
                Box(
                    modifier = Modifier.padding(top = 6.dp, bottom = 8.dp).border(1.dp, colorResource(R.color.event_color), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(text = event.category, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colorResource(R.color.event_color))
                }

                // Tanggal, Waktu, Lokasi
                val timeDisplay = if (event.endTime.isNotEmpty()) "${event.time} - ${event.endTime}" else event.time
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_date), null, tint = colorResource(R.color.event_color), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "${event.date.toReadableDate()} • $timeDisplay", fontSize = 12.sp, color = colorResource(R.color.text_secondary))
                }
                if (!event.location.isNullOrEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(painterResource(R.drawable.ic_location), null, tint = colorResource(R.color.event_color), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = event.location, fontSize = 12.sp, color = colorResource(R.color.text_secondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (!event.description.isNullOrEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 1.dp, color = Color.LightGray.copy(alpha = 0.5f))
                    Text(text = event.description, fontSize = 12.sp, color = colorResource(R.color.text_secondary), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ==========================================
// 3. BOTTOM SHEET EVENT
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventActionBottomSheet(
    event: Event, onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onStatusToggle: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = colorResource(R.color.card_background),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 36.dp)) {
            Text(text = event.title, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = colorResource(R.color.text_primary))
            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f), thickness = 1.dp, modifier = Modifier.padding(bottom = 8.dp))

            BottomSheetItemRow(
                iconRes = if (event.isCompleted) R.drawable.ic_clear else R.drawable.ic_check_circle,
                title = if (event.isCompleted) "Tandai Belum Selesai" else "Tandai Selesai",
                color = Color(0xFF4CAF50),
                onClick = { onStatusToggle(); onDismiss() }
            )

            BottomSheetItemRow(
                iconRes = R.drawable.ic_edit_calendar,
                title = "Edit Rincian Acara",
                color = colorResource(id = R.color.text_primary),
                backgroundColor = colorResource(id = R.color.icon_placeholder),
                onClick = { onEdit(); onDismiss() }
            )
            BottomSheetItemRow(
                iconRes = R.drawable.ic_delete_fill,
                title = "Hapus Acara",
                color = Color(0xFFD32F2F),
                backgroundColor = Color(0xFFFFEBEE),
                onClick = { onDelete(); onDismiss() }
            )
        }
    }
}

// (Biarkan fungsi AiOptionsComposeDialog & AiOptionItem yang asli kamu copy ke bagian paling bawah file ini)
// ==========================================
// 🚀 JETPACK COMPOSE UI COMPONENT (DIALOG AI)
// ==========================================

@Composable
fun AiOptionsComposeDialog(onDismiss: () -> Unit, onItemClick: (Int) -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp), // Membuat ujung membulat khas Material 3
        colors = CardDefaults.cardColors(containerColor = colorResource(id = R.color.card_background)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Pilih Sumber Data (AI)",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                color = colorResource(id = R.color.colorPrimary),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Opsi 1: Kamera
            AiOptionItem(
                iconRes = R.drawable.ic_camera,
                title = "Ambil dari Kamera",
                colorRes = R.color.task_color // Pakai warna task/oren sebagai highlight
            ) { onItemClick(0) }

            // Opsi 2: Galeri
            AiOptionItem(
                iconRes = R.drawable.ic_gallery,
                title = "Pilih dari Galeri",
                colorRes = R.color.event_color // Pakai warna event/hijau
            ) { onItemClick(1) }

            // Opsi 3: PDF
            AiOptionItem(
                iconRes = R.drawable.ic_pdf,
                title = "Dokumen PDF / File",
                colorRes = R.color.consultation_color // Pakai warna konsultasi/merah atau lainnya
            ) { onItemClick(2) }

            Spacer(modifier = Modifier.height(16.dp))

            // Tombol Batal
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = "Batal",
                    color = colorResource(id = R.color.text_secondary),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun AiOptionItem(iconRes: Int, title: String, colorRes: Int, onClick: () -> Unit) {
    val mainColor = colorResource(id = colorRes)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp)
    ) {
        // Kotak background untuk ikon dengan efek transparansi 15%
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .background(mainColor.copy(alpha = 0.15f), CircleShape)
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                tint = mainColor,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Teks Menu
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = colorResource(id = R.color.text_primary)
        )
    }
}