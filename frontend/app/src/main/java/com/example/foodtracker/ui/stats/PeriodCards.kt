package com.example.foodtracker.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foodtracker.R
import com.example.foodtracker.stats.MacroAverages
import com.example.foodtracker.stats.PeriodDeltas
import com.example.foodtracker.stats.PeriodKind
import com.example.foodtracker.stats.PeriodSummary
import com.example.foodtracker.ui.MacroLine
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.ui.theme.glassCard
import com.example.foodtracker.util.LocalAppLanguage
import com.example.foodtracker.util.toLocale
import kotlin.math.roundToInt

@Composable
fun PeriodKpiRow(summary: PeriodSummary, deltas: PeriodDeltas, kind: PeriodKind) {
    val vsRes = if (kind == PeriodKind.WEEK) R.string.stats_delta_vs_prev_week else R.string.stats_delta_vs_prev_month
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PeriodKpiCard(
            title = stringResource(R.string.stats_kpi_avg_kcal),
            value = summary.avgKcal?.toString() ?: "–",
            delta = deltas.avgKcal?.let {
                stringResource(vsRes, stringResource(R.string.stats_delta_kcal, signedInt(it)))
            },
            accent = GlassColors.accentGreen,
            modifier = Modifier.weight(1f),
        )
        PeriodKpiCard(
            title = stringResource(R.string.stats_kpi_in_target),
            value = if (summary.completeDays > 0)
                stringResource(R.string.stats_in_target_value, summary.inTargetDays, summary.completeDays) else "–",
            delta = deltas.inTargetPctPoints?.let {
                stringResource(vsRes, stringResource(R.string.stats_delta_pp, signedInt(it)))
            },
            accent = GlassColors.accentBlue,
            modifier = Modifier.weight(1f),
        )
        PeriodKpiCard(
            title = stringResource(R.string.stats_kpi_balance),
            value = summary.balanceVsPlanKcal?.let { signedInt(it) } ?: "–",
            delta = deltas.balancePerDayKcal?.let {
                stringResource(vsRes, stringResource(R.string.stats_delta_kcal_day, signedInt(it)))
            },
            accent = GlassColors.accentOrange,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PeriodKpiCard(title: String, value: String, delta: String?, accent: Color, modifier: Modifier) {
    Column(modifier.glassCard(16).padding(12.dp)) {
        Text(title, fontSize = 11.sp, color = GlassColors.textSecondary)
        Spacer(Modifier.height(6.dp))
        Text(value, fontSize = 18.sp, color = GlassColors.textPrimary, fontWeight = FontWeight.ExtraBold)
        if (delta != null) {
            Spacer(Modifier.height(3.dp))
            Text(delta, fontSize = 10.sp, color = accent, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun PeriodMacroCard(macros: MacroAverages) {
    Column(Modifier.fillMaxWidth().glassCard(20).padding(16.dp)) {
        Text(
            stringResource(R.string.stats_macro_avg_title),
            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GlassColors.textPrimary,
        )
        Spacer(Modifier.height(10.dp))
        MacroLine(stringResource(R.string.stats_protein), macros.proteinG.roundToInt(), macros.proteinGoalG.roundToInt(), GlassColors.proteinColor)
        Spacer(Modifier.height(10.dp))
        MacroLine(stringResource(R.string.stats_carbs), macros.carbsG.roundToInt(), macros.carbsGoalG.roundToInt(), GlassColors.carbsColor)
        Spacer(Modifier.height(10.dp))
        MacroLine(stringResource(R.string.stats_fat), macros.fatG.roundToInt(), macros.fatGoalG.roundToInt(), GlassColors.fatColor)
    }
}

@Composable
fun PeriodBalanceCard(summary: PeriodSummary, weightTrendKg: Float? = null) {
    val balance = summary.balanceVsPlanKcal ?: return
    val locale = LocalAppLanguage.current.toLocale()
    Column(Modifier.fillMaxWidth().glassCard(20).padding(16.dp)) {
        Text(
            stringResource(R.string.stats_balance_title),
            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GlassColors.textPrimary,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(
                if (balance >= 0) R.string.stats_balance_over_plan else R.string.stats_balance_under_plan,
                signedInt(balance),
            ),
            fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = GlassColors.textPrimary,
        )
        val estimate = summary.estimatedKgChange?.let {
            stringResource(R.string.stats_balance_estimated_kg, signedKg(it, locale))
        }
        val trend = weightTrendKg?.let { stringResource(R.string.stats_balance_weight_trend, signedKg(it, locale)) }
        val line = listOfNotNull(estimate, trend).joinToString(" ")
        if (line.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(text = line, fontSize = 13.sp, color = GlassColors.textSecondary)
        }
    }
}
