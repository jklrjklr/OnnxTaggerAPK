package com.example.onnxtagger.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.onnxtagger.data.model.*

@Composable
fun ModelManagerScreen(
    settings: AppSettings,
    onTagModelPicked: (modelUri: Uri, labelsUri: Uri) -> Unit,
    onCaptionModelPicked: (modelUri: Uri, tokenizerUri: Uri) -> Unit,
    onSettingsChanged: (AppSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Models") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Tag Model", style = MaterialTheme.typography.titleSmall)
                TagModelSlot(
                    config = settings.tagModelConfig,
                    onPick = onTagModelPicked,
                    onConfigChanged = { onSettingsChanged(settings.copy(tagModelConfig = it)) },
                )
                HorizontalDivider()
                Text("Caption Model", style = MaterialTheme.typography.titleSmall)
                CaptionModelSlot(
                    config = settings.captionModelConfig,
                    onPick = onCaptionModelPicked,
                    onConfigChanged = { onSettingsChanged(settings.copy(captionModelConfig = it)) },
                )
            }
        },
    )
}

@Composable
private fun TagModelSlot(
    config: ModelConfig,
    onPick: (Uri, Uri) -> Unit,
    onConfigChanged: (ModelConfig) -> Unit,
) {
    var pendingModel by remember { mutableStateOf<Uri?>(null) }
    var pendingLabels by remember { mutableStateOf<Uri?>(null) }
    var showAdvanced by remember { mutableStateOf(false) }

    val modelLauncher = rememberLauncherForActivityResult(OpenDocument()) { uri -> uri?.let { pendingModel = it } }
    val labelsLauncher = rememberLauncherForActivityResult(OpenDocument()) { uri -> uri?.let { pendingLabels = it } }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FilePickerRow(
            label = "Model (.onnx)",
            currentUri = pendingModel ?: config.modelUriString.toUriOrNull(),
            onClick = { modelLauncher.launch(arrayOf("*/*")) },
        )
        FilePickerRow(
            label = "Labels (.csv / .txt)",
            currentUri = pendingLabels ?: config.labelsUriString.toUriOrNull(),
            onClick = { labelsLauncher.launch(arrayOf("*/*")) },
        )

        if (pendingModel != null && pendingLabels != null) {
            Button(
                onClick = {
                    onPick(pendingModel!!, pendingLabels!!)
                    pendingModel = null
                    pendingLabels = null
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Apply") }
        }

        if (config.modelUriString.isNotBlank()) {
            Text(
                "Input: ${config.inputWidth}×${config.inputHeight}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        TextButton(
            onClick = { showAdvanced = !showAdvanced },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (showAdvanced) "▾ Advanced" else "▸ Advanced")
        }

        if (showAdvanced) {
            AdvancedModelSettings(config = config, onConfigChanged = onConfigChanged)
        }
    }
}

@Composable
private fun CaptionModelSlot(
    config: ModelConfig,
    onPick: (Uri, Uri) -> Unit,
    onConfigChanged: (ModelConfig) -> Unit,
) {
    var pendingModel by remember { mutableStateOf<Uri?>(null) }
    var pendingTokenizer by remember { mutableStateOf<Uri?>(null) }
    var showAdvanced by remember { mutableStateOf(false) }

    val modelLauncher = rememberLauncherForActivityResult(OpenDocument()) { uri -> uri?.let { pendingModel = it } }
    val tokenizerLauncher = rememberLauncherForActivityResult(OpenDocument()) { uri -> uri?.let { pendingTokenizer = it } }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FilePickerRow(
            label = "Model (.onnx)",
            currentUri = pendingModel ?: config.modelUriString.toUriOrNull(),
            onClick = { modelLauncher.launch(arrayOf("*/*")) },
        )
        FilePickerRow(
            label = "Tokenizer (.json)",
            currentUri = pendingTokenizer ?: config.tokenizerUriString.toUriOrNull(),
            onClick = { tokenizerLauncher.launch(arrayOf("*/*")) },
        )

        if (pendingModel != null && pendingTokenizer != null) {
            Button(
                onClick = {
                    onPick(pendingModel!!, pendingTokenizer!!)
                    pendingModel = null
                    pendingTokenizer = null
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Apply") }
        }

        TextButton(
            onClick = { showAdvanced = !showAdvanced },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (showAdvanced) "▾ Advanced" else "▸ Advanced")
        }

        if (showAdvanced) {
            AdvancedModelSettings(config = config, onConfigChanged = onConfigChanged)
        }
    }
}

@Composable
private fun AdvancedModelSettings(
    config: ModelConfig,
    onConfigChanged: (ModelConfig) -> Unit,
) {
    var overrideSize by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.padding(start = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Input layout:", style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TensorLayout.entries.forEach { layout ->
                FilterChip(
                    selected = config.inputLayout == layout,
                    onClick = { onConfigChanged(config.copy(inputLayout = layout)) },
                    label = { Text(layout.name, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }

        Text("Channel order:", style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChannelOrder.entries.forEach { order ->
                FilterChip(
                    selected = config.channelOrder == order,
                    onClick = { onConfigChanged(config.copy(channelOrder = order)) },
                    label = { Text(order.name, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }

        Text("Output activation:", style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutputActivation.entries.forEach { act ->
                FilterChip(
                    selected = config.outputActivation == act,
                    onClick = { onConfigChanged(config.copy(outputActivation = act)) },
                    label = {
                        Text(
                            act.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Use NNAPI (API 28+)", style = MaterialTheme.typography.bodySmall)
            Switch(
                checked = config.useNnapi,
                onCheckedChange = { onConfigChanged(config.copy(useNnapi = it)) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Override input size", style = MaterialTheme.typography.bodySmall)
            Switch(checked = overrideSize, onCheckedChange = { overrideSize = it })
        }

        if (overrideSize) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = config.inputWidth.toString(),
                    onValueChange = { v ->
                        v.toIntOrNull()?.takeIf { it > 0 }?.let { onConfigChanged(config.copy(inputWidth = it)) }
                    },
                    label = { Text("Width") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = config.inputHeight.toString(),
                    onValueChange = { v ->
                        v.toIntOrNull()?.takeIf { it > 0 }?.let { onConfigChanged(config.copy(inputHeight = it)) }
                    },
                    label = { Text("Height") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
        }

        OutlinedTextField(
            value = config.inputNodeName,
            onValueChange = { onConfigChanged(config.copy(inputNodeName = it)) },
            label = { Text("Input node name") },
            placeholder = { Text("(auto-detect)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = config.outputNodeName,
            onValueChange = { onConfigChanged(config.copy(outputNodeName = it)) },
            label = { Text("Output node name") },
            placeholder = { Text("(auto-detect)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }
}

@Composable
private fun FilePickerRow(label: String, currentUri: Uri?, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        val displayText = currentUri?.lastPathSegment
            ?.let { name -> if (name.length > 28) "…${name.takeLast(25)}" else name }
            ?: label
        Text(
            text = displayText,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun String.toUriOrNull(): Uri? =
    if (isBlank()) null else runCatching { Uri.parse(this) }.getOrNull()
