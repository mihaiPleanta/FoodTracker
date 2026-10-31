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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foodtracker.R
import com.example.foodtracker.model.MEAL_ORDER
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.util.AppLocale
import com.example.foodtracker.util.LocalAppLanguage
import com.example.foodtracker.util.PortionChoice
import com.example.foodtracker.util.toLocale
import com.example.foodtracker.viewmodel.CopyCandidate
import com.example.foodtracker.viewmodel.CopyMode
import com.example.foodtracker.viewmodel.CopyViewModel
import com.example.foodtracker.viewmodel.FoodViewModel
import com.example.foodtracker.viewmodel.PortionsViewModel
import com.example.foodtracker.viewmodel.SourceDayState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MEAL_EMOJI = mapOf("Breakfast" to "🌅", "Lunch" to "☀️", "Dinner" to "🌙", "Snacks" to "🍎")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopyFoodsSheet(
    copy: CopyViewModel,
    foodViewModel: FoodViewModel,
    portions: PortionsViewModel,
    accentColor: Color,
    onDismiss: () -> Unit,
) {
    val state by copy.state.collectAsState()
    val searchState by foodViewModel.searchState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val locale = LocalAppLanguage.current.toLocale()

    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(copy) { copy.copied.collect { currentOnDismiss() } }

    var showDatePicker by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<Long?>(null) }
    // A day change resets selection/edits in the VM, so an open editor must not outlive it.
    LaunchedEffect(state.selectedDay) { editingId = null }

    val mode = state.mode
    val mealName = (mode as? CopyMode.Meal)?.mealName
    val mealLower = mealName?.let { stringResource(mealLabelRes(it)).lowercase(locale) }
    val loaded = state.source as? SourceDayState.Loaded

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
                    .padding(top = 12.dp, bottom = 22.dp)
                    .navigationBarsPadding(),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .width(40.dp).height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(GlassColors.textTertiary),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    if (mealLower != null) stringResource(R.string.copy_title_meal, mealLower)
                    else stringResource(R.string.copy_title_day),
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GlassColors.textPrimary,
                )
                Spacer(Modifier.height(14.dp))

                DayStrip(
                    days = state.stripDays,
                    extraDay = state.extraDay,
                    daysWithContent = state.daysWithContent,
                    selected = state.selectedDay,
                    accentColor = accentColor,
                    locale = locale,
                    onSelect = { copy.selectDay(it) },
                    onCalendar = { showDatePicker = true },
                )
                Spacer(Modifier.height(12.dp))

                Column(Modifier.weight(1f, fill = false)) {
                    when {
                        state.summaryLoading -> CenteredSpinner(accentColor)
                        state.summaryError != null -> ErrorWithRetry(state.summaryError!!) { copy.retry() }
                        else -> when (val src = state.source) {
                            SourceDayState.None -> InfoText(
                                if (mealLower != null) stringResource(R.string.copy_none_recent_meal, mealLower)
                                else stringResource(R.string.copy_none_recent_day)
                            )
                            SourceDayState.Loading -> CenteredSpinner(accentColor)
                            is SourceDayState.Error -> ErrorWithRetry(src.messageRes) { copy.retry() }
                            is SourceDayState.Loaded -> if (src.candidates.isEmpty()) {
                                InfoText(
                                    if (mealLower != null) stringResource(R.string.copy_empty_meal_day, mealLower)
                                    else stringResource(R.string.copy_empty_day)
                                )
                            } else {
                                val day = state.selectedDay
                                if (day != null) {
                                    val kcal = src.candidates.sumOf { it.logged.calories }
                                    Text(
                                        stringResource(
                                            R.string.copy_day_summary,
                                            formatDay(day, locale),
                                            pluralStringResource(R.plurals.copy_foods_count, src.candidates.size, src.candidates.size),
                                            kcal,
                                        ),
                                        fontSize = 13.sp, color = GlassColors.textSecondary,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                                CandidateList(
                                    candidates = src.candidates,
                                    groupByMeal = mode is CopyMode.Day,
                                    accentColor = accentColor,
                                    onToggle = { copy.toggle(it) },
                                    onToggleMeal = { copy.toggleMeal(it) },
                                    onEdit = { editingId = it },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                val count = state.checkedCount
                Button(
                    onClick = { copy.copy() },
                    enabled = count > 0 && !state.copying,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color.Black,
                        disabledContainerColor = GlassColors.cardBackground,
                        disabledContentColor = GlassColors.textTertiary,
                    ),
                ) {
                    if (state.copying) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = Color.Black, strokeWidth = 2.dp)
                    } else {
                        Text(
                            pluralStringResource(R.plurals.copy_button, count, count),
                            fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val target = state.targetDate
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.selectedDay?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(target)
                override fun isSelectableYear(year: Int): Boolean = year <= target.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedDateMillis != null,
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            copy.selectDay(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                        }
                        showDatePicker = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) { DatePicker(state = pickerState) }
    }

    val editing = loaded?.candidates?.firstOrNull { it.sourceId == editingId }
    if (editing != null) {
        val logged = editing.logged
        val ingredients = logged.food.ingredients
        if (ingredients != null) {
            RecipePortionEditor(
                recipeName = logged.food.name,
                accentColor = accentColor,
                initialIngredients = ingredients,
                searchState = searchState,
                onSearch = { foodViewModel.searchFoods(it) },
                onClearSearch = { foodViewModel.clearSearch() },
                onDismiss = { editingId = null; foodViewModel.clearSearch() },
                onSave = { updated ->
                    copy.updateIngredients(editing.sourceId, updated)
                    editingId = null
                    foodViewModel.clearSearch()
                },
            )
        } else {
            AddFoodSheet(
                target = logged.food,
                accentColor = accentColor,
                confirmLabel = stringResource(R.string.action_save),
                portions = portions,
                initial = PortionChoice(logged.grams, logged.unit, logged.unitQty),
                onDismiss = { editingId = null },
                onConfirm = { _, choice ->
                    copy.updatePortion(editing.sourceId, choice.grams, choice.unit, choice.unitQty)
                    editingId = null
                },
            )
        }
    }
}

private fun formatDay(day: LocalDate, locale: Locale): String =
    day.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
        .replaceFirstChar { it.titlecase(locale) }

@Composable
private fun DayStrip(
    days: List<LocalDate>,
    extraDay: LocalDate?,
    daysWithContent: Set<LocalDate>,
    selected: LocalDate?,
    accentColor: Color,
    locale: Locale,
    onSelect: (LocalDate) -> Unit,
    onCalendar: () -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items(days) { d ->
            DayChip(d, d == selected, d in daysWithContent, accentColor, locale) { onSelect(d) }
        }
        if (extraDay != null) {
            item(key = "extra") {
                DayChip(extraDay, extraDay == selected, extraDay in daysWithContent, accentColor, locale) { onSelect(extraDay) }
            }
        }
        item(key = "calendar") {
            Box(
                Modifier
                    .width(52.dp).height(70.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(GlassColors.cardBackground)
                    .border(1.dp, GlassColors.cardBorder, RoundedCornerShape(14.dp))
                    .clickable(onClickLabel = stringResource(R.string.copy_pick_date)) { onCalendar() },
                contentAlignment = Alignment.Center,
            ) { Text("📅", fontSize = 22.sp) }
        }
    }
}

@Composable
private fun DayChip(
    day: LocalDate,
    isSelected: Boolean,
    hasContent: Boolean,
    accentColor: Color,
    locale: Locale,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val label = day.format(DateTimeFormatter.ofPattern("EEE", locale)).uppercase(locale)
    Column(
        Modifier
            .width(52.dp)
            .alpha(if (hasContent || isSelected) 1f else 0.4f)
            .clip(shape)
            .background(if (isSelected) accentColor else GlassColors.cardBackground)
            .border(1.dp, if (isSelected) Color.Transparent else GlassColors.cardBorder, shape)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            color = if (isSelected) Color.Black.copy(.7f) else GlassColors.textTertiary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            day.dayOfMonth.toString(), fontSize = 19.sp, fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.Black else GlassColors.textPrimary,
        )
    }
}

@Composable
private fun CenteredSpinner(accentColor: Color) {
    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = accentColor, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
    }
}

@Composable
private fun InfoText(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 14.sp, color = GlassColors.textSecondary)
    }
}

