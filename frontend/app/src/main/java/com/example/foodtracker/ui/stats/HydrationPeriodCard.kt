package com.example.foodtracker.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foodtracker.R
import com.example.foodtracker.stats.PeriodSummary
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.ui.theme.glassCard
import com.example.foodtracker.util.LocalAppLanguage
import com.example.foodtracker.util.toLocale

@Composable
fun HydrationPeriodCard(summary: PeriodSummary) {
    val avg = summary.avgHydrationL ?: return
    val locale = LocalAppLanguage.current.toLocale()
    Column(Modifier.fillMaxWidth().glassCard(20).padding(16.dp)) {
        Text(
            stringResource(R.string.stats_hydration_title),
            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GlassColors.textPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.stats_hydration_avg, formatLitres(avg, locale)),
            fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = GlassColors.accentBlue,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.stats_hydration_goal_days, summary.hydrationGoalDays, summary.completeDays),
            fontSize = 13.sp, color = GlassColors.textSecondary,
        )
    }
}
