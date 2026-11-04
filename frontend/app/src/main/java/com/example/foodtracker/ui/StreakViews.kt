package com.example.foodtracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foodtracker.R
import com.example.foodtracker.model.RecoveryOfferDto
import com.example.foodtracker.model.StreakDayDto
import com.example.foodtracker.model.StreakResponseDto
import com.example.foodtracker.streak.milestoneProgress
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.util.AppLocale
import com.example.foodtracker.util.LocalAppLanguage
import com.example.foodtracker.util.toLocale
import com.example.foodtracker.viewmodel.StreakUiState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

private val CircleSize = 42.dp

/** Dashed circle outline (Compose borders can't be dashed). */
private fun Modifier.dashedCircle(color: Color, width: Dp = 2.dp): Modifier = drawBehind {
    val w = width.toPx()
    val dash = 3.dp.toPx()
    drawCircle(
        color = color,
        radius = (size.minDimension - w) / 2f,
        style = Stroke(
            width = w,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
        ),
    )
}

/** Header circle: 🔥 + current streak. Filled orange once today is logged, dashed grey otherwise. */
@Composable
fun StreakBadge(state: StreakUiState, onClick: () -> Unit) {
    val orange = GlassColors.accentOrange
    val grey = GlassColors.textSecondary
    val bg = GlassColors.cardBackground
    val current: Int?
    val logged: Boolean
    when (state) {
        is StreakUiState.Loaded -> { current = state.data.current; logged = state.data.todayLogged }
        is StreakUiState.Offline -> { current = state.current; logged = state.todayLogged }
        StreakUiState.Loading -> { current = null; logged = false }
    }
    val active = logged && (current ?: 0) > 0
    val cd = stringResource(R.string.streak_cd, current ?: 0)
    Box(
        Modifier
            .size(CircleSize)
            .clip(CircleShape)
            .background(bg)
            .then(
                if (active) Modifier.border(2.dp, orange, CircleShape)
                else Modifier.dashedCircle(grey.copy(alpha = 0.7f))
            )
            .then(if (state is StreakUiState.Loaded) Modifier.clickable(onClick = onClick) else Modifier)
            .clearAndSetSemantics { contentDescription = cd },
        Alignment.Center,
    ) {
        if (current == null) {
            Text("🔥", fontSize = 18.sp)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔥", fontSize = 11.sp, lineHeight = 11.sp)
                Text(
                    current.toString(),
                    fontSize = 13.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (active) orange else grey,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreakSheet(data: StreakResponseDto, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = ContentMaxWidth,
        containerColor = GlassColors.cardBackgroundAlt,
    ) {
        AppLocale(LocalAppLanguage.current) {
            StreakSheetContent(data)
        }
    }
}

@Composable
private fun StreakSheetContent(data: StreakResponseDto) {
    val locale = LocalAppLanguage.current.toLocale()
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(bottom = 22.dp)
            .navigationBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("🔥 ${data.current}", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = GlassColors.accentOrange)
            Column {
                Text(stringResource(R.string.streak_days_in_row), fontSize = 13.sp, color = GlassColors.textSecondary)
                if (!data.todayLogged) {
                    Text(stringResource(R.string.streak_not_logged_today), fontSize = 13.sp, color = GlassColors.textSecondary)
                }
            }
        }

        StreakCard {
            CardLabel(stringResource(R.string.streak_last_14))
            Spacer(Modifier.height(8.dp))
            data.last14Days.chunked(7).forEachIndexed { i, week ->
                if (i > 0) Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    week.forEach { DayCell(it, locale) }
                }
            }
        }

        StreakCard {
            Text(
                stringResource(
                    R.string.streak_next_milestone,
                    (data.nextMilestone - data.current).coerceAtLeast(0),
                    data.nextMilestone,
                ),
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GlassColors.textPrimary,
            )
            Spacer(Modifier.height(8.dp))
            val progress = milestoneProgress(data.current, data.nextMilestone)
            val track = GlassColors.backgroundSurface2
            val from = GlassColors.accentOrange
            val to = GlassColors.accentYellow
            Box(
                Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(track)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .height(8.dp)
                        .background(Brush.horizontalGradient(listOf(from, to)))
                )
            }
        }

        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
            StatCard(
                stringResource(R.string.streak_best),
                stringResource(R.string.streak_days_count, data.best),
                GlassColors.textPrimary, Modifier.weight(1f),
            )
            StatCard(
                stringResource(R.string.streak_total),
                stringResource(R.string.streak_days_count, data.totalLoggedDays),
                GlassColors.textPrimary, Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
            val pauseUsed = data.pauseUsedThisWeek
            StatCard(
                stringResource(R.string.streak_pause_week),
                if (pauseUsed) "⏸ " + stringResource(R.string.streak_used_on, shortDate(data.pauseUsedOn, locale))
                else "⏸ " + stringResource(R.string.streak_available),
                if (pauseUsed) GlassColors.textTertiary else GlassColors.accentGreen,
                Modifier.weight(1f),
            )
            val recoveryFree = data.recoveryAvailableThisMonth
            StatCard(
                stringResource(R.string.streak_recovery_month),
                if (recoveryFree) "↺ " + stringResource(R.string.streak_available)
                else "↺ " + stringResource(R.string.streak_used_on, shortDate(data.recoveryUsedOn, locale)),
                if (recoveryFree) GlassColors.accentGreen else GlassColors.textTertiary,
                Modifier.weight(1f),
            )
        }

        Text(
            stringResource(R.string.streak_rules),
            fontSize = 12.sp, lineHeight = 17.sp, color = GlassColors.textSecondary,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

private fun shortDate(iso: String?, locale: Locale): String {
    val date = iso?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return "—"
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale).format(date)
}

@Composable
private fun StreakCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassColors.cardBackground)
            .border(1.dp, GlassColors.cardBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) { content() }
}

