package com.example.foodtracker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.foodtracker.model.AppLanguage
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.ui.theme.glassCard
import androidx.compose.ui.res.stringResource
import com.example.foodtracker.R
import com.example.foodtracker.viewmodel.AuthViewModel
import com.example.foodtracker.viewmodel.DeleteAccountState
import com.example.foodtracker.viewmodel.FoodViewModel
import com.example.foodtracker.util.notifications.MealReminderScheduler
import com.example.foodtracker.util.notifications.withReminder
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: FoodViewModel,
    authViewModel: AuthViewModel,
    onLogout: () -> Unit,
    onAccountDeleted: () -> Unit,
) {
    val settings by viewModel.appSettings.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val enableNotifMsg = stringResource(R.string.settings_enable_notifications_system)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.updateAppSettings(settings.copy(notificationsEnabled = true))
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(enableNotifMsg)
            }
        }
    }

    var showLogoutDialog by remember { mutableStateOf(false) }
    var editingMeal by remember { mutableStateOf<MealReminderScheduler.Meal?>(null) }
    val is24Hour = DateFormat.is24HourFormat(context)
    var showDeleteDialog by remember { mutableStateOf(false) }
    val deleteAccountState by authViewModel.deleteAccountState.collectAsState()

    Box(Modifier.fillMaxSize().background(GlassColors.backgroundDark)) {
        Column(
            Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxSize()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(GlassColors.cardBackground)
                        .border(1.dp, GlassColors.cardBorder, CircleShape)
                        .clickable { navController.popBackStack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.ArrowBackIosNew,
                        contentDescription = stringResource(R.string.action_back),
                        tint = GlassColors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    stringResource(R.string.settings_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = GlassColors.textPrimary
                )
            }

            Spacer(Modifier.height(4.dp))

            // ── Notificări ────────────────────────────────────────────────────
            SettingsSectionTitle(stringResource(R.string.settings_notifications))
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .glassCard(16)
            ) {
                SettingsToggleRow(
                    label = stringResource(R.string.settings_meal_reminders),
                    checked = settings.notificationsEnabled,
                    onCheckedChange = { newValue ->
                        if (newValue &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context, Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.updateAppSettings(settings.copy(notificationsEnabled = newValue))
                        }
                    }
                )
                MealReminderScheduler.Meal.values().forEach { meal ->
                    val reminder = meal.reminderOf(settings)
                    MealReminderRow(
                        emoji = meal.emoji,
                        label = stringResource(mealLabelRes(meal.key)),
                        time = formatMinuteOfDay(reminder.minuteOfDay, is24Hour),
                        checked = reminder.enabled,
                        enabled = settings.notificationsEnabled,
                        onTimeClick = { editingMeal = meal },
                        onCheckedChange = {
                            viewModel.updateAppSettings(
                                settings.withReminder(meal, reminder.copy(enabled = it))
                            )
                        }
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            Spacer(Modifier.height(14.dp))

            // ── Aspect ────────────────────────────────────────────────────────
            SettingsSectionTitle(stringResource(R.string.settings_appearance))
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .glassCard(16)
            ) {
                SettingsToggleRow(
                    label = stringResource(R.string.settings_theme_dark),
                    checked = settings.isDarkTheme,
                    onCheckedChange = {
                        viewModel.updateAppSettings(settings.copy(isDarkTheme = it))
                    }
                )
            }

            Spacer(Modifier.height(14.dp))

            // ── Limbă ─────────────────────────────────────────────────────────
            SettingsSectionTitle(stringResource(R.string.settings_language))
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .glassCard(16)
                    .padding(16.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        AppLanguage.ROMANIAN to "Română",
                        AppLanguage.ENGLISH  to "English"
                    ).forEach { (lang, label) ->
                        val isSelected = settings.language == lang
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSelected) GlassColors.accentGreen
                                    else GlassColors.cardBackground
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) GlassColors.accentGreen else GlassColors.cardBorder,
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable {
                                    viewModel.updateAppSettings(settings.copy(language = lang))
                                }
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) Color.Black else GlassColors.textSecondary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Cont ──────────────────────────────────────────────────────────
            SettingsSectionTitle(stringResource(R.string.settings_account))
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .glassCard(16)
            ) {
                SettingsActionRow(
                    label = stringResource(R.string.action_logout),
                    color = GlassColors.accentOrange,
                    onClick = { showLogoutDialog = true }
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(1.dp)
                        .background(GlassColors.cardBorder)
                )
                SettingsActionRow(
                    label = stringResource(R.string.settings_delete_account),
                    color = Color(0xFFFF4444),
                    onClick = { showDeleteDialog = true }
                )
            }

            Spacer(Modifier.height(14.dp))

            // ── Despre ────────────────────────────────────────────────────────
            SettingsSectionTitle(stringResource(R.string.settings_about))
            Spacer(Modifier.height(8.dp))
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .glassCard(16)
                    .clickable { uriHandler.openUri("https://world.openfoodfacts.org") }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text(
                    stringResource(R.string.settings_off_data),
                    fontSize = 13.sp,
                    color = GlassColors.textSecondary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_off_source),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GlassColors.textPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "world.openfoodfacts.org",
                    fontSize = 12.sp,
                    color = GlassColors.accentGreen,
                )
            }

            Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .glassCard(16)
                    .clickable { uriHandler.openUri("https://fdc.nal.usda.gov") }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text(
                    stringResource(R.string.settings_usda_data),
                    fontSize = 13.sp,
                    color = GlassColors.textSecondary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_usda_source),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GlassColors.textPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "fdc.nal.usda.gov",
                    fontSize = 12.sp,
                    color = GlassColors.accentGreen,
                )
            }

            Spacer(Modifier.height(100.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────
    // AlertDialog content runs in a separate window that re-provides the system
    // locale, so resolve the strings here (in the screen's localized scope) and
    // pass plain Strings into the dialog slots.
    val logoutLabel = stringResource(R.string.action_logout)
    val logoutConfirmMsg = stringResource(R.string.settings_logout_confirm)
    val deleteAccountLabel = stringResource(R.string.settings_delete_account)
    val deleteConfirmMsg = stringResource(R.string.settings_delete_confirm)
    val deleteLabel = stringResource(R.string.action_delete)
    val cancelLabel = stringResource(R.string.action_cancel)
    val deleteErrorMsg = stringResource(R.string.error_delete_account)
    val deleteNoInternetMsg = stringResource(R.string.error_no_internet)

    editingMeal?.let { meal ->
        val reminder = meal.reminderOf(settings)
        ReminderTimeDialog(
            initialMinuteOfDay = reminder.minuteOfDay,
            is24Hour = is24Hour,
            onDismiss = { editingMeal = null },
            onConfirm = { minuteOfDay ->
                editingMeal = null
                viewModel.updateAppSettings(
                    settings.withReminder(meal, reminder.copy(minuteOfDay = minuteOfDay))
                )
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(logoutLabel, color = GlassColors.textPrimary) },
            text  = {
                Text(
                    logoutConfirmMsg,
                    color = GlassColors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { showLogoutDialog = false; onLogout() }) {
                    Text(
                        logoutLabel,
                        color = GlassColors.accentOrange,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(cancelLabel, color = GlassColors.textSecondary)
                }
            },
            containerColor = GlassColors.cardBackground,
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showDeleteDialog) {
        val isDeleting = deleteAccountState is DeleteAccountState.Deleting
        val deleteRed = Color(0xFFFF4444)
        val errorRes = (deleteAccountState as? DeleteAccountState.Error)?.messageRes
        val inlineError = when (deleteAccountState) {
            is DeleteAccountState.Error ->
                if (errorRes == R.string.error_no_internet) deleteNoInternetMsg else deleteErrorMsg
            else -> null
        }
        AlertDialog(
            onDismissRequest = {
                if (!isDeleting) {
                    showDeleteDialog = false
                    authViewModel.clearDeleteAccountState()
                }
            },
            title = { Text(deleteAccountLabel, color = GlassColors.textPrimary) },
            text = {
                Column {
                    Text(deleteConfirmMsg, color = GlassColors.textSecondary)
                    if (inlineError != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            inlineError,
                            color = deleteRed,
                            fontSize = 13.sp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = {
                        authViewModel.deleteAccount {
                            onAccountDeleted()
                        }
                    }
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = deleteRed,
                        )
                    } else {
                        Text(
                            deleteLabel,
                            color = deleteRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = {
                        showDeleteDialog = false
                        authViewModel.clearDeleteAccountState()
                    }
                ) {
                    Text(cancelLabel, color = GlassColors.textSecondary)
                }
            },
            containerColor = GlassColors.cardBackground,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

// ── Shared components ─────────────────────────────────────────────────────────

@Composable
fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = GlassColors.textTertiary,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 24.dp)
    )
}

@Composable
fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 15.sp,
            color = GlassColors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor   = Color.Black,
                checkedTrackColor   = GlassColors.accentGreen,
                uncheckedThumbColor = GlassColors.textSecondary,
                uncheckedTrackColor = GlassColors.cardBorder
            )
        )
    }
}

