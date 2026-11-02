package com.example.foodtracker.ui.customfood

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.foodtracker.R
import com.example.foodtracker.ui.ContentMaxWidth
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.ui.theme.glassCard
import com.example.foodtracker.util.findActivity
import com.example.foodtracker.util.label.LabelParser
import com.example.foodtracker.util.label.LabelTextRecognizer
import com.example.foodtracker.util.label.clearLabelPhotos
import com.example.foodtracker.util.label.newLabelPhotoUri
import com.example.foodtracker.viewmodel.CustomFoodViewModel
import com.example.foodtracker.viewmodel.SaveState
import kotlinx.coroutines.launch
import java.util.UUID

/** How the screen was opened: creating (optionally prefilled, optionally for the current meal) or editing. */
sealed interface CustomFoodMode {
    data class New(val barcode: String?, val name: String?, val forMeal: Boolean) : CustomFoodMode
    data class Edit(val id: Int) : CustomFoodMode
}

private val ErrorRed = Color(0xFFFF6B6B)

/**
 * The screen instance that currently owns the VM's form flow. On a replacing navigation
 * (e.g. "Deschide produsul") the old screen is disposed only after the new one started its
 * flow, so the old one must not clear it.
 */
private var activeFlowOwner: String? = null

@Composable
fun CustomFoodScreen(
    vm: CustomFoodViewModel,
    mode: CustomFoodMode,
    onDone: () -> Unit,
    onOpenExisting: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val ownerToken = rememberSaveable { UUID.randomUUID().toString() }
    var photoStep by rememberSaveable { mutableStateOf(mode is CustomFoodMode.New) }
    var showLabelDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showZoom by remember { mutableStateOf(false) }
    var recognizing by remember { mutableStateOf(false) }

    // Starts once per screen instance; survives configuration changes (owner unchanged) and
    // restarts after process death (the static owner and the VM are both gone).
    LaunchedEffect(Unit) {
        if (activeFlowOwner != ownerToken) {
            activeFlowOwner = ownerToken
            when (mode) {
                is CustomFoodMode.New -> vm.startNew(mode.barcode, mode.name, mode.forMeal)
                is CustomFoodMode.Edit -> vm.startEdit(mode.id)
            }
        }
    }

    // Leaving the flow (back, save, delete, navigation): drop the temp photo and the form,
    // but not on a configuration change and not when another screen already took over.
    DisposableEffect(Unit) {
        onDispose {
            val changingConfig = context.findActivity()?.isChangingConfigurations == true
            if (!changingConfig && activeFlowOwner == ownerToken) {
                activeFlowOwner = null
                clearLabelPhotos(context)
                vm.clearFlow()
            }
        }
    }

    val form by vm.form.collectAsState()
    val labelImage by vm.labelImage.collectAsState()
    val ocrMessage by vm.ocrMessage.collectAsState()
    val saveState by vm.saveState.collectAsState()
    val showErrors by vm.showErrors.collectAsState()

    LaunchedEffect(saveState) {
        if (saveState is SaveState.Saved) {
            clearLabelPhotos(context)
            onDone()
        }
    }

    fun recognize(uri: Uri) {
        scope.launch {
            recognizing = true
            val lines = LabelTextRecognizer.recognize(context, uri)
            val parsed = LabelParser.parse(lines)
            deleteOwnPhoto(context, labelImage, keep = uri)
            vm.onLabelRecognized(uri.toString(), parsed)
            recognizing = false
            photoStep = false
            showLabelDialog = false
        }
    }

    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCameraUri?.let(Uri::parse)
        pendingCameraUri = null
        if (uri == null) return@rememberLauncherForActivityResult
        if (ok) recognize(uri) else deleteOwnPhoto(context, uri.toString(), keep = null)
    }
    // The picked content:// URI is read in place by the on-device OCR; nothing is copied or uploaded.
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) recognize(uri)
    }
    val photoActions = PhotoActions(
        onCamera = {
            val uri = newLabelPhotoUri(context)
            pendingCameraUri = uri.toString()
            cameraLauncher.launch(uri)
        },
        onGallery = {
            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
    )

    val isEdit = mode is CustomFoodMode.Edit
    val editLoading = isEdit && form.values.isEmpty() && saveState !is SaveState.Error
    val editMissing = isEdit && form.values.isEmpty() && saveState is SaveState.Error

    Box(Modifier.fillMaxSize().background(GlassColors.backgroundDark)) {
        Column(
            Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxSize()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .imePadding()
        ) {
            Header(
                title = stringResource(if (isEdit) R.string.custom_food_title_edit else R.string.custom_food_title_new),
                onBack = onDone,
                showActions = isEdit && !editLoading && !editMissing,
                onFillFromLabel = { showLabelDialog = true },
                onDelete = { showDeleteDialog = true },
            )
            when {
                editLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = GlassColors.accentGreen)
                }
                editMissing -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource((saveState as SaveState.Error).msg),
                            color = ErrorRed,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = onDone) {
                            Text(stringResource(R.string.action_back), color = GlassColors.accentGreen)
                        }
                    }
                }
                photoStep -> PhotoStep(
                    recognizing = recognizing,
                    actions = photoActions,
                    onSkip = { vm.skipPhoto(); photoStep = false },
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                )
                else -> FormContent(
                    form = form,
                    labelImage = labelImage,
                    ocrMessage = ocrMessage,
                    saveState = saveState,
                    showErrors = showErrors,
                    saveLabel = stringResource(
                        if ((mode as? CustomFoodMode.New)?.forMeal == true) R.string.custom_food_save_and_add
                        else R.string.action_save
                    ),
                    onField = vm::onField,
                    onThumbnailClick = { showZoom = true },
                    onSave = vm::save,
                    onOpenExisting = { id ->
                        clearLabelPhotos(context)
                        onOpenExisting(id)
                    },
                )
            }
        }
    }

    if (showLabelDialog) {
        Dialog(onDismissRequest = { if (!recognizing) showLabelDialog = false }) {
            PhotoStep(
                recognizing = recognizing,
                actions = photoActions,
                onSkip = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(GlassColors.backgroundDark)
                    .padding(20.dp),
            )
        }
    }

    if (showZoom && labelImage != null) {
        ZoomDialog(imageUri = labelImage!!, onDismiss = { showZoom = false })
    }

    if (showDeleteDialog && mode is CustomFoodMode.Edit) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            text = { Text(stringResource(R.string.custom_food_delete_confirm), color = GlassColors.textPrimary) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    vm.delete(mode.id)
                    onDone()
                }) {
                    Text(stringResource(R.string.action_delete), color = ErrorRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel), color = GlassColors.textSecondary)
                }
            },
            containerColor = GlassColors.cardBackground,
            shape = RoundedCornerShape(20.dp),
        )
    }
}

