package com.example.foodtracker.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foodtracker.R
import com.example.foodtracker.stats.StatsPeriod
import com.example.foodtracker.stats.TrendPoint
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.ui.theme.glassCard
import com.example.foodtracker.util.LocalAppLanguage
import com.example.foodtracker.util.toLocale
import java.time.temporal.ChronoUnit

/** Raw weigh-ins as dots plus the smoothed trend line; [points] are already limited to the period. */
@Composable
fun WeightTrendCard(period: StatsPeriod, points: List<TrendPoint>) {
    val locale = LocalAppLanguage.current.toLocale()
    val dot = GlassColors.textSecondary
    val line = GlassColors.accentGreen
    Column(Modifier.fillMaxWidth().glassCard(20).padding(16.dp)) {
        Text(
            stringResource(R.string.stats_weight_title),
            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GlassColors.textPrimary,
        )
        Spacer(Modifier.height(10.dp))
        if (points.isEmpty()) {
            Text(stringResource(R.string.stats_weight_no_data), fontSize = 13.sp, color = GlassColors.textSecondary)
            return@Column
        }
        val allKg = points.flatMap { listOf(it.weightKg, it.trendKg) }
        val minKg = allKg.min() - 0.5f
        val maxKg = allKg.max() + 0.5f
        val span = (ChronoUnit.DAYS.between(period.start, period.end).toInt()).coerceAtLeast(1)
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val pad = 6.dp.toPx()
            fun x(p: TrendPoint) =
                pad + (size.width - 2 * pad) * ChronoUnit.DAYS.between(period.start, p.date).toFloat() / span
            fun y(kg: Float) = size.height - pad - (size.height - 2 * pad) * (kg - minKg) / (maxKg - minKg)
            if (points.size > 1) {
                val path = Path()
                points.forEachIndexed { i, p ->
                    if (i == 0) path.moveTo(x(p), y(p.trendKg)) else path.lineTo(x(p), y(p.trendKg))
                }
                drawPath(path, line, style = Stroke(width = 2.5.dp.toPx()))
            }
            points.forEach { p -> drawCircle(dot, 3.dp.toPx(), Offset(x(p), y(p.weightKg))) }
            drawCircle(line, 4.dp.toPx(), Offset(x(points.last()), y(points.last().trendKg)))
        }
        Spacer(Modifier.height(6.dp))
        val last = points.last()
        Text(
            stringResource(
                R.string.stats_weight_latest,
                String.format(locale, "%.1f", last.weightKg), String.format(locale, "%.1f", last.trendKg),
            ),
            fontSize = 12.sp, color = GlassColors.textSecondary,
        )
    }
}
