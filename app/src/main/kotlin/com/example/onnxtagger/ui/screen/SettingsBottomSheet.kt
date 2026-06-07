package com.example.onnxtagger.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.onnxtagger.data.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onSettingsChanged: (AppSettings) -> Unit,
    onManageModels: () -> Unit,
    onManageProfiles: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            // Model / Profile management — always at the top
            OutlinedButton(onClick = onManageModels, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Manage Models")
            }
            OutlinedButton(onClick = onManageProfiles, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Bookmarks, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Manage Profiles")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            val tempLabel = if (settings.activeMode == InferenceMode.TAG) "Confidence Threshold" else "Temperature"
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(tempLabel, style = MaterialTheme.typography.bodyMedium)
                    Text("%.2f".format(settings.temperature), style = MaterialTheme.typography.bodyMedium)
                }
                Slider(
                    value = settings.temperature,
                    onValueChange = { onSettingsChanged(settings.copy(temperature = it)) },
                    valueRange = 0f..1f,
                    steps = 19, // 20 intervals of 0.05
                )
            }

            // Max tags or max tokens
            if (settings.activeMode == InferenceMode.TAG) {
                IntInputField(
                    label = "Max Tags",
                    value = settings.maxTags,
                    onValueChange = { onSettingsChanged(settings.copy(maxTags = it)) },
                )
            } else {
                IntInputField(
                    label = "Max Tokens",
                    value = settings.maxTokens,
                    onValueChange = { onSettingsChanged(settings.copy(maxTokens = it)) },
                )
            }

            // Prepend / Append
            OutlinedTextField(
                value = settings.prependText,
                onValueChange = { onSettingsChanged(settings.copy(prependText = it)) },
                label = { Text("Prepend to output") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = settings.appendText,
                onValueChange = { onSettingsChanged(settings.copy(appendText = it)) },
                label = { Text("Append to output") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // Act on existing
            Text("When output already has content:", style = MaterialTheme.typography.labelMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ActOnExisting.entries.forEach { option ->
                    FilterChip(
                        selected = settings.actOnExisting == option,
                        onClick = { onSettingsChanged(settings.copy(actOnExisting = option)) },
                        label = {
                            Text(
                                option.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }

            // Batch separator
            OutlinedTextField(
                value = settings.batchItemSeparator,
                onValueChange = { onSettingsChanged(settings.copy(batchItemSeparator = it)) },
                label = { Text("Batch item separator") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // Tag-mode specific
            if (settings.activeMode == InferenceMode.TAG) {
                Text("Tag sort order:", style = MaterialTheme.typography.labelMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TagSortOrder.entries.forEach { order ->
                        FilterChip(
                            selected = settings.tagSortOrder == order,
                            onClick = { onSettingsChanged(settings.copy(tagSortOrder = order)) },
                            label = {
                                Text(
                                    when (order) {
                                        TagSortOrder.CONFIDENCE_DESC -> "Confidence ↓"
                                        TagSortOrder.ALPHABETICAL -> "A–Z"
                                        TagSortOrder.ORIGINAL_INDEX -> "Model order"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }

                OutlinedTextField(
                    value = settings.tagSeparator,
                    onValueChange = { onSettingsChanged(settings.copy(tagSeparator = it)) },
                    label = { Text("Tag separator") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun IntInputField(label: String, value: Int, onValueChange: (Int) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { text -> text.toIntOrNull()?.takeIf { it > 0 }?.let(onValueChange) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}