@Composable
private fun CardLabel(text: String) {
    Text(text.uppercase(), fontSize = 12.sp, color = GlassColors.textSecondary, letterSpacing = 0.5.sp)
}

@Composable
private fun StatCard(label: String, value: String, valueColor: Color, modifier: Modifier) {
    StreakCard(modifier) {
        CardLabel(label)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = valueColor, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun DayCell(day: StreakDayDto, locale: Locale) {
    val orange = GlassColors.accentOrange
    val blue = GlassColors.accentBlue
    val grey = GlassColors.textTertiary
    val date = runCatching { LocalDate.parse(day.date) }.getOrNull()
    val letter = date?.dayOfWeek?.getDisplayName(TextStyle.NARROW, locale) ?: ""
    val size = 28.dp
    val base = Modifier.size(size).clip(CircleShape)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (day.state) {
            "logged", "today_logged" -> Box(
                base.background(orange.copy(alpha = 0.18f))
                    .border(
                        if (day.state == "today_logged") 2.dp else 1.dp,
                        if (day.state == "today_logged") orange else orange.copy(alpha = 0.4f),
                        CircleShape,
                    ),
                Alignment.Center,
            ) { Text("🔥", fontSize = 13.sp) }
            "pause" -> Box(
                base.background(blue.copy(alpha = 0.18f)).border(1.dp, blue.copy(alpha = 0.4f), CircleShape),
                Alignment.Center,
            ) { Text("⏸", fontSize = 13.sp) }
            "recovered" -> Box(
                base.background(GlassColors.accentGreen.copy(alpha = 0.18f))
                    .border(1.dp, GlassColors.accentGreen.copy(alpha = 0.4f), CircleShape),
                Alignment.Center,
            ) { Text("↺", fontSize = 13.sp, color = GlassColors.accentGreen) }
            "today_pending" -> Box(Modifier.size(size).dashedCircle(orange), Alignment.Center) {}
            else -> Box(Modifier.size(size).dashedCircle(grey, 1.5.dp), Alignment.Center) {} // missed
        }
        Text(letter, fontSize = 11.sp, color = GlassColors.textSecondary)
    }
}

@Composable
fun StreakRecoveryDialog(
    offer: RecoveryOfferDto,
    onRecover: () -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GlassColors.cardBackgroundAlt,
        shape = RoundedCornerShape(24.dp),
        title = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔥", fontSize = 42.sp)
                Text(
                    stringResource(R.string.streak_broken_title, offer.previousStreak),
                    fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                    color = GlassColors.textPrimary, textAlign = TextAlign.Center,
                )
            }
        },
        text = {
            Text(
                stringResource(R.string.streak_broken_body, offer.previousStreak),
                fontSize = 14.sp, color = GlassColors.textSecondary,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRecover,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GlassColors.accentOrange, contentColor = Color.Black,
                    ),
                ) { Text(stringResource(R.string.streak_recover), fontWeight = FontWeight.Bold) }
                TextButton(
                    onClick = onDecline,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, GlassColors.cardBorder),
                    shape = RoundedCornerShape(14.dp),
                ) { Text(stringResource(R.string.streak_start_over), color = GlassColors.textSecondary) }
            }
        },
    )
}
