package com.example.foodtracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.foodtracker.R
import com.example.foodtracker.util.AppLocale
import com.example.foodtracker.util.LocalAppLanguage
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightCheckInSheet(
    initialWeightKg: Float? = null,
    onDismiss: () -> Unit,
    onSave: (Float) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember {
        mutableStateOf(initialWeightKg?.let { "%.1f".format(it) } ?: "")
    }
    val parsed = text.replace(",", ".").toFloatOrNull()
    val isValid = parsed != null && parsed in 30f..300f

    var savingWeight by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(savingWeight) {
        savingWeight?.let { w ->
            kotlinx.coroutines.delay(600)
            onSave(w)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = ContentMaxWidth,
    ) {
      AppLocale(LocalAppLanguage.current) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.weight_sheet_title))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("kg") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            if (!isValid && text.isNotBlank()) {
                Text(stringResource(R.string.weight_sheet_range))
            }
            AnimatedActionButton(
                label = stringResource(R.string.action_save),
                successLabel = stringResource(R.string.action_saved),
                phase = if (savingWeight != null) ActionPhase.Success else ActionPhase.Idle,
                enabled = isValid,
                modifier = Modifier.fillMaxWidth(),
                onClick = { parsed?.let { savingWeight = it } },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
      }
    }
}
