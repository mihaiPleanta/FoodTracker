package com.example.foodtracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foodtracker.R
import com.example.foodtracker.model.DayStatus
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.ui.theme.accentCard
import com.example.foodtracker.ui.theme.glassCard

/** Primary action when the day is not complete; "done" chip + undo when it is. */
@Composable
fun DayCompleteButton(
    status: DayStatus?,
    onComplete: () -> Unit,
    onUndo: () -> Unit,
    isPastDay: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (status == DayStatus.COMPLETE) {
        Row(
            modifier = modifier.fillMaxWidth().accentCard(GlassColors.accentGreen)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.day_complete_done), color = GlassColors.accentGreen,
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.day_complete_undo), color = GlassColors.textSecondary,
                fontSize = 14.sp, modifier = Modifier.clickable(onClick = onUndo).padding(8.dp))
        }
    } else {
        val label = if (isPastDay) R.string.day_complete_action_past else R.string.day_complete_action
        Box(
            modifier = modifier.fillMaxWidth().glassCard()
                .clickable(onClick = onComplete).padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(label), color = GlassColors.accentGreen,
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun YesterdayCompletePrompt(
    onYes: () -> Unit,
    onNo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().glassCard().padding(16.dp)) {
        Text(stringResource(R.string.yesterday_prompt_title), color = GlassColors.textPrimary,
            fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.yesterday_prompt_yes), color = GlassColors.accentGreen,
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onYes).padding(horizontal = 8.dp, vertical = 8.dp))
            Text(stringResource(R.string.yesterday_prompt_no), color = GlassColors.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.clickable(onClick = onNo).padding(horizontal = 8.dp, vertical = 8.dp))
        }
    }
}
