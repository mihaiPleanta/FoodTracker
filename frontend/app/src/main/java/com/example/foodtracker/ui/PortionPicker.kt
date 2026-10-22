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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foodtracker.R
import com.example.foodtracker.model.PortionUnit
import com.example.foodtracker.model.UnitSource
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.util.AppLocale
import com.example.foodtracker.util.LocalAppLanguage
import com.example.foodtracker.util.STANDARD_UNITS
import com.example.foodtracker.util.unitPluralRes
import java.text.DecimalFormatSymbols

private val ErrorRed = Color(0xFFFF6B6B)

// ── Pure helpers (unit-tested) ────────────────────────────────────────────────

/**
 * Editable text for a quantity: "2", "1,5", "1,25" (up to 2 decimals, trailing zeros trimmed) —
 * never "½", so [com.example.foodtracker.util.parseQty] reads back the same value.
 */
fun qtyInputText(qty: Float, decimalSeparator: Char): String =
    if (qty == qty.toInt().toFloat()) qty.toInt().toString()
    else String.format(java.util.Locale.ROOT, "%.2f", qty)
        .trimEnd('0').trimEnd('.')
        .replace('.', decimalSeparator)

private val EDITABLE_QTY = Regex("""^\d*([.,]\d{0,2})?$""")

/** What the quantity field accepts while typing: ≤ 5 chars, digits, one separator, ≤ 2 decimals. */
fun isEditableQty(text: String): Boolean = text.length <= 5 && EDITABLE_QTY.matches(text)

/** Grams per unit typed in a dialog: decimals with comma or dot, 0 < g ≤ 5000 (backend limit). */
fun parseUnitGrams(text: String): Float? {
    val v = text.trim().replace(',', '.').toFloatOrNull() ?: return null
    return if (v.isFinite() && v > 0f && v <= 5000f) v else null
}

/** "100, 30 50" → [30, 50, 100]; null if empty, more than 5 values, or any value outside 1..5000. */
fun parsePresets(text: String): List<Int>? {
    val parts = text.split(Regex("[,;\\s]+")).filter { it.isNotBlank() }
    if (parts.isEmpty()) return null
    val values = parts.map { p -> p.toIntOrNull()?.takeIf { it in 1..5000 } ?: return null }
    val distinct = values.distinct().sorted()
    return if (distinct.size in 1..5) distinct else null
}

fun isValidUnitName(name: String): Boolean = name.trim().length in 1..30

/** True when [name] matches (trim + case-insensitive) one of [taken] or a standard unit key. */
fun isUnitNameTaken(name: String, taken: Collection<String>): Boolean {
    val key = name.trim().lowercase()
    return (taken + STANDARD_UNITS).any { it.trim().lowercase() == key }
}

// ── Labels ────────────────────────────────────────────────────────────────────

/** Label of a unit without the number: "linguriță" (count 1) / "lingurițe" (count 2); free names as typed. */
@Composable
fun unitLabel(unit: String, count: Int): String {
    val res = unitPluralRes(unit) ?: return unit
    return pluralStringResource(res, count, "").trim()
}

@Composable
private fun formatUnitGrams(grams: Float): String {
    val sep = DecimalFormatSymbols.getInstance(LocalConfiguration.current.locales[0]).decimalSeparator
    return qtyInputText((grams * 10f).let { kotlin.math.round(it) / 10f }, sep)
}

// ── Stepper ───────────────────────────────────────────────────────────────────

@Composable
fun PortionQuantityStepper(
    qtyText: String,
    onQtyTextChange: (String) -> Unit,
    onStep: (Float) -> Unit,
    accentColor: Color,
    isInvalid: Boolean,
    step: Float = 0.5f,
) {
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
                .border(2.dp, if (isInvalid) ErrorRed else accentColor, RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepperButton(symbol = "−", onClick = { onStep(-step) })
            BasicTextField(
                value = qtyText,
                onValueChange = { new ->
                    if (isEditableQty(new)) onQtyTextChange(new)
                },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    color = GlassColors.textPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                ),
                singleLine = true,
                cursorBrush = SolidColor(accentColor),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            StepperButton(symbol = "+", onClick = { onStep(step) })
        }
    }
}

