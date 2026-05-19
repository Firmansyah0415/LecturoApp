package com.lecturo.lecturo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BottomSheetItemRow(
    iconRes: Int,
    title: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    backgroundColor: Color = color.copy(alpha = 0.12f),
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).background(backgroundColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(iconRes), null, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        // Teks otomatis mengikuti warna ikon (colorOnSurface)
        Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
    }
}