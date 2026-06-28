package com.example.foodtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.example.foodtracker.util.AppLocale
import com.example.foodtracker.util.LocalAppLanguage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foodtracker.R
import com.example.foodtracker.model.FoodIngredient
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.viewmodel.FoodViewModel

/**
 * Editor for a recipe portion: each ingredient's grams can be edited or the row
 * removed; the total kcal/macros recompute live. "Salvează" returns the resulting
 * ingredient list (the caller recomputes + PATCHes).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipePortionEditor(
    recipeName: String,
    accentColor: Color,
    initialIngredients: List<FoodIngredient>,
    searchState: FoodViewModel.SearchUiState,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (List<FoodIngredient>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Parallel state: ingredient identity (name + per-100g macros) and editable grams text.
    val ings = remember { mutableStateListOf(*initialIngredients.toTypedArray()) }
    val gramsText = remember {
        mutableStateListOf(*initialIngredients.map { it.grams.toInt().toString() }.toTypedArray())
    }

    var adding by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(query) { if (adding) onSearch(query) }

    fun addIngredient(item: com.example.foodtracker.model.FoodItem) {
        ings.add(FoodIngredient(item.name, 100f, item.per100g.toFloat(), item.protein100g, item.carbs100g, item.fat100g))
        gramsText.add("100")
        adding = false
        query = ""
        onClearSearch()
    }

    fun gramsAt(i: Int): Float = gramsText[i].toFloatOrNull() ?: 0f
    val totalKcal = ings.indices.sumOf { (ings[it].kcal100g * gramsAt(it) / 100).toDouble() }
    val totalP = ings.indices.sumOf { (ings[it].protein100g * gramsAt(it) / 100).toDouble() }
    val totalC = ings.indices.sumOf { (ings[it].carbs100g * gramsAt(it) / 100).toDouble() }
    val totalF = ings.indices.sumOf { (ings[it].fat100g * gramsAt(it) / 100).toDouble() }

    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(saving) {
        if (saving) {
            val result = ings.indices.map { ings[it].copy(grams = gramsAt(it).coerceAtLeast(1f)) }
            kotlinx.coroutines.delay(600)
            onSave(result)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = ContentMaxWidth,
        containerColor = GlassColors.cardBackgroundAlt,
        dragHandle = null,
    ) {
      AppLocale(LocalAppLanguage.current) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(top = 16.dp, bottom = 22.dp)
                .navigationBarsPadding(),
        ) {
            Text(recipeName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GlassColors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "${totalKcal.toInt()} kcal  ·  P ${totalP.toInt()}g  ·  C ${totalC.toInt()}g  ·  G ${totalF.toInt()}g",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = accentColor,
            )
            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ings.indices.toList().forEach { i ->
                    IngredientEditRow(
                        name = ings[i].name,
                        gramsText = gramsText[i],
                        accentColor = accentColor,
                        onGramsChange = { new -> if (new.all { it.isDigit() } && new.length <= 4) gramsText[i] = new },
                        onRemove = if (ings.size > 1) {
                            { gramsText.removeAt(i); ings.removeAt(i) }
                        } else null,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            if (!adding) {
                Text(
                    stringResource(R.string.recipe_editor_add_ingredient),
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = accentColor,
                    modifier = Modifier.clickable { adding = true; query = "" }.padding(vertical = 6.dp),
                )
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.recipe_editor_search_hint), color = GlassColors.textSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = accentColor,
                        unfocusedIndicatorColor = GlassColors.cardBorder,
                        focusedTextColor = GlassColors.textPrimary,
                        unfocusedTextColor = GlassColors.textPrimary,
                    ),
                )
                val results = (searchState as? FoodViewModel.SearchUiState.Results)?.items.orEmpty()
                if (results.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Column(
                        modifier = Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        results.take(12).forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(GlassColors.cardBackground)
                                    .clickable { addIngredient(item) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(item.name, fontSize = 13.sp, color = GlassColors.textPrimary,
                                    maxLines = 1, modifier = Modifier.weight(1f).padding(end = 8.dp))
                                Text("${item.per100g} kcal/100g", fontSize = 11.sp, color = GlassColors.textSecondary)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            AnimatedActionButton(
                label = stringResource(R.string.action_save),
                successLabel = stringResource(R.string.action_saved),
                phase = if (saving) ActionPhase.Success else ActionPhase.Idle,
                accent = Brush.horizontalGradient(listOf(accentColor, accentColor)),
                modifier = Modifier.fillMaxWidth(),
                onClick = { saving = true },
            )
        }
      }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientEditRow(
    name: String,
    gramsText: String,
    accentColor: Color,
    onGramsChange: (String) -> Unit,
    onRemove: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GlassColors.cardBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GlassColors.textPrimary,
            maxLines = 2, modifier = Modifier.weight(1f).padding(end = 10.dp),
        )
        OutlinedTextField(
            value = gramsText,
            onValueChange = onGramsChange,
            suffix = { Text("g", fontSize = 13.sp, color = GlassColors.textSecondary) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(96.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = accentColor,
                unfocusedIndicatorColor = GlassColors.cardBorder,
                focusedTextColor = GlassColors.textPrimary,
                unfocusedTextColor = GlassColors.textPrimary,
            ),
        )
        if (onRemove != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF4444).copy(alpha = 0.12f))
                    .clickable { onRemove() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Close, contentDescription = null,
                    tint = Color(0xFFFF6B6B), modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
