package com.lecturo.lecturo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.model.TaskWithFocusStats
import com.lecturo.lecturo.data.model.Tasks
import com.lecturo.lecturo.utils.FocusPreferences
import com.lecturo.lecturo.utils.toReadableDate

@Composable
fun TaskListItemCompose(
    item: TaskWithFocusStats,
    onActionClick: (Tasks, String) -> Unit
) {
    val tasks = item.task
    val context = LocalContext.current
    val prefs = remember { FocusPreferences(context) }

    val activeTaskId = prefs.getActiveTaskId()
    val isTaskActiveFocus = activeTaskId == tasks.id
    val currentPhase = prefs.getCurrentPhase()
    val isFocusing = currentPhase == "Fokus"

    // Penentuan Warna Kartu (Sedang Fokus / Normal)
    val cardStrokeColor = if (isTaskActiveFocus) {
        if (isFocusing) colorResource(R.color.colorPrimary) else colorResource(android.R.color.holo_orange_dark)
    } else {
        colorResource(com.google.android.material.R.color.m3_sys_color_light_outline_variant)
    }
    val cardStrokeWidth = if (isTaskActiveFocus) 2.dp else 1.dp

    // Penentuan Warna Prioritas
    val priorityText = tasks.priority ?: "Sedang"
    val (priorityTextColorRes, priorityBgColorRes) = when (priorityText.lowercase()) {
        "tinggi", "high", "hight", "urgent" -> Pair(R.color.high_priority, R.color.high_priority_bg)
        "rendah", "low" -> Pair(R.color.low_priority, R.color.low_priority_bg)
        else -> Pair(R.color.medium_priority, R.color.medium_priority_bg)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colorResource(R.color.card_background)),
        border = BorderStroke(cardStrokeWidth, cardStrokeColor),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clickable { onActionClick(tasks, "edit") }
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {

            // 🔴 [PERBAIKAN 2] CHECKBOX LINGKARAN HIJAU MODERN
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (tasks.isCompleted) Color(0xFF4CAF50) else Color.Transparent)
                    .border(2.dp, if (tasks.isCompleted) Color(0xFF4CAF50) else Color.LightGray, CircleShape)
                    .clickable { onActionClick(tasks, if (tasks.isCompleted) "uncomplete" else "complete") },
                contentAlignment = Alignment.Center
            ) {
                if (tasks.isCompleted) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Judul
                Text(
                    text = tasks.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(R.color.text_primary),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Badge Prioritas
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .background(colorResource(priorityBgColorRes), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(text = priorityText.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colorResource(priorityTextColorRes))
                }

                // 🔴 [PERBAIKAN 1] TANGGAL & LOKASI DIPISAH BARIS AGAR TIDAK TERPOTONG
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    val timeDisplay = if (tasks.endTime.isNotEmpty()) "${tasks.time} - ${tasks.endTime}" else tasks.time

                    // Baris 1: Tanggal & Waktu
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_event_upcoming), null, tint = colorResource(R.color.text_secondary), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "${tasks.date.toReadableDate()} • $timeDisplay", fontSize = 12.sp, color = colorResource(R.color.text_secondary))
                    }

                    // Baris 2: Lokasi (Di bawahnya)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(painterResource(R.drawable.ic_location), null, tint = colorResource(R.color.text_secondary), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = tasks.location.takeIf { !it.isNullOrEmpty() } ?: "Tidak ada lokasi", fontSize = 12.sp, color = colorResource(R.color.text_secondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                // Deskripsi
                if (!tasks.description.isNullOrEmpty()) {
                    Text(
                        text = tasks.description,
                        fontSize = 12.sp,
                        color = colorResource(R.color.text_secondary),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // Baris Indikator Fokus & Statistik Pomodoro
                Row(modifier = Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    if (isTaskActiveFocus) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(cardStrokeColor, RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(painterResource(R.drawable.ic_timer), null, tint = Color.White, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isFocusing) "SEDANG FOKUS" else "SEDANG ISTIRAHAT", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    if (item.completedSessionsCount > 0) {
                        val totalMinutes = item.totalFocusMinutes ?: 0
                        val hours = totalMinutes / 60
                        val minutes = totalMinutes % 60
                        val timeFormatted = when {
                            hours > 0 && minutes > 0 -> "$hours Jam $minutes mnt"
                            hours > 0 -> "$hours Jam"
                            else -> "$totalMinutes mnt"
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(colorResource(R.color.card_background), RoundedCornerShape(12.dp))
                                .border(1.dp, colorResource(R.color.colorPrimary), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(painterResource(R.drawable.ic_history), null, tint = colorResource(R.color.colorPrimary), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "${item.completedSessionsCount} Sesi ($timeFormatted)", color = colorResource(R.color.text_secondary), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}