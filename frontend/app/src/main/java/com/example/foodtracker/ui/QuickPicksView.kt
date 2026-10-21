package com.example.foodtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foodtracker.R
import com.example.foodtracker.model.FoodItem
import com.example.foodtracker.model.RecentFood
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.viewmodel.QuickListState

private const val DEFAULT_GRAMS = 100

/** "Recent | Favorites" tabs shown while the search bar is focused and empty. */
@Composable
fun QuickPicksView(
    recent: QuickListState<RecentFood>,
    favorites: QuickListState<FoodItem>,
    favoriteBarcodes: Set<String>,
    accentColor: Color,
    onPick: (FoodItem, Int) -> Unit,
    onToggleFavorite: (FoodItem) -> Unit,
    onRetry: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        SecondaryTabRow(
            selectedTabIndex = tab,
            containerColor = Color.Transparent,
            contentColor = accentColor,
            indicator = {
                TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tab), color = accentColor)
            },
            divider = {},
            modifier = Modifier.padding(horizontal = 20.dp),
        ) {
            listOf(R.string.quick_tab_recent, R.string.quick_tab_favorites).forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = {
                        Text(
                            stringResource(label),
                            fontWeight = if (tab == index) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (tab == index) accentColor else GlassColors.textSecondary,
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (tab == 0) {
            QuickList(recent, accentColor, R.string.quick_recent_empty, onRetry) { items ->
                items(items, key = { it.food.barcode }) { item ->
                    SearchResultRow(
                        food = item.food,
                        onAdd = { onPick(item.food, item.lastGrams) },
                        subtitleExtra = stringResource(R.string.quick_recent_last_grams, item.lastGrams),
                        isFavorite = item.food.barcode in favoriteBarcodes,
                        onToggleFavorite = { onToggleFavorite(item.food) },
                    )
                }
            }
        } else {
            QuickList(favorites, accentColor, R.string.quick_favorites_empty, onRetry) { items ->
                items(items, key = { it.barcode }) { food ->
                    SearchResultRow(
                        food = food,
                        onAdd = { onPick(food, DEFAULT_GRAMS) },
                        isFavorite = true,
                        onToggleFavorite = { onToggleFavorite(food) },
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> QuickList(
    state: QuickListState<T>,
    accentColor: Color,
    emptyRes: Int,
    onRetry: () -> Unit,
    content: LazyListScope.(List<T>) -> Unit,
) {
    when (state) {
        QuickListState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = accentColor)
        }
        is QuickListState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(state.messageRes), color = Color(0xFFFF6B6B), fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.18f))
                        .clickable { onRetry() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.action_retry), color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        is QuickListState.Loaded -> if (state.items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(horizontal = 32.dp), Alignment.Center) {
                Text(
                    stringResource(emptyRes),
                    color = GlassColors.textTertiary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                content(state.items)
            }
        }
    }
}