@Composable
private fun ErrorWithRetry(messageRes: Int, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(messageRes), fontSize = 14.sp, color = GlassColors.textSecondary)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

@Composable
private fun CandidateList(
    candidates: List<CopyCandidate>,
    groupByMeal: Boolean,
    accentColor: Color,
    onToggle: (Long) -> Unit,
    onToggleMeal: (String) -> Unit,
    onEdit: (Long) -> Unit,
) {
    val checkColors = CheckboxDefaults.colors(checkedColor = accentColor, checkmarkColor = Color.Black)
    LazyColumn(Modifier.fillMaxWidth()) {
        if (groupByMeal) {
            MEAL_ORDER.forEach { meal ->
                val group = candidates.filter { it.mealName == meal }
                if (group.isEmpty()) return@forEach
                item(key = "h_$meal") {
                    val all = group.all { it.checked }
                    val none = group.none { it.checked }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onToggleMeal(meal) }
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TriStateCheckbox(
                            state = when { all -> ToggleableState.On; none -> ToggleableState.Off; else -> ToggleableState.Indeterminate },
                            onClick = { onToggleMeal(meal) },
                            colors = checkColors,
                        )
                        Text(
                            "${MEAL_EMOJI[meal].orEmpty()} ${stringResource(mealLabelRes(meal))}",
                            fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GlassColors.textPrimary,
                        )
                    }
                }
                items(group, key = { it.sourceId }) { c -> CandidateRow(c, checkColors, onToggle, onEdit) }
            }
        } else {
            items(candidates, key = { it.sourceId }) { c -> CandidateRow(c, checkColors, onToggle, onEdit) }
        }
    }
}

@Composable
private fun CandidateRow(
    c: CopyCandidate,
    checkColors: androidx.compose.material3.CheckboxColors,
    onToggle: (Long) -> Unit,
    onEdit: (Long) -> Unit,
) {
    val l = c.logged
    val portion = if (l.unit != null && l.unitQty != null) {
        val qty = l.unitQty
        val q = if (qty % 1f == 0f) qty.toInt().toString() else qty.toString()
        "$q ${l.unit} · ${l.grams} g"
    } else "${l.grams} g"
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onEdit(c.sourceId) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = c.checked, onCheckedChange = { onToggle(c.sourceId) }, colors = checkColors)
        ProductThumbnail(l.food, 36.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                l.food.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                color = GlassColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(portion, fontSize = 12.sp, color = GlassColors.textSecondary)
        }
        Spacer(Modifier.width(8.dp))
        Text("${l.calories} kcal", fontSize = 13.sp, color = GlassColors.textSecondary)
        Spacer(Modifier.width(8.dp))
    }
}
