package com.example.foodtracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Check-in greutate")
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("kg") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            if (!isValid && text.isNotBlank()) {
                Text("Introdu o greutate între 30 și 300 kg")
            }
            Button(
                onClick = { parsed?.let { onSave(it) } },
                enabled = isValid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Salvează")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