/** Deletes a photo this app took (FileProvider URI) unless it is [keep]; gallery URIs are never touched. */
private fun deleteOwnPhoto(context: Context, uri: String?, keep: Uri?) {
    if (uri == null || uri == keep?.toString()) return
    if (!uri.startsWith("content://${context.packageName}.fileprovider/")) return
    try {
        context.contentResolver.delete(Uri.parse(uri), null, null)
    } catch (_: Exception) {
        // best effort; the whole folder is cleared when the flow ends
    }
}

private class PhotoActions(val onCamera: () -> Unit, val onGallery: () -> Unit)

@Composable
private fun Header(
    title: String,
    onBack: () -> Unit,
    showActions: Boolean,
    onFillFromLabel: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleButton(Icons.Rounded.ArrowBackIosNew, stringResource(R.string.action_back), GlassColors.textSecondary, onBack)
        Spacer(Modifier.width(14.dp))
        Text(
            title,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = GlassColors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (showActions) {
            CircleButton(
                Icons.Rounded.DocumentScanner,
                stringResource(R.string.custom_food_fill_from_label),
                GlassColors.accentOrange,
                onFillFromLabel,
            )
            Spacer(Modifier.width(10.dp))
            CircleButton(Icons.Rounded.DeleteOutline, stringResource(R.string.action_delete), ErrorRed, onDelete)
        }
    }
}

@Composable
private fun CircleButton(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(GlassColors.cardBackground)
            .border(1.dp, GlassColors.cardBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PhotoStep(
    recognizing: Boolean,
    actions: PhotoActions,
    onSkip: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (recognizing) {
            Spacer(Modifier.height(48.dp))
            CircularProgressIndicator(color = GlassColors.accentGreen)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.custom_food_reading_label), color = GlassColors.textSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(48.dp))
            return@Column
        }
        Text(
            stringResource(R.string.custom_food_photo_hint),
            color = GlassColors.textSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        BigAction(Icons.Rounded.PhotoCamera, stringResource(R.string.custom_food_take_photo), actions.onCamera)
        Spacer(Modifier.height(12.dp))
        BigAction(Icons.Rounded.PhotoLibrary, stringResource(R.string.custom_food_pick_gallery), actions.onGallery)
        if (onSkip != null) {
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.custom_food_fill_manually),
                color = GlassColors.textSecondary,
                fontSize = 14.sp,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onSkip).padding(8.dp),
            )
        }
    }
}

