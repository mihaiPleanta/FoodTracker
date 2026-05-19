package com.example.foodtracker.ui

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.foodtracker.model.AppLanguage
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.ui.theme.glassCard
import com.example.foodtracker.viewmodel.FoodViewModel

@Composable
fun SettingsScreen(navController: NavController, viewModel: FoodViewModel) {
    val settings by viewModel.appSettings.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(GlassColors.backgroundDark)) {
        Column(
            Modifier
                .fillMaxSize()
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
                        contentDescription = "Înapoi",
                        tint = GlassColors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    "Setări",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = GlassColors.textPrimary
                )
            }

            Spacer(Modifier.height(4.dp))

            // ── Notificări ────────────────────────────────────────────────────
            SettingsSectionTitle("NOTIFICĂRI")
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .glassCard(16)
            ) {
                SettingsToggleRow(
                    label = "Remindere mese",
                    checked = settings.notificationsEnabled,
                    onCheckedChange = {
                        viewModel.updateAppSettings(settings.copy(notificationsEnabled = it))
                    }
                )
            }

            Spacer(Modifier.height(14.dp))

            // ── Aspect ────────────────────────────────────────────────────────
            SettingsSectionTitle("ASPECT")
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .glassCard(16)
            ) {
                SettingsToggleRow(
                    label = "Temă întunecată",
                    checked = settings.isDarkTheme,
                    onCheckedChange = {
                        viewModel.updateAppSettings(settings.copy(isDarkTheme = it))
                    }
                )
            }

            Spacer(Modifier.height(14.dp))

            // ── Limbă ─────────────────────────────────────────────────────────
            SettingsSectionTitle("LIMBĂ")
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
            SettingsSectionTitle("CONT")
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .glassCard(16)
            ) {
                SettingsActionRow(
                    label = "Deconectare",
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
                    label = "Șterge cont",
                    color = Color(0xFFFF4444),
                    onClick = { showDeleteDialog = true }
                )
            }

            Spacer(Modifier.height(14.dp))

            // ── Despre ────────────────────────────────────────────────────────
            SettingsSectionTitle("DESPRE")
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
                    "Date despre produse",
                    fontSize = 13.sp,
                    color = GlassColors.textSecondary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Open Food Facts (ODbL)",
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

            Spacer(Modifier.height(100.dp))
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Deconectare", color = GlassColors.textPrimary) },
            text  = {
                Text(
                    "Ești sigur că vrei să te deconectezi?",
                    color = GlassColors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(
                        "Deconectare",
                        color = GlassColors.accentOrange,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Anulează", color = GlassColors.textSecondary)
                }
            },
            containerColor = GlassColors.cardBackground,
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Șterge cont", color = GlassColors.textPrimary) },
            text  = {
                Text(
                    "Această acțiune este ireversibilă. Ești sigur?",
                    color = GlassColors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(
                        "Șterge",
                        color = Color(0xFFFF4444),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Anulează", color = GlassColors.textSecondary)
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