@Composable
private fun StepperButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(GlassColors.cardBackground)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GlassColors.textPrimary)
    }
}

// ── Unit list ─────────────────────────────────────────────────────────────────

/**
 * Radio list: "grame", then [units] in order, then "＋ Unitate nouă" (only when [onAddNew] != null).
 * [selectedUnit] null = grams. [onEdit] null hides the pencils (portions not loaded).
 */
@Composable
fun PortionUnitList(
    units: List<PortionUnit>,
    selectedUnit: String?,
    labelCount: Int,
    accentColor: Color,
    onSelect: (String?) -> Unit,
    onEdit: ((PortionUnit) -> Unit)?,
    onAddNew: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, GlassColors.cardBorder, RoundedCornerShape(12.dp)),
    ) {
        UnitRow(
            label = stringResource(R.string.portion_grams_row),
            gramsText = null,
            selected = selectedUnit == null,
            accentColor = accentColor,
            onClick = { onSelect(null) },
            onEdit = null,
        )
        units.forEach { u ->
            HorizontalDivider(color = GlassColors.cardBorderSubtle)
            val g = formatUnitGrams(u.grams)
            UnitRow(
                label = unitLabel(u.unit, labelCount),
                gramsText = if (u.source == UnitSource.ESTIMATE) {
                    "${stringResource(R.string.portion_approx_prefix)} $g g"
                } else "$g g",
                selected = selectedUnit == u.unit,
                accentColor = accentColor,
                onClick = { onSelect(u.unit) },
                onEdit = onEdit?.let { edit -> { edit(u) } },
            )
        }
        if (onAddNew != null) {
            HorizontalDivider(color = GlassColors.cardBorderSubtle)
            Text(
                text = "＋ ${stringResource(R.string.portion_new_unit)}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = GlassColors.textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GlassColors.backgroundSurface2)
                    .clickable(onClick = onAddNew)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun UnitRow(
    label: String,
    gramsText: String?,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    onEdit: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) accentColor.copy(alpha = 0.12f) else GlassColors.backgroundSurface2)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
            .heightIn(min = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) accentColor else GlassColors.textTertiary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(accentColor))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) accentColor else GlassColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (gramsText != null) {
                Text(text = gramsText, fontSize = 11.sp, color = GlassColors.textTertiary)
            }
        }
        if (onEdit != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.portion_unit_edit_title),
                    tint = GlassColors.textTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// ── Quick grams row ───────────────────────────────────────────────────────────

@Composable
fun GramPresetsRow(
    presets: List<Int>,
    gramsText: String,
    accentColor: Color,
    onTap: (Int) -> Unit,
    onEdit: (() -> Unit)?,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        presets.forEach { value ->
            val isActive = gramsText == value.toString()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isActive) accentColor.copy(alpha = 0.22f) else Color(0xFF333333))
                    .border(
                        1.5.dp,
                        if (isActive) accentColor else Color(0xFF454545),
                        RoundedCornerShape(14.dp),
                    )
                    .clickable { onTap(value) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${value}g",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isActive) accentColor else GlassColors.textPrimary,
                    maxLines = 1,
                )
            }
        }
        if (onEdit != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.portion_presets_edit_title),
                    tint = GlassColors.textTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// ── Dialogs ───────────────────────────────────────────────────────────────────

@Composable
private fun dialogFieldColors(accentColor: Color) = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    errorContainerColor = Color.Transparent,
    focusedIndicatorColor = accentColor,
    unfocusedIndicatorColor = GlassColors.cardBorder,
    focusedTextColor = GlassColors.textPrimary,
    unfocusedTextColor = GlassColors.textPrimary,
    focusedLabelColor = accentColor,
    unfocusedLabelColor = GlassColors.textSecondary,
    cursorColor = accentColor,
)

@Composable
private fun PortionDialog(
    title: String,
    accentColor: Color,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    extraAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            AppLocale(LocalAppLanguage.current) { Text(title, color = GlassColors.textPrimary) }
        },
        text = { AppLocale(LocalAppLanguage.current) { content() } },
        confirmButton = {
            AppLocale(LocalAppLanguage.current) {
                Row {
                    extraAction?.invoke()
                    TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                        Text(
                            stringResource(R.string.portion_save),
                            color = if (confirmEnabled) accentColor else GlassColors.textTertiary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        },
        dismissButton = {
            AppLocale(LocalAppLanguage.current) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel), color = GlassColors.textSecondary)
                }
            }
        },
        containerColor = GlassColors.cardBackground,
        shape = RoundedCornerShape(20.dp),
    )
}

