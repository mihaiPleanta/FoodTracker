package com.example.foodtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import com.example.foodtracker.R
import com.example.foodtracker.util.AppLocale
import com.example.foodtracker.util.LocalAppLanguage
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalConfiguration
import com.example.foodtracker.model.PortionUnit
import com.example.foodtracker.model.UnitSource
import com.example.foodtracker.util.PortionChoice
import com.example.foodtracker.util.gramsFor
import com.example.foodtracker.util.mergeLoggedUnit
import com.example.foodtracker.util.maxQtyFor
import com.example.foodtracker.util.parseQty
import com.example.foodtracker.util.stepFor
import com.example.foodtracker.util.stepQty
import com.example.foodtracker.util.unitPluralRes
import com.example.foodtracker.viewmodel.PortionsState
import com.example.foodtracker.viewmodel.PortionsViewModel
import java.text.DecimalFormatSymbols

private val DEFAULT_GRAM_PRESETS = listOf(50, 100, 150, 200, 250)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodSheet(
    target: FoodItem,
    accentColor: Color,
    confirmLabel: String,
    portions: PortionsViewModel,
    onDismiss: () -> Unit,
    onConfirm: (FoodItem, PortionChoice) -> Unit,
    initial: PortionChoice? = null,      // edit: the logged portion; null => last use or fallback
    fallbackGrams: Int = 100,            // Recents: lastGrams; search: 100
    isFavorite: Boolean? = null,          // null => no star (edit flows)
    onToggleFavorite: () -> Unit = {},
) {
    val barcode = target.barcode
    val decimalSep = DecimalFormatSymbols.getInstance(LocalConfiguration.current.locales[0]).decimalSeparator

    // Only data loaded after our own load() call, for this barcode, counts (the VM is shared).
    var requested by remember(barcode) { mutableStateOf(false) }
    LaunchedEffect(barcode) {
        portions.load(barcode)
        requested = true
    }
    val portionsState by portions.state.collectAsState()
    val loaded = (portionsState as? PortionsState.Loaded)
        ?.takeIf { requested && it.barcode == barcode }?.portions

    val initialUnit = initial?.unit?.takeIf { (initial.unitQty ?: 0f) > 0f }
    var selectedUnit by rememberSaveable(barcode) { mutableStateOf(initialUnit) }
    var qtyText by rememberSaveable(barcode) {
        mutableStateOf(initial?.unitQty?.takeIf { initialUnit != null }?.let { qtyInputText(it, decimalSep) } ?: "1")
    }
    var gramsText by rememberSaveable(barcode) { mutableStateOf((initial?.grams ?: fallbackGrams).toString()) }
    // Once settled (initial given, user touched something, or last use applied), loads don't move the selection.
    var settled by rememberSaveable(barcode) { mutableStateOf(initial != null) }
    // Editing: the logged unit keeps its logged grams/unit until the user corrects that unit.
    var keepLoggedRate by rememberSaveable(barcode) { mutableStateOf(true) }

    LaunchedEffect(loaded) {
        if (loaded == null || settled) return@LaunchedEffect
        settled = true
        val last = loaded.last ?: return@LaunchedEffect
        if (last.unit != null && last.unitQty != null && loaded.units.any { it.unit == last.unit }) {
            selectedUnit = last.unit
            qtyText = qtyInputText(last.unitQty, decimalSep)
        } else {
            selectedUnit = null
            gramsText = last.grams.toString()
        }
    }

    val noLogged = PortionChoice(0, null, null)
    val units = mergeLoggedUnit(loaded?.units ?: emptyList(), initial ?: noLogged)
    val loggedRate = initial?.unitQty?.takeIf { it > 0f }?.let { initial.grams / it }
    val rate: Float? = selectedUnit?.let { u ->
        if (keepLoggedRate && u == initialUnit && loggedRate != null) loggedRate
        else units.firstOrNull { it.unit == u }?.grams
    }
    val unitMode = selectedUnit != null && rate != null
    val qty = parseQty(qtyText, selectedUnit)
    val gramsInt = parseGrams(gramsText)
    val choice: PortionChoice? = if (unitMode) {
        qty?.let { q -> PortionChoice(gramsFor(q, rate!!), selectedUnit, q) }
    } else {
        gramsInt?.let { PortionChoice(it, null, null) }
    }
    val labelCount = if (unitMode && qty != null && qty > 1f) 2 else 1

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var saving by remember(barcode) { mutableStateOf<PortionChoice?>(null) }
    LaunchedEffect(saving) {
        saving?.let { c ->
            kotlinx.coroutines.delay(600)
            onConfirm(target, c)
        }
    }

    var editingUnit by remember(barcode) { mutableStateOf<PortionUnit?>(null) }
    var showNewUnit by remember(barcode) { mutableStateOf(false) }
    var showPresetsEdit by remember(barcode) { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = ContentMaxWidth,
        containerColor = GlassColors.cardBackgroundAlt,
        dragHandle = null,
    ) {
      AppLocale(LocalAppLanguage.current) {
        // The confirm button sits outside the scrolling part, so it stays pinned at the bottom of the
        // (bottom-anchored) sheet: units arriving 0.5-2 s after opening grow the sheet upwards
        // instead of pushing the button down under a quick tap.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(top = 12.dp, bottom = 22.dp)
                .navigationBarsPadding(),
        ) {
          Column(
              modifier = Modifier
                  .fillMaxWidth()
                  .weight(1f, fill = false)
                  .verticalScroll(rememberScrollState()),
          ) {
            AddFoodSheetGrabber()
            Spacer(Modifier.height(8.dp))
            AddFoodSheetHeader(
                food = target,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                accentColor = accentColor,
            )
            Spacer(Modifier.height(18.dp))
            if (unitMode) {
                PortionQuantityStepper(
                    qtyText = qtyText,
                    onQtyTextChange = { settled = true; qtyText = it },
                    onStep = { delta ->
                        settled = true
                        val next = stepQty(qty ?: 0f, delta, floor = stepFor(selectedUnit))
                            .coerceAtMost(maxQtyFor(selectedUnit))
                        qtyText = qtyInputText(next, decimalSep)
                    },
                    accentColor = accentColor,
                    isInvalid = qty == null,
                    step = stepFor(selectedUnit),
                )
            } else {
                AddFoodSheetGramsField(
                    grams = gramsText,
                    onGramsChange = { new ->
                        if (new.all { it.isDigit() } && new.length <= 4) {
                            settled = true
                            gramsText = new
                        }
                    },
                    accentColor = accentColor,
                    isInvalid = gramsText.isNotEmpty() && gramsInt == null,
                )
                Spacer(Modifier.height(12.dp))
                GramPresetsRow(
                    presets = loaded?.gramPresets?.takeIf { it.isNotEmpty() } ?: DEFAULT_GRAM_PRESETS,
                    gramsText = gramsText,
                    accentColor = accentColor,
                    onTap = { value -> settled = true; gramsText = value.toString() },
                    onEdit = if (loaded != null) ({ showPresetsEdit = true }) else null,
                )
            }
            if (loaded != null || units.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                PortionUnitList(
                    units = units,
                    selectedUnit = if (unitMode) selectedUnit else null,
                    labelCount = labelCount,
                    accentColor = accentColor,
                    onSelect = { unit ->
                        settled = true
                        if (unit == null) {
                            if (unitMode) choice?.let { gramsText = it.grams.toString() }
                        } else if (!unitMode && qty == null) {
                            qtyText = "1"
                        }
                        selectedUnit = unit
                    },
                    onEdit = if (loaded != null) ({ u -> editingUnit = u }) else null,
                    onAddNew = if (loaded != null) ({ showNewUnit = true }) else null,
                )
            }
            Spacer(Modifier.height(16.dp))
            AddFoodSheetPreview(
                food = target,
                gramsInt = choice?.grams,
                accentColor = accentColor,
                showGrams = unitMode,
            )
          }
            Spacer(Modifier.height(16.dp))
            AnimatedActionButton(
                label = confirmLabel,
                successLabel = stringResource(R.string.action_saved),
                phase = if (saving != null) ActionPhase.Success else ActionPhase.Idle,
                enabled = choice != null,
                accent = Brush.horizontalGradient(listOf(accentColor, accentColor)),
                height = 54.dp,
                modifier = Modifier.fillMaxWidth(),
                onClick = { if (saving == null) choice?.let { saving = it } },
            )
        }

        editingUnit?.let { u ->
            val existing = loaded?.units?.firstOrNull { it.unit == u.unit }
            UnitEditDialog(
                unit = u,
                canDelete = existing?.source == UnitSource.USER,
                accentColor = accentColor,
                onDismiss = { editingUnit = null },
                onSave = { grams ->
                    portions.saveUnit(barcode, u.unit, grams)
                    if (u.unit == initialUnit) keepLoggedRate = false
                    editingUnit = null
                },
                onDelete = {
                    portions.deleteUnit(barcode, u.unit)
                    editingUnit = null
                },
            )
        }

        if (showNewUnit) {
            val takenNames = units.flatMap { u ->
                if (unitPluralRes(u.unit) != null) listOf(u.unit, unitLabel(u.unit, 1), unitLabel(u.unit, 2))
                else listOf(u.unit)
            }
            NewUnitDialog(
                takenNames = takenNames,
                accentColor = accentColor,
                onDismiss = { showNewUnit = false },
                onSave = { name, grams ->
                    portions.saveUnit(barcode, name, grams)
                    settled = true
                    if (!unitMode) qtyText = "1"
                    selectedUnit = name
                    showNewUnit = false
                },
            )
        }

        if (showPresetsEdit && loaded != null) {
            PresetsEditDialog(
                presets = loaded.gramPresets,
                custom = loaded.presetsCustom,
                accentColor = accentColor,
                onDismiss = { showPresetsEdit = false },
                onSave = { values ->
                    portions.savePresets(barcode, values)
                    showPresetsEdit = false
                },
                onReset = {
                    portions.resetPresets(barcode)
                    showPresetsEdit = false
                },
            )
        }
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
private fun AddFoodSheetHeader(
    food: FoodItem,
    isFavorite: Boolean?,
    onToggleFavorite: () -> Unit,
    accentColor: Color,
) {
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
        if (isFavorite != null) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = stringResource(
                        if (isFavorite) R.string.favorite_remove else R.string.favorite_add
                    ),
                    tint = if (isFavorite) accentColor else GlassColors.textTertiary,
                )
            }
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
private fun AddFoodSheetPreview(
    food: FoodItem,
    gramsInt: Int?,
    accentColor: Color,
    showGrams: Boolean = false,
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
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = if (scaled != null) "${scaled.kcal} kcal" else "— kcal",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor,
            )
            if (showGrams && gramsInt != null) {
                Text(
                    text = " · $gramsInt g",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GlassColors.textSecondary,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
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

