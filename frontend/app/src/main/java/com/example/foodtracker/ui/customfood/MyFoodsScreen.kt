package com.example.foodtracker.ui.customfood

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foodtracker.R
import com.example.foodtracker.model.CustomFoodDto
import com.example.foodtracker.ui.ContentMaxWidth
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.ui.theme.glassCard
import com.example.foodtracker.viewmodel.CustomFoodViewModel
import com.example.foodtracker.viewmodel.MyFoodsState

/** "Produsele mele": the user's own foods. Tapping a row opens the edit screen (delete lives there). */
@Composable
fun MyFoodsScreen(
    vm: CustomFoodViewModel,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (Int) -> Unit,
) {
    val state by vm.myFoods.collectAsState()
    LaunchedEffect(Unit) { vm.loadMyFoods() }

    Box(Modifier.fillMaxSize().background(GlassColors.backgroundDark)) {
        Column(
            Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxSize()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(Icons.Rounded.ArrowBackIosNew, stringResource(R.string.action_back), onBack)
                Spacer(Modifier.width(14.dp))
                Text(
                    stringResource(R.string.my_foods_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = GlassColors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                RoundIconButton(Icons.Rounded.Add, stringResource(R.string.my_foods_add), onCreate)
            }
            when (val s = state) {
                is MyFoodsState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = GlassColors.accentGreen)
                }
                is MyFoodsState.Error -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(s.msg),
                            color = GlassColors.textSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        PillButton(stringResource(R.string.action_retry)) { vm.loadMyFoods() }
                    }
                }
                is MyFoodsState.Loaded -> if (s.items.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(R.string.my_foods_empty),
                                color = GlassColors.textSecondary,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(16.dp))
                            PillButton(stringResource(R.string.my_foods_create_first), onClick = onCreate)
                        }
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(s.items, key = { it.id }) { food -> MyFoodRow(food) { onEdit(food.id) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun MyFoodRow(food: CustomFoodDto, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .glassCard(16)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            food.name,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = GlassColors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!food.brand.isNullOrBlank()) {
            Text(food.brand, fontSize = 12.sp, color = GlassColors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.my_foods_kcal_per_100g, food.kcal100g.toInt()),
            fontSize = 13.sp,
            color = GlassColors.textSecondary,
        )
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(GlassColors.cardBackground)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = GlassColors.textSecondary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(GlassColors.accentGreen)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = GlassColors.backgroundDark, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