@Composable
private fun BigAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .glassCard(16)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = GlassColors.accentGreen, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = GlassColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FormContent(
    form: CustomFoodForm,
    labelImage: String?,
    ocrMessage: Int?,
    saveState: SaveState,
    showErrors: Boolean,
    saveLabel: String,
    onField: (FormField, String) -> Unit,
    onThumbnailClick: () -> Unit,
    onSave: () -> Unit,
    onOpenExisting: (Int) -> Unit,
) {
    val errors = if (showErrors) form.validate() else emptyMap()
    // The "already taken" error belongs to the code that was saved; editing the code hides it.
    val takenBarcode = remember(saveState) {
        if (saveState is SaveState.BarcodeTaken) form.values[FormField.BARCODE]?.trim() else null
    }
    val taken = (saveState as? SaveState.BarcodeTaken)
        ?.takeIf { takenBarcode != null && form.values[FormField.BARCODE]?.trim() == takenBarcode }

    @Composable
    fun field(f: FormField, label: Int, keyboard: KeyboardType = KeyboardType.Text, modifier: Modifier = Modifier.fillMaxWidth()) {
        FormTextField(
            value = form.values[f].orEmpty(),
            onValueChange = { onField(f, it) },
            label = stringResource(label),
            autoFilled = f in form.autoFilled,
            error = errors[f]?.let { stringResource(it) },
            keyboard = keyboard,
            modifier = modifier,
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding(),
    ) {
        if (labelImage != null) {
            AsyncImage(
                model = Uri.parse(labelImage),
                contentDescription = stringResource(R.string.custom_food_label_photo),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .glassCard(16)
                    .clickable(onClick = onThumbnailClick),
            )
            Spacer(Modifier.height(12.dp))
        }
        if (ocrMessage != null) {
            Text(stringResource(ocrMessage), color = GlassColors.accentOrange, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
        }

        field(FormField.NAME, R.string.custom_food_field_name)
        field(FormField.BRAND, R.string.custom_food_field_brand)
        FormTextField(
            value = form.values[FormField.BARCODE].orEmpty(),
            onValueChange = { onField(FormField.BARCODE, it) },
            label = stringResource(R.string.custom_food_field_barcode),
            autoFilled = false,
            error = errors[FormField.BARCODE]?.let { stringResource(it) }
                ?: taken?.let { stringResource(R.string.custom_food_barcode_taken) },
            keyboard = KeyboardType.Number,
            modifier = Modifier.fillMaxWidth(),
        )
        if (taken != null) {
            Text(
                stringResource(R.string.custom_food_open_existing),
                color = GlassColors.accentGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenExisting(taken.existingId) }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel(stringResource(R.string.custom_food_per_100g))
        field(FormField.KCAL, R.string.custom_food_field_kcal, KeyboardType.Decimal)
        if (form.kcalMismatch) {
            Text(
                stringResource(R.string.custom_food_warning_kcal_mismatch),
                color = GlassColors.accentOrange,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
        }
        field(FormField.PROTEIN, R.string.custom_food_field_protein, KeyboardType.Decimal)
        field(FormField.CARBS, R.string.custom_food_field_carbs, KeyboardType.Decimal)
        field(FormField.FAT, R.string.custom_food_field_fat, KeyboardType.Decimal)

        Spacer(Modifier.height(12.dp))
        SectionLabel(stringResource(R.string.custom_food_serving_section))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            field(FormField.SERVING_UNIT, R.string.custom_food_field_serving_unit, modifier = Modifier.weight(1.4f))
            field(FormField.SERVING_GRAMS, R.string.custom_food_field_serving_grams, KeyboardType.Decimal, Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))
        if (saveState is SaveState.Error) {
            Text(stringResource(saveState.msg), color = ErrorRed, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }
        val saving = saveState is SaveState.Saving
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(GlassColors.accentGreen)
                .clickable(enabled = !saving, onClick = onSave),
            contentAlignment = Alignment.Center,
        ) {
            if (saving) {
                CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            } else {
                Text(saveLabel, color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = GlassColors.textSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    autoFilled: Boolean,
    error: String?,
    keyboard: KeyboardType,
    modifier: Modifier,
) {
    val accent = GlassColors.accentGreen
    val orange = GlassColors.accentOrange
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = when {
            error != null -> { { Text(error, color = ErrorRed) } }
            autoFilled -> { { Text(stringResource(R.string.custom_food_check), color = orange) } }
            else -> null
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent,
            focusedIndicatorColor = if (autoFilled) orange else accent,
            unfocusedIndicatorColor = if (autoFilled) orange else GlassColors.cardBorder,
            focusedTextColor = GlassColors.textPrimary,
            unfocusedTextColor = GlassColors.textPrimary,
            errorTextColor = GlassColors.textPrimary,
            focusedLabelColor = if (autoFilled) orange else accent,
            unfocusedLabelColor = if (autoFilled) orange else GlassColors.textSecondary,
            cursorColor = accent,
        ),
        modifier = modifier.padding(bottom = 4.dp),
    )
}

/** Full-screen label photo with pinch-to-zoom and pan. */
@Composable
private fun ZoomDialog(imageUri: String, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                },
        ) {
            AsyncImage(
                model = Uri.parse(imageUri),
                contentDescription = stringResource(R.string.custom_food_label_photo),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_back), tint = Color.White)
            }
        }
    }
}
