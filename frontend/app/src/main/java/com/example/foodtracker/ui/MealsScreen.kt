package com.example.foodtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import com.example.foodtracker.R
import com.example.foodtracker.model.FoodItem
import com.example.foodtracker.model.LoggedFood
import com.example.foodtracker.model.RecipeDto
import com.example.foodtracker.model.SavedRecipeDto
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.viewmodel.FoodViewModel
import com.example.foodtracker.viewmodel.RecipeUiState
import com.example.foodtracker.viewmodel.RecipeViewModel

private data class MealChip(val type: String, val labelRes: Int, val accent: Color)

private val mealChips = listOf(
    MealChip("BREAKFAST", R.string.meal_breakfast, Color(0xFFFFD600)),
    MealChip("LUNCH", R.string.meal_lunch, Color(0xFF00E676)),
    MealChip("DINNER", R.string.meal_dinner, Color(0xFF448AFF)),
    MealChip("SNACKS", R.string.meal_snacks, Color(0xFFFF6D00)),
)

// FoodViewModel keys meals by capitalized name; RecipeViewModel uses the wire form.
private val backendMealToFoodVm = mapOf(
    "BREAKFAST" to "Breakfast",
    "LUNCH" to "Lunch",
    "DINNER" to "Dinner",
    "SNACKS" to "Snacks",
)

// A recipe is logged as a single 100g "portion", so its per-serving macros map
// directly onto the per-100g fields the food log stores.
private fun RecipeDto.toLoggedPortion(): LoggedFood = LoggedFood(
    food = FoodItem(
        barcode = "recipe",
        name = title,
        categories = listOf("recipe"),
        per100g = kcalPerServing,
        protein100g = proteinG.toFloat(),
        carbs100g = carbsG.toFloat(),
        fat100g = fatG.toFloat(),
    ),
    grams = 100,
)

@Composable
fun MealsScreen(
    foodViewModel: FoodViewModel,
    viewModel: RecipeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val recipeState by viewModel.recipeState.collectAsState()
    val saved by viewModel.savedRecipes.collectAsState()
    val selectedMeal by viewModel.selectedMealType.collectAsState()
    val accent = mealChips.first { it.type == selectedMeal }.accent
    val savedForMeal = saved.filter { it.mealType == selectedMeal }
    var openedRecipe by remember { mutableStateOf<SavedRecipeDto?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GlassColors.backgroundDark)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 120.dp),
    ) {
        item {
            Text(
                stringResource(R.string.meals_title),
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = GlassColors.textPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.meals_subtitle),
                fontSize = 13.sp,
                color = GlassColors.textSecondary,
            )
            Spacer(Modifier.height(20.dp))
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                mealChips.forEach { chip ->
                    val selected = chip.type == selectedMeal
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selected) chip.accent.copy(alpha = 0.18f)
                                else GlassColors.cardBackground
                            )
                            .border(
                                1.dp,
                                if (selected) chip.accent else GlassColors.cardBorder,
                                RoundedCornerShape(14.dp),
                            )
                            .clickable { viewModel.setMealType(chip.type) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(chip.labelRes),
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) chip.accent else GlassColors.textSecondary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(accent)
                    .clickable(enabled = recipeState !is RecipeUiState.Loading) {
                        viewModel.generate()
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.meals_generate_button),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        item {
            when (val s = recipeState) {
                is RecipeUiState.Idle -> {}
                is RecipeUiState.Loading -> Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(color = accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.meals_cooking), color = GlassColors.textSecondary, fontSize = 14.sp)
                }
                is RecipeUiState.Result -> RecipeCard(
                    recipe = s.recipe,
                    accent = accent,
                    onSave = { viewModel.saveCurrent() },
                    onRegenerate = { viewModel.generate() },
                    onAddToJournal = {
                        val mealName = backendMealToFoodVm[selectedMeal] ?: "Breakfast"
                        foodViewModel.addFoodToMeal(mealName, s.recipe.toLoggedPortion())
                        Toast.makeText(context, context.getString(R.string.meals_added_to_journal), Toast.LENGTH_SHORT).show()
                    },
                )
                is RecipeUiState.Error -> Column {
                    Text(
                        if (s.arg != null) stringResource(s.messageRes, s.arg)
                        else stringResource(s.messageRes),
                        color = Color(0xFFFF5252),
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.action_retry),
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { viewModel.generate() },
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }

        item {
            Text(
                stringResource(R.string.meals_my_recipes),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = GlassColors.textPrimary,
            )
            Spacer(Modifier.height(12.dp))
            if (savedForMeal.isEmpty()) {
                Text(
                    stringResource(R.string.meals_none_saved),
                    color = GlassColors.textTertiary,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        items(savedForMeal, key = { it.id }) { item ->
            SavedRecipeCard(
                item = item,
                onClick = { openedRecipe = item },
                onDelete = { viewModel.delete(item.id) },
            )
            Spacer(Modifier.height(10.dp))
        }
    }

    openedRecipe?.let { opened ->
        val openedAccent = mealChips.first { it.type == opened.mealType }.accent
        SavedRecipeDialog(
            item = opened,
            accent = openedAccent,
            onAddToJournal = {
                val mealName = backendMealToFoodVm[opened.mealType] ?: "Breakfast"
                foodViewModel.addFoodToMeal(mealName, opened.recipe.toLoggedPortion())
                Toast.makeText(context, context.getString(R.string.meals_added_to_journal), Toast.LENGTH_SHORT).show()
                openedRecipe = null
            },
            onDismiss = { openedRecipe = null },
        )
    }
}

@Composable
private fun SavedRecipeDialog(
    item: SavedRecipeDto,
    accent: Color,
    onAddToJournal: () -> Unit,
    onDismiss: () -> Unit,
) {
    val recipe = item.recipe
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(GlassColors.cardBackground)
                .border(1.dp, GlassColors.cardBorder, RoundedCornerShape(20.dp))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(recipe.title, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = GlassColors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(recipe.description, fontSize = 13.sp, color = GlassColors.textSecondary)
            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.meals_ingredients), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = accent)
            Spacer(Modifier.height(6.dp))
            recipe.ingredients.forEach { ing ->
                Text("• ${ing.name} — ${ing.quantity}", fontSize = 13.sp, color = GlassColors.textPrimary)
                Spacer(Modifier.height(2.dp))
            }
            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.meals_steps), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = accent)
            Spacer(Modifier.height(6.dp))
            recipe.steps.forEachIndexed { i, step ->
                Text("${i + 1}. $step", fontSize = 13.sp, color = GlassColors.textPrimary)
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(10.dp))

            Text(
                "${recipe.kcalPerServing} kcal · P ${recipe.proteinG}g · C ${recipe.carbsG}g · G ${recipe.fatG}g / porție",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = GlassColors.textSecondary,
            )
            Spacer(Modifier.height(18.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent)
                    .clickable { onAddToJournal() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.meals_add_to_journal), fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 14.sp) }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, GlassColors.cardBorder, RoundedCornerShape(12.dp))
                    .clickable { onDismiss() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.meals_close), fontWeight = FontWeight.Bold, color = GlassColors.textPrimary, fontSize = 14.sp) }
        }
    }
}

