package com.example.onnxtagger.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.onnxtagger.ui.BatchProgress

@Composable
fun OutputArea(
    outputText: String,
    batchProgress: BatchProgress?,
    isRunning: Boolean,
    canRun: Boolean,
    onOutputTextChanged: (String) -> Unit,
    onRunClicked: () -> Unit,
    onCancelClicked: () -> Unit,
    onCopyClicked: () -> Unit,
    onSaveClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(8.dp)) {
        // Progress row
        if (batchProgress != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Processing ${batchProgress.current} / ${batchProgress.total}…",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = onCancelClicked) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Cancel")
                }
            }
            LinearProgressIndicator(
                progress = { batchProgress.current.toFloat() / batchProgress.total },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
        }

        // Run button
        Button(
            onClick = onRunClicked,
            enabled = canRun && !isRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isRunning) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (isRunning) "Running…" else "Run")
        }

        Spacer(Modifier.height(8.dp))

        // Output text (read-only SelectionContainer + editable OutlinedTextField)
        OutlinedTextField(
            value = outputText,
            onValueChange = onOutputTextChanged,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp, max = 200.dp),
            label = { Text("Output") },
            placeholder = { Text("Results will appear here…") },
        )

        if (outputText.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onCopyClicked, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy")
                }
                OutlinedButton(onClick = onSaveClicked, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save")
                }
            }
        }
    }
}
