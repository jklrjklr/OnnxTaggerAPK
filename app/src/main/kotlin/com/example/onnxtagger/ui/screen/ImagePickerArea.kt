package com.example.onnxtagger.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.onnxtagger.data.model.BatchImageItem
import com.example.onnxtagger.data.model.BatchItemStatus
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImagePickerArea(
    images: List<BatchImageItem>,
    isRunning: Boolean,
    onAddImages: () -> Unit,
    onRemoveImage: (Int) -> Unit,
    onReorder: (Int, Int) -> Unit,
    onPreview: (BatchImageItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        onMove = { from, to -> onReorder(from.index - 1, to.index - 1) } // offset for "add" tile
    )

    LazyRow(
        state = lazyListState,
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        item(key = "add") {
            AddImageTile(
                onClick = onAddImages,
                enabled = !isRunning,
            )
        }

        items(images, key = { it.uri.toString() }) { item ->
            val index = images.indexOf(item)
            ReorderableItem(reorderState, key = item.uri.toString()) { isDragging ->
                ThumbnailCard(
                    item = item,
                    isDragging = isDragging,
                    onRemove = { onRemoveImage(index) },
                    onTap = { onPreview(item) },
                    modifier = Modifier
                        .draggableHandle(
                            onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                        )
                        .semantics { contentDescription = "Drag to reorder ${item.displayName}" },
                )
            }
        }
    }
}

@Composable
private fun AddImageTile(onClick: () -> Unit, enabled: Boolean) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Add, contentDescription = "Add images")
            Text("Add", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ThumbnailCard(
    item: BatchImageItem,
    isDragging: Boolean,
    onRemove: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(96.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onTap)
            .background(
                if (isDragging) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface
            ),
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Status badge (top-left)
        StatusBadge(status = item.status, modifier = Modifier.align(Alignment.TopStart).padding(4.dp))

        // Remove button (top-right)
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove ${item.displayName}",
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
fun StatusBadge(status: BatchItemStatus, modifier: Modifier = Modifier) {
    val (icon, desc, color) = when (status) {
        BatchItemStatus.PENDING    -> Triple(null, "Pending", MaterialTheme.colorScheme.outline)
        BatchItemStatus.PROCESSING -> Triple(null, "Processing", MaterialTheme.colorScheme.primary)
        BatchItemStatus.DONE       -> Triple(Icons.Default.Check, "Done", MaterialTheme.colorScheme.primary)
        BatchItemStatus.FAILED     -> Triple(Icons.Default.Error, "Failed", MaterialTheme.colorScheme.error)
    }
    when (status) {
        BatchItemStatus.PROCESSING -> CircularProgressIndicator(
            modifier = modifier.size(16.dp),
            strokeWidth = 2.dp,
        )
        BatchItemStatus.DONE, BatchItemStatus.FAILED ->
            Icon(
                imageVector = icon!!,
                contentDescription = desc,
                tint = color,
                modifier = modifier.size(16.dp),
            )
        else -> {}
    }
}