@Composable
private fun RecipeCard(
    recipe: RecipeDto,
    accent: Color,
    onSave: () -> Unit,
    onRegenerate: () -> Unit,
    onAddToJournal: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(GlassColors.cardBackground)
            .border(1.dp, GlassColors.cardBorder, RoundedCornerShape(20.dp))
            .padding(18.dp),
    ) {
        Text(recipe.title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = GlassColors.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(recipe.description, fontSize = 13.sp, color = GlassColors.textSecondary)
        Spacer(Modifier.height(14.dp))

        Text(stringResource(R.string.meals_ingredients), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = accent)
        Spacer(Modifier.height(6.dp))
        recipe.ingredients.forEach { ing ->
            Text("• ${ing.name} — ${ing.quantity}", fontSize = 13.sp, color = GlassColors.textPrimary)
        }
        Spacer(Modifier.height(14.dp))

        Text(stringResource(R.string.meals_steps), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = accent)
        Spacer(Modifier.height(6.dp))
        recipe.steps.forEachIndexed { i, step ->
            Text("${i + 1}. $step", fontSize = 13.sp, color = GlassColors.textPrimary)
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(14.dp))

        Text(
            "${recipe.kcalPerServing} kcal · P ${recipe.proteinG}g · C ${recipe.carbsG}g · G ${recipe.fatG}g / porție",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = GlassColors.textSecondary,
        )
        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(accent)
                .clickable { onAddToJournal() }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.meals_add_to_journal), fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 14.sp) }
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, GlassColors.cardBorder, RoundedCornerShape(12.dp))
                    .clickable { onSave() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold, color = GlassColors.textPrimary, fontSize = 14.sp) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, GlassColors.cardBorder, RoundedCornerShape(12.dp))
                    .clickable { onRegenerate() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.meals_regenerate), fontWeight = FontWeight.Bold, color = GlassColors.textPrimary, fontSize = 14.sp) }
        }
    }
}

@Composable
private fun SavedRecipeCard(item: SavedRecipeDto, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassColors.cardBackground)
            .border(1.dp, GlassColors.cardBorder, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = GlassColors.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(
                "${item.recipe.kcalPerServing} kcal / porție",
                fontSize = 12.sp,
                color = GlassColors.textTertiary,
            )
        }
        Text(
            stringResource(R.string.action_delete),
            fontSize = 13.sp,
            color = Color(0xFFFF5252),
            modifier = Modifier.clickable { onDelete() },
        )
    }
}
