package com.example.foodtracker.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foodtracker.R
import com.example.foodtracker.ui.EmptyStateCard
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.ui.theme.glassCard
import com.example.foodtracker.util.LocalAppLanguage
import com.example.foodtracker.util.toLocale
import com.example.foodtracker.viewmodel.PeriodUiState
import java.time.LocalDate

@Composable
fun PeriodStatsContent(
    state: PeriodUiState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    fallbackGoalKcal: Int? = null,
) {
    when (state) {
        PeriodUiState.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is PeriodUiState.Error -> Column(
            Modifier.fillMaxWidth().glassCard(20).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(state.message), fontSize = 13.sp, color = GlassColors.textSecondary, textAlign = TextAlign.Center)
            Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
        is PeriodUiState.Loaded -> Loaded(state, onPrev, onNext, onOpenDay, fallbackGoalKcal)
    }
}

@Composable
private fun Loaded(
    state: PeriodUiState.Loaded,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    fallbackGoalKcal: Int?,
) {
    val locale = LocalAppLanguage.current.toLocale()
    val s = state.summary
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.stats_prev_period), tint = GlassColors.textPrimary)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    periodLabel(state.period, locale),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GlassColors.textPrimary,
                )
                Text(
                    stringResource(R.string.stats_complete_days, s.completeDays, s.elapsedDays),
                    fontSize = 12.sp, color = GlassColors.textSecondary,
                )
            }
            IconButton(onClick = onNext, enabled = state.canGoNext) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.stats_next_period),
                    tint = GlassColors.textPrimary.copy(alpha = if (state.canGoNext) 1f else 0.3f),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        PeriodKpiRow(s, state.deltas, state.period.kind)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().glassCard(20).padding(16.dp)) {
            Text(
                stringResource(R.string.stats_bars_title),
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GlassColors.textPrimary,
            )
            Spacer(Modifier.height(10.dp))
            CalorieBarsChart(
                bars = s.bars,
                onBarClick = onOpenDay,
                slotDates = state.period.days(),
                fallbackGoalKcal = fallbackGoalKcal,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (s.completeDays == 0) {
            EmptyStateCard(
                emoji = "📅",
                title = stringResource(R.string.stats_period_empty_title),
                subtitle = stringResource(R.string.stats_period_empty_subtitle),
            )
        } else {
            s.macros?.let { PeriodMacroCard(it) }
            Spacer(Modifier.height(12.dp))
            PeriodBalanceCard(s)
        }
    }
}
