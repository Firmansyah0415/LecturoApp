package com.lecturo.lecturo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lecturo.lecturo.R
import com.lecturo.lecturo.data.model.CalendarEntry
import com.lecturo.lecturo.utils.EventCategories
import kotlin.math.cos
import kotlin.math.sin

// ENUM UNTUK FILTER
enum class ChartFilter { FULL_24H, AM_ONLY, PM_ONLY }

@OptIn(ExperimentalTextApi::class)
@Composable
fun DetailedDonutChartScreen(agendaList: List<CalendarEntry>) {
    var selectedFilter by remember { mutableStateOf(ChartFilter.FULL_24H) }
    val textMeasurer = rememberTextMeasurer()

    // 1. Resolve Warna di LUAR Canvas
    val amAgendas = agendaList.filter { isAM(it.time) }.map {
        Pair(it, getColorForFeature(it.sourceFeatureType, it.category))
    }
    val pmAgendas = agendaList.filter { !isAM(it.time) }.map {
        Pair(it, getColorForFeature(it.sourceFeatureType, it.category))
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colorResource(id = R.color.card_background)),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- TOMBOL FILTER ---
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                SegmentedButton(
                    selected = selectedFilter == ChartFilter.FULL_24H,
                    onClick = { selectedFilter = ChartFilter.FULL_24H },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                ) { Text("24 Jam") }
                SegmentedButton(
                    selected = selectedFilter == ChartFilter.AM_ONLY,
                    onClick = { selectedFilter = ChartFilter.AM_ONLY },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                ) { Text("Pagi (AM)") }
                SegmentedButton(
                    selected = selectedFilter == ChartFilter.PM_ONLY,
                    onClick = { selectedFilter = ChartFilter.PM_ONLY },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                ) { Text("Malam (PM)") }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- AREA CANVAS ---
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(280.dp)) {

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val isSingleRing = selectedFilter != ChartFilter.FULL_24H
                    val strokeWidth = if (isSingleRing) 40.dp.toPx() else 24.dp.toPx()
                    val center = Offset(size.width / 2, size.height / 2)

                    val strokeStyle = Stroke(width = strokeWidth, cap = StrokeCap.Butt)

                    // PENGATURAN UKURAN CINCIN
                    val outerSize = size
                    val innerSize = Size(size.width - strokeWidth * 2.2f, size.height - strokeWidth * 2.2f)
                    val innerOffset = Offset(strokeWidth * 1.1f, strokeWidth * 1.1f)

                    // --- MENGGAMBAR CINCIN DASAR ---
                    if (selectedFilter == ChartFilter.FULL_24H || selectedFilter == ChartFilter.AM_ONLY) {
                        drawArc(Color.LightGray.copy(alpha = 0.2f), 0f, 360f, false, style = strokeStyle)
                    }
                    if (selectedFilter == ChartFilter.FULL_24H) {
                        drawArc(Color.LightGray.copy(alpha = 0.2f), 0f, 360f, false, style = strokeStyle, size = innerSize, topLeft = innerOffset)
                    } else if (selectedFilter == ChartFilter.PM_ONLY) {
                        drawArc(Color.LightGray.copy(alpha = 0.2f), 0f, 360f, false, style = strokeStyle)
                    }

                    // --- MENGGAMBAR DATA JADWAL ---
                    if (selectedFilter == ChartFilter.FULL_24H || selectedFilter == ChartFilter.AM_ONLY) {
                        amAgendas.forEach { (agenda, resolvedColor) ->
                            val startAngle = calculateAngle(agenda.time)
                            val sweepAngle = if (agenda.endTime.isNotEmpty()) calculateSweep(agenda.time, agenda.endTime) else 30f
                            drawArc(color = resolvedColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = strokeStyle)
                        }
                    }

                    if (selectedFilter == ChartFilter.FULL_24H) {
                        pmAgendas.forEach { (agenda, resolvedColor) ->
                            val startAngle = calculateAngle(agenda.time)
                            val sweepAngle = if (agenda.endTime.isNotEmpty()) calculateSweep(agenda.time, agenda.endTime) else 30f
                            drawArc(color = resolvedColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = strokeStyle, size = innerSize, topLeft = innerOffset)
                        }
                    } else if (selectedFilter == ChartFilter.PM_ONLY) {
                        pmAgendas.forEach { (agenda, resolvedColor) ->
                            val startAngle = calculateAngle(agenda.time)
                            val sweepAngle = if (agenda.endTime.isNotEmpty()) calculateSweep(agenda.time, agenda.endTime) else 30f
                            drawArc(color = resolvedColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = strokeStyle)
                        }
                    }

                    // --- MENGGAMBAR ANGKA JAM DAN TITIK MENIT ---
                    val outerRadius = size.width / 2
                    val radiusForTextOuter = outerRadius + 22.dp.toPx() // Angka ditaruh di luar cincin
                    // Angka dalam ditaruh di sisi dalam dari cincin dalam
                    val radiusForTextInner = outerRadius - (strokeWidth * 2.2f) - 14.dp.toPx()

                    for (hour in 1..12) {
                        val hourAngle = (hour * 30f) - 90f // Posisi jam 12 di atas (-90 deg)
                        val angleRad = Math.toRadians(hourAngle.toDouble())

                        // Logika Format Jam (AM vs PM)
                        val amText = hour.toString()
                        val pmText = if (hour == 12) "00" else (hour + 12).toString()

                        // Fungsi Helper Internal untuk menggambar Teks Jam
                        fun drawHourText(text: String, radius: Float, fontSizeSp: Int) {
                            val textLayoutResult = textMeasurer.measure(
                                text = text,
                                style = TextStyle(fontSize = fontSizeSp.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            )
                            val textX = center.x + radius * cos(angleRad).toFloat() - (textLayoutResult.size.width / 2)
                            val textY = center.y + radius * sin(angleRad).toFloat() - (textLayoutResult.size.height / 2)
                            drawText(textLayoutResult, topLeft = Offset(textX, textY))
                        }

                        // Penempatan Teks berdasarkan Filter
                        if (selectedFilter == ChartFilter.AM_ONLY) {
                            drawHourText(amText, radiusForTextOuter, 12)
                        } else if (selectedFilter == ChartFilter.PM_ONLY) {
                            drawHourText(pmText, radiusForTextOuter, 12) // PM Only pakai format 13-00 di luar
                        } else {
                            // FULL_24H: Gambar dua-duanya (AM di luar, PM di dalam)
                            drawHourText(amText, radiusForTextOuter, 12)
                            drawHourText(pmText, radiusForTextInner, 10) // Ukuran font lebih kecil agar rapi
                        }

                        // Gambar Garis Pendek untuk penanda Menit 30 (Setengah Jam) di bagian LUAR saja
                        val halfHourAngle = hourAngle + 15f
                        val halfRad = Math.toRadians(halfHourAngle.toDouble())

                        val startLineX = center.x + (outerRadius + 2.dp.toPx()) * cos(halfRad).toFloat()
                        val startLineY = center.y + (outerRadius + 2.dp.toPx()) * sin(halfRad).toFloat()
                        val endLineX = center.x + (outerRadius + 8.dp.toPx()) * cos(halfRad).toFloat()
                        val endLineY = center.y + (outerRadius + 8.dp.toPx()) * sin(halfRad).toFloat()

                        drawLine(
                            color = Color.LightGray.copy(alpha = 0.5f),
                            start = Offset(startLineX, startLineY),
                            end = Offset(endLineX, endLineY),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            ChartLegendRow()
        }
    }
}

// Helper Compose: Menentukan Warna berdasarkan kategori
@Composable
fun getColorForFeature(featureType: String, category: String?): Color {
    val cat = category?.lowercase() ?: ""
    return when {
        featureType == "TEACHING_SCHEDULE" -> colorResource(R.color.teaching_color)
        featureType == "TASK" -> colorResource(R.color.task_color)
        featureType == "CONSULTATION" || cat == "konsultasi" -> colorResource(R.color.consultation_color)
        featureType == "EVENT" || EventCategories.list.contains(cat) -> colorResource(R.color.event_color)
        else -> Color.Gray
    }
}

@Composable
fun ChartLegendRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendItem("Mengajar", R.color.teaching_color)
        LegendItem("Tugas", R.color.task_color)
        LegendItem("Acara", R.color.event_color)
        LegendItem("Konsultasi", R.color.consultation_color)
    }
}