@Composable
fun SettingsActionRow(label: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = color.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun MealReminderRow(
    emoji: String,
    label: String,
    time: String,
    checked: Boolean,
    enabled: Boolean,
    onTimeClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .padding(start = 28.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 16.sp)
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            fontSize = 14.sp,
            color = GlassColors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        Text(
            time,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = GlassColors.accentGreen,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled, onClick = onTimeClick)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor   = Color.Black,
                checkedTrackColor   = GlassColors.accentGreen,
                uncheckedThumbColor = GlassColors.textSecondary,
                uncheckedTrackColor = GlassColors.cardBorder
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(
    initialMinuteOfDay: Int,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialMinuteOfDay / 60,
        initialMinute = initialMinuteOfDay % 60,
        is24Hour = is24Hour
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_reminder_time_title), color = GlassColors.textPrimary) },
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = GlassColors.cardBorder,
                    selectorColor = GlassColors.accentGreen,
                    timeSelectorSelectedContainerColor = GlassColors.accentGreen.copy(alpha = 0.25f),
                    timeSelectorSelectedContentColor = GlassColors.textPrimary,
                    timeSelectorUnselectedContainerColor = GlassColors.cardBorder,
                    timeSelectorUnselectedContentColor = GlassColors.textSecondary,
                    clockDialSelectedContentColor = Color.Black,
                    clockDialUnselectedContentColor = GlassColors.textPrimary,
                    periodSelectorSelectedContainerColor = GlassColors.accentGreen.copy(alpha = 0.25f),
                    periodSelectorSelectedContentColor = GlassColors.textPrimary,
                    periodSelectorUnselectedContentColor = GlassColors.textSecondary,
                    periodSelectorBorderColor = GlassColors.cardBorder
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text(
                    stringResource(R.string.action_save),
                    color = GlassColors.accentGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = GlassColors.textSecondary)
            }
        },
        containerColor = GlassColors.cardBackground,
        shape = RoundedCornerShape(20.dp)
    )
}
