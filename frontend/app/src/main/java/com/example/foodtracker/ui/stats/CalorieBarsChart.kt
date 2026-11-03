package com.example.foodtracker.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.example.foodtracker.stats.DayBar
import com.example.foodtracker.stats.DayBarState
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.util.LocalAppLanguage
import com.example.foodtracker.util.toLocale
import java.time.LocalDate
import java.time.format.TextStyle

/**
 * Calorie bars with a dashed goal tick per bar. [slotDates] are all days of the period
 * (future days stay empty); [fallbackGoalKcal] is drawn for bars without their own goal snapshot.
 */
@Composable
fun CalorieBarsChart(
    bars: List<DayBar>,
    onBarClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    slotDates: List<LocalDate> = bars.map { it.date },
    fallbackGoalKcal: Int? = null,
) {
    val green = GlassColors.accentGreen
    val blue = GlassColors.accentBlue
    val grey = GlassColors.textSecondary.copy(alpha = 0.4f)
    val goalColor = GlassColors.textPrimary.copy(alpha = 0.7f)
    val locale = LocalAppLanguage.current.toLocale()
    val byDate = bars.associateBy { it.date }
    val slots = slotDates.size.coerceAtLeast(1)
    val maxKcal = (bars.maxOfOrNull { maxOf(it.kcal, it.goalKcal ?: fallbackGoalKcal ?: 0) }
        ?: fallbackGoalKcal ?: 0).coerceAtLeast(1)
    val clickHandler by rememberUpdatedState(onBarClick)
    val currentDates by rememberUpdatedState(slotDates)
    val currentBars by rememberUpdatedState(bars)

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        hitTestBar(pos.x, size.width.toFloat(), currentDates, currentBars)?.let { clickHandler(it) }
                    }
                }
        ) {
            val slotW = size.width / slots
            val barW = slotW * 0.6f
            slotDates.forEachIndexed { i, date ->
                val bar = byDate[date] ?: return@forEachIndexed
                val x = i * slotW + (slotW - barW) / 2f
                val goal = bar.goalKcal ?: fallbackGoalKcal
                val color = dayStateColor(bar.state, green, blue, grey)
                if (color != null) {
                    val h = size.height * bar.kcal / maxKcal
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, size.height - h),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                }
                if (goal != null) {
                    val y = size.height - size.height * goal / maxKcal
                    drawLine(
                        color = goalColor,
                        start = Offset(x - 2.dp.toPx(), y),
                        end = Offset(x + barW + 2.dp.toPx(), y),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val month = slots > 7
            slotDates.forEach { d ->
                val label = when {
                    !month -> d.dayOfWeek.getDisplayName(TextStyle.NARROW, locale)
                    d.dayOfMonth == 1 || d.dayOfMonth % 5 == 0 -> d.dayOfMonth.toString()
                    else -> ""
                }
                Text(
                    text = label,
                    fontSize = 10.sp,
                    color = GlassColors.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Maps a tap at [x] (of [width] px) to the tapped day, or null when that day has no bar to open. */
fun hitTestBar(x: Float, width: Float, slotDates: List<LocalDate>, bars: List<DayBar>): LocalDate? {
    if (slotDates.isEmpty() || width <= 0f) return null
    val idx = (x / (width / slotDates.size)).toInt().coerceIn(0, slotDates.size - 1)
    val date = slotDates[idx]
    val state = bars.firstOrNull { it.date == date }?.state ?: return null
    return if (state == DayBarState.MISSING) null else date
}
