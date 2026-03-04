package com.example.foodtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foodtracker.ui.theme.GlassColors

@Composable
fun PlaceholderScreen(title: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(GlassColors.backgroundDark),
        Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = GlassColors.textPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Coming soon",
                fontSize = 14.sp,
                color = GlassColors.textTertiary
            )
        }
    }
}


