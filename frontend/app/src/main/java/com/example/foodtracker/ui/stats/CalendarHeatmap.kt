package com.example.foodtracker.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foodtracker.R
import com.example.foodtracker.stats.DayBar
import com.example.foodtracker.stats.PeriodKind
import com.example.foodtracker.stats.StatsPeriod
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.ui.theme.glassCard
import com.example.foodtracker.util.LocalAppLanguage
import com.example.foodtracker.util.toLocale
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

/** Month grid, Monday first; future days are blank, colours match the calorie bars. MONTH only. */
@Composable
fun CalendarHeatmap(
    period: StatsPeriod,
    bars: List<DayBar>,
    today: LocalDate,
    onDayClick: (LocalDate) -> Unit,
) {
    if (period.kind != PeriodKind.MONTH) return
    val locale = LocalAppLanguage.current.toLocale()
    val green = GlassColors.accentGreen
    val blue = GlassColors.accentBlue
    val grey = GlassColors.textSecondary.copy(alpha = 0.4f)
    val faint = GlassColors.textSecondary.copy(alpha = 0.25f)
    val byDate = bars.associateBy { it.date }
    Column(Modifier.fillMaxWidth().glassCard(20).padding(16.dp)) {
        Text(
            stringResource(R.string.stats_calendar_title),
            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GlassColors.textPrimary,
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.values().forEach { d ->
                Text(
                    d.getDisplayName(TextStyle.NARROW, locale),
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    fontSize = 10.sp, color = GlassColors.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        calendarCells(period).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    val cell = Modifier.weight(1f).padding(2.dp).aspectRatio(1f)
                    if (date == null || date.isAfter(today)) {
                        Box(cell)
                        return@forEach
                    }
                    val bar = byDate[date]
                    val color = bar?.let { dayStateColor(it.state, green, blue, grey) }
                    val shape = RoundedCornerShape(8.dp)
                    Box(
                        cell.let { m ->
                            if (color != null) m.background(color, shape).clickable { onDayClick(date) }
                            else m.border(1.dp, faint, shape)
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            date.dayOfMonth.toString(), fontSize = 11.sp,
                            color = if (color != null) GlassColors.textPrimary else GlassColors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}