/** Pencil on a unit row: correct its grams; "Șterge" only when [canDelete]. */
@Composable
fun UnitEditDialog(
    unit: PortionUnit,
    canDelete: Boolean,
    accentColor: Color,
    onDismiss: () -> Unit,
    onSave: (Float) -> Unit,
    onDelete: () -> Unit,
) {
    val sep = DecimalFormatSymbols.getInstance(LocalConfiguration.current.locales[0]).decimalSeparator
    var gramsText by remember(unit) {
        mutableStateOf(qtyInputText(kotlin.math.round(unit.grams * 10f) / 10f, sep))
    }
    val grams = parseUnitGrams(gramsText)
    val label = unitLabel(unit.unit, 1)
    PortionDialog(
        title = stringResource(R.string.portion_unit_edit_title),
        accentColor = accentColor,
        confirmEnabled = grams != null,
        onConfirm = { grams?.let(onSave) },
        onDismiss = onDismiss,
        extraAction = if (canDelete) {
            {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.portion_delete), color = ErrorRed)
                }
            }
        } else null,
    ) {
        Column {
            Text(label, color = GlassColors.textSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = gramsText,
                onValueChange = { gramsText = it },
                label = { Text(stringResource(R.string.portion_unit_grams_label)) },
                suffix = { Text("g", color = GlassColors.textSecondary) },
                singleLine = true,
                isError = gramsText.isNotBlank() && grams == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = dialogFieldColors(accentColor),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** "＋ Unitate nouă": name + grams; inline error when [takenNames] already holds the name. */
@Composable
fun NewUnitDialog(
    takenNames: List<String>,
    accentColor: Color,
    onDismiss: () -> Unit,
    onSave: (String, Float) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var gramsText by remember { mutableStateOf("") }
    val grams = parseUnitGrams(gramsText)
    val taken = name.isNotBlank() && isUnitNameTaken(name, takenNames)
    val valid = isValidUnitName(name) && !taken && grams != null
    PortionDialog(
        title = stringResource(R.string.portion_new_unit),
        accentColor = accentColor,
        confirmEnabled = valid,
        onConfirm = { if (valid) onSave(name.trim(), grams!!) },
        onDismiss = onDismiss,
    ) {
        Column {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 30) name = it },
                label = { Text(stringResource(R.string.portion_unit_name_label)) },
                singleLine = true,
                isError = taken,
                supportingText = if (taken) {
                    { Text(stringResource(R.string.portion_unit_exists_error), color = ErrorRed) }
                } else null,
                colors = dialogFieldColors(accentColor),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = gramsText,
                onValueChange = { gramsText = it },
                label = { Text(stringResource(R.string.portion_unit_grams_label)) },
                suffix = { Text("g", color = GlassColors.textSecondary) },
                singleLine = true,
                isError = gramsText.isNotBlank() && grams == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = dialogFieldColors(accentColor),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Pencil at the end of the quick-grams row: up to 5 values; "Revino la propuse" only when [custom]. */
@Composable
fun PresetsEditDialog(
    presets: List<Int>,
    custom: Boolean,
    accentColor: Color,
    onDismiss: () -> Unit,
    onSave: (List<Int>) -> Unit,
    onReset: () -> Unit,
) {
    var text by remember(presets) { mutableStateOf(presets.joinToString(", ")) }
    val parsed = parsePresets(text)
    PortionDialog(
        title = stringResource(R.string.portion_presets_edit_title),
        accentColor = accentColor,
        confirmEnabled = parsed != null,
        onConfirm = { parsed?.let(onSave) },
        onDismiss = onDismiss,
        extraAction = if (custom) {
            {
                TextButton(onClick = onReset) {
                    Text(stringResource(R.string.portion_reset_presets), color = GlassColors.textSecondary)
                }
            }
        } else null,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.portion_presets_label)) },
            singleLine = true,
            isError = text.isNotBlank() && parsed == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            colors = dialogFieldColors(accentColor),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