@Composable
fun LegendItem(title: String, colorRes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(colorResource(colorRes), CircleShape))
        Spacer(modifier = Modifier.width(4.dp))
        Text(title, fontSize = 10.sp, color = colorResource(R.color.text_secondary))
    }
}

// --- HELPER LOGIKA WAKTU MATEMATIKA ---
private fun calculateAngle(timeStr: String): Float {
    try {
        val parts = timeStr.split(":")
        var h = parts[0].toInt()
        val m = parts[1].toInt()
        if (h >= 12) h -= 12

        val baseAngle = -90f
        val hourProgress = (h * 30f) + (m * 0.5f)
        return baseAngle + hourProgress
    } catch (e: Exception) { return -90f }
}

private fun calculateSweep(startStr: String, endStr: String): Float {
    try {
        val sParts = startStr.split(":")
        val eParts = endStr.split(":")
        val sMinutes = (sParts[0].toInt() * 60) + sParts[1].toInt()
        val eMinutes = (eParts[0].toInt() * 60) + eParts[1].toInt()

        var diff = eMinutes - sMinutes
        if (diff < 0) diff += 1440
        return diff * 0.5f
    } catch (e: Exception) { return 30f }
}

private fun isAM(timeStr: String): Boolean {
    try {
        val h = timeStr.split(":")[0].toInt()
        return h < 12
    } catch (e: Exception) { return true }
}