package com.example.onnxtagger.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.onnxtagger.data.model.BatchImageItem
import com.example.onnxtagger.data.model.BatchItemStatus
import com.example.onnxtagger.data.model.InferenceResult

@Composable
fun BatchResultList(
    images: List<BatchImageItem>,
    onToggleExpanded: (Int) -> Unit,
    onPreview: (BatchImageItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (images.isEmpty()) {
        EmptyState(modifier = modifier)
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(images, key = { it.uri.toString() }) { item ->
                val index = images.indexOf(item)
                ExpandableResultCard(
                    item = item,
                    onToggle = { onToggleExpanded(index) },
                    onPreview = { onPreview(item) },
                )
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.ImageSearch,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Pick images to get started",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExpandableResultCard(
    item: BatchImageItem,
    onToggle: () -> Unit,
    onPreview: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusBadge(status = item.status)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(4.dp))
                // Collapsed summary
                if (!item.isExpanded) {
                    Text(
                        text = collapsedSummary(item),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 120.dp),
                    )
                }
                Icon(
                    if (item.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (item.isExpanded) "Collapse" else "Expand",
                )
                IconButton(onClick = onPreview, enabled = item.result != null) {
                    Icon(Icons.Default.OpenInFull, contentDescription = "Preview full image")
                }
            }

            // Expanded body
            if (item.isExpanded) {
                HorizontalDivider()
                Box(modifier = Modifier.padding(12.dp)) {
                    when (val r = item.result) {
                        is InferenceResult.TagResult -> SelectionContainer {
                            Text(r.tags.joinToString(", ") { it.label },
                                style = MaterialTheme.typography.bodySmall)
                        }
                        is InferenceResult.CaptionResult -> SelectionContainer {
                            Text(r.text, style = MaterialTheme.typography.bodySmall)
                        }
                        is InferenceResult.Failure ->
                            Text(
                                "Error: ${r.error.message ?: "Unknown error"}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        null -> Text(
                            "Pending",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

private fun collapsedSummary(item: BatchImageItem): String = when (val r = item.result) {
    is InferenceResult.TagResult -> "${r.tags.size} tags"
    is InferenceResult.CaptionResult -> r.text.take(60).let { if (r.text.length > 60) "$it…" else it }
    is InferenceResult.Failure -> "Failed"
    null -> when (item.status) {
        BatchItemStatus.PROCESSING -> "Processing…"
        else -> "Pending"
    }
}
