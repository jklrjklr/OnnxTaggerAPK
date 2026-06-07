package com.example.onnxtagger.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.onnxtagger.data.model.BatchImageItem
import com.example.onnxtagger.data.model.TagFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagViewerSheet(
    images: List<BatchImageItem>,
    tagFilters: Map<String, TagFilter>,
    tagSeparator: String,
    onTagFilterToggled: (String) -> Unit,
    onClearFilters: () -> Unit,
    onDeleteTag: (String) -> Unit,
    onReplaceTag: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val tagCounts = remember(images, tagSeparator) {
        images
            .flatMap { item -> item.text.split(tagSeparator).map { it.trim() }.filter { it.isNotBlank() } }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
    }

    var searchQuery by remember { mutableStateOf("") }
    var replaceTarget by remember { mutableStateOf<String?>(null) }
    var replaceWith by remember { mutableStateOf("") }

    val visibleTags = remember(tagCounts, searchQuery) {
        if (searchQuery.isBlank()) tagCounts
        else tagCounts.filter { it.key.contains(searchQuery, ignoreCase = true) }
    }

    val activeFilterCount = tagFilters.values.count { it != TagFilter.NONE }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BottomSheetDefaults.DragHandle()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Tag Viewer", style = MaterialTheme.typography.titleMedium)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            "${images.size} image${if (images.size != 1) "s" else ""}  •  ${tagCounts.size} tags",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search tags") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))

            // Deselect All / Actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onClearFilters,
                    enabled = activeFilterCount > 0,
                ) {
                    Text(if (activeFilterCount > 0) "Deselect All ($activeFilterCount)" else "Deselect All")
                }

                var showActionsMenu by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { showActionsMenu = true }) {
                        Text("Actions")
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = showActionsMenu,
                        onDismissRequest = { showActionsMenu = false },
                    ) {
                        val selectedTags = tagFilters.entries
                            .filter { it.value != TagFilter.NONE }
                            .map { it.key }
                        if (selectedTags.isEmpty()) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Select tags first",
                                        color = MaterialTheme.colorScheme.outline,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                },
                                onClick = { showActionsMenu = false },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Delete selected tags from all images") },
                                onClick = {
                                    showActionsMenu = false
                                    selectedTags.forEach { onDeleteTag(it) }
                                    onClearFilters()
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            )
                        }
                    }
                }
            }

            if (tagCounts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No tags yet — run AI or type tags manually",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(visibleTags, key = { it.key }) { (tag, count) ->
                        TagRow(
                            tag = tag,
                            count = count,
                            filterState = tagFilters[tag] ?: TagFilter.NONE,
                            onToggle = { onTagFilterToggled(tag) },
                            onDelete = { onDeleteTag(tag) },
                            onReplace = { replaceTarget = tag; replaceWith = tag },
                        )
                    }
                }
            }
        }
    }

    // Replace dialog
    val target = replaceTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { replaceTarget = null },
            title = { Text("Replace tag") },
            text = {
                Column {
                    Text(
                        "Replace \"$target\" with:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = replaceWith,
                        onValueChange = { replaceWith = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("New tag") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (replaceWith.isNotBlank()) onReplaceTag(target, replaceWith)
                        replaceTarget = null
                    },
                    enabled = replaceWith.isNotBlank(),
                ) { Text("Replace all") }
            },
            dismissButton = {
                TextButton(onClick = { replaceTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun TagRow(
    tag: String,
    count: Int,
    filterState: TagFilter,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onReplace: () -> Unit,
) {
    val chipColor = when (filterState) {
        TagFilter.NONE -> MaterialTheme.colorScheme.surfaceVariant
        TagFilter.INCLUDED -> MaterialTheme.colorScheme.primaryContainer
        TagFilter.EXCLUDED -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (filterState) {
        TagFilter.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
        TagFilter.INCLUDED -> MaterialTheme.colorScheme.onPrimaryContainer
        TagFilter.EXCLUDED -> MaterialTheme.colorScheme.onErrorContainer
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            onClick = onToggle,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(20.dp),
            color = chipColor,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when (filterState) {
                    TagFilter.INCLUDED -> Icon(
                        Icons.Default.Check,
                        contentDescription = "Included",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                    )
                    TagFilter.EXCLUDED -> Icon(
                        Icons.Default.Close,
                        contentDescription = "Excluded",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(15.dp),
                    )
                    TagFilter.NONE -> Spacer(Modifier.size(0.dp))
                }
                Text(
                    tag,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                ) {
                    Text(
                        "$count",
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                    )
                }
            }
        }

        // Per-tag actions
        var showMenu by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Actions for $tag",
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Replace in all images") },
                    onClick = { showMenu = false; onReplace() },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                )
                DropdownMenuItem(
                    text = { Text("Delete from all images") },
                    onClick = { showMenu = false; onDelete() },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                )
            }
        }
    }
}
