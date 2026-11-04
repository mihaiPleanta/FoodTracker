package com.example.foodtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foodtracker.R
import com.example.foodtracker.ui.theme.GlassColors

internal val DeleteRed = Color(0xFFFF6B6B)

/** Red "Delete" text button shown under the Save button of the edit sheets on Home. */
@Composable
fun DeleteEntryButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.fillMaxWidth().padding(top = 6.dp)) {
        Icon(Icons.Outlined.Delete, contentDescription = null, tint = DeleteRed, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.action_delete), color = DeleteRed, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

/**
 * Swipe right-to-left to delete.
 * The row is removed by the caller's state update; returning true lets the box settle dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteRow(
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete(); true
            } else false
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            // Rows are translucent: draw the red band only while actually swiping, or it shows through.
            if (state.dismissDirection != SwipeToDismissBoxValue.EndToStart) return@SwipeToDismissBox
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(DeleteRed.copy(alpha = 0.85f))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.action_delete), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        },
    ) {
        val swiping = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
        val underlay = GlassColors.cardBackground
        Box(
            if (swiping) Modifier.clip(RoundedCornerShape(10.dp)).background(underlay) else Modifier
        ) { content() }
    }
}
