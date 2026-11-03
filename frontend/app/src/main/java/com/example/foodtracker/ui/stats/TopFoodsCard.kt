package com.example.foodtracker.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foodtracker.R
import com.example.foodtracker.model.TopFoodsDto
import com.example.foodtracker.ui.ProductThumbnail
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.ui.theme.glassCard
import com.example.foodtracker.util.LocalAppLanguage
import com.example.foodtracker.util.toLocale

@Composable
fun TopFoodsCard(topFoods: TopFoodsDto) {
    var byProtein by rememberSaveable { mutableStateOf(false) }
    val locale = LocalAppLanguage.current.toLocale()
    val resources = LocalContext.current.resources
    val items = (if (byProtein) topFoods.byProtein else topFoods.byKcal).take(5)
    Column(Modifier.fillMaxWidth().glassCard(20).padding(16.dp)) {
        Text(
            stringResource(R.string.stats_top_title),
            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GlassColors.textPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !byProtein, onClick = { byProtein = false },
                label = { Text(stringResource(R.string.stats_top_by_kcal)) },
            )
            FilterChip(
                selected = byProtein, onClick = { byProtein = true },
                label = { Text(stringResource(R.string.stats_top_by_protein)) },
            )
        }
        Spacer(Modifier.height(8.dp))
        items.forEach { food ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                ProductThumbnail(food.toThumbnailItem(), 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        food.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = GlassColors.textPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        resources.getQuantityString(R.plurals.stats_top_days, food.daysCount, food.daysCount),
                        fontSize = 11.sp, color = GlassColors.textSecondary,
                    )
                }
                Text(
                    topFoodTotal(food, byProtein, locale),
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GlassColors.textPrimary,
                )
            }
        }
    }
}
