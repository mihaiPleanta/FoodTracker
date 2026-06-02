package com.example.foodtracker.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import com.example.foodtracker.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foodtracker.model.FoodItem
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.util.parseGrams
import com.example.foodtracker.util.scaleNutrition

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodSheet(
    target: FoodItem,
    accentColor: Color,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (FoodItem, Int) -> Unit,
    initialGrams: Int = 100,
) {
    var grams by rememberSaveable(target.barcode) { mutableStateOf(initialGrams.toString()) }
    val gramsInt = parseGrams(grams)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GlassColors.cardBackgroundAlt,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(top = 12.dp, bottom = 22.dp)
                .navigationBarsPadding(),
        ) {
            AddFoodSheetGrabber()
            Spacer(Modifier.height(8.dp))
            AddFoodSheetHeader(food = target)
            Spacer(Modifier.height(18.dp))
            AddFoodSheetGramsField(
                grams = grams,
                onGramsChange = { new ->
                    if (new.all { it.isDigit() } && new.length <= 4) grams = new
                },
                accentColor = accentColor,
                isInvalid = grams.isNotEmpty() && gramsInt == null,
            )
            Spacer(Modifier.height(12.dp))
            AddFoodSheetChips(
                grams = grams,
                onChipTap = { value -> grams = value.toString() },
                accentColor = accentColor,
            )
            Spacer(Modifier.height(16.dp))
            AddFoodSheetPreview(
                food = target,
                gramsInt = gramsInt,
                accentColor = accentColor,
            )
            Spacer(Modifier.height(16.dp))
            AddFoodSheetAddButton(
                label = confirmLabel,
                enabled = gramsInt != null,
                accentColor = accentColor,
                onClick = { gramsInt?.let { onConfirm(target, it) } },
            )
        }
    }
}

@Composable
private fun AddFoodSheetGrabber() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(40.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(GlassColors.textTertiary),
        )
    }
}

@Composable
private fun AddFoodSheetHeader(food: FoodItem) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ProductThumbnail(food = food, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = food.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = GlassColors.textPrimary,
                maxLines = 1,
            )
            val subtitlePrefix = if (food.brand.isNullOrBlank()) "" else "${food.brand} · "
            Text(
                text = "${subtitlePrefix}per 100g · ${food.per100g} kcal · P${food.protein100g.toInt()} C${food.carbs100g.toInt()} F${food.fat100g.toInt()}",
                fontSize = 11.sp,
                color = GlassColors.textTertiary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AddFoodSheetGramsField(
    grams: String,
    onGramsChange: (String) -> Unit,
    accentColor: Color,
    isInvalid: Boolean,
) {
    val borderColor = when {
        isInvalid -> Color(0xFFFF6B6B)
        else -> accentColor
    }
    Column {
        Text(
            text = stringResource(R.string.add_food_quantity),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = GlassColors.textSecondary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(GlassColors.backgroundSurface2)
                .border(2.dp, borderColor, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            BasicTextField(
                value = grams,
                onValueChange = onGramsChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    color = GlassColors.textPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
                singleLine = true,
                cursorBrush = SolidColor(accentColor),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                decorationBox = { inner ->
                    if (grams.isEmpty()) {
                        Text(
                            "0",
                            color = GlassColors.textTertiary,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    inner()
                },
            )
            Text(
                text = "g",
                fontSize = 14.sp,
                color = GlassColors.textSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun AddFoodSheetChips(
    grams: String,
    onChipTap: (Int) -> Unit,
    accentColor: Color,
) {
    val values = listOf(50, 100, 150, 200)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            val isActive = grams == value.toString()
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isActive) accentColor.copy(alpha = 0.22f) else Color(0xFF333333)
                    )
                    .border(
                        1.5.dp,
                        if (isActive) accentColor else Color(0xFF454545),
                        RoundedCornerShape(14.dp),
                    )
                    .clickable { onChipTap(value) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Text(
                    text = "${value}g",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isActive) accentColor else GlassColors.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun AddFoodSheetPreview(
    food: FoodItem,
    gramsInt: Int?,
    accentColor: Color,
) {
    val scaled = gramsInt?.let { scaleNutrition(food, it) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GlassColors.backgroundSurface2)
            .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = if (scaled != null) "${scaled.kcal} kcal" else "— kcal",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = accentColor,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniMacroTag(
                text = if (scaled != null) "P ${scaled.protein.toInt()}g" else "P —",
                color = GlassColors.proteinColor,
            )
            MiniMacroTag(
                text = if (scaled != null) "C ${scaled.carbs.toInt()}g" else "C —",
                color = GlassColors.carbsColor,
            )
            MiniMacroTag(
                text = if (scaled != null) "F ${scaled.fat.toInt()}g" else "F —",
                color = GlassColors.fatColor,
            )
        }
    }
}

@Composable
private fun AddFoodSheetAddButton(
    label: String,
    enabled: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    val gradient = if (enabled) {
        Brush.horizontalGradient(listOf(accentColor, accentColor))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF3A3A3A), Color(0xFF3A3A3A)))
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(gradient)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) Color.Black else GlassColors.textSecondary,
        )
    }
}
