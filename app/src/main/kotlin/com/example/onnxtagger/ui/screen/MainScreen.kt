package com.example.onnxtagger.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.onnxtagger.data.model.BatchImageItem
import com.example.onnxtagger.data.model.BatchItemStatus
import com.example.onnxtagger.data.model.InferenceMode
import com.example.onnxtagger.ui.BatchProgress
import com.example.onnxtagger.ui.MainViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var showHistory by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) vm.onImagesSelected(uris) }

    val saveDirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) vm.onSaveDirPicked(uri) }

    LaunchedEffect(Unit) {
        vm.pickSaveDirEvent.collect { saveDirLauncher.launch(null) }
    }

    val pagerState = rememberPagerState { uiState.selectedImages.size }

    // Clamp page index when images are removed
    LaunchedEffect(uiState.selectedImages.size) {
        if (uiState.selectedImages.isNotEmpty() &&
            pagerState.currentPage >= uiState.selectedImages.size) {
            pagerState.animateScrollToPage(uiState.selectedImages.size - 1)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.saveResultSnackbar) {
        uiState.saveResultSnackbar?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            vm.dismissSaveSnackbar()
        }
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            vm.dismissError()
        }
    }
    LaunchedEffect(uiState.detectedSizeSnackbar) {
        uiState.detectedSizeSnackbar?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            vm.dismissDetectedSizeSnackbar()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            TopActionBar(
                isRunning = uiState.isRunning,
                hasImages = uiState.selectedImages.isNotEmpty(),
                hasText = uiState.selectedImages.any { it.text.isNotBlank() },
                activeMode = settings.activeMode,
                batchProgress = uiState.batchProgress,
                onAddImages = { imagePickerLauncher.launch(arrayOf("image/*")) },
                onRun = vm::onRunClicked,
                onReset = vm::onResetTapped,
                onSaveTxts = vm::onSavePerImageTapped,
                onModeToggle = { vm.onSettingsChanged(settings.copy(activeMode = it)) },
                onSettings = vm::toggleSettings,
                onHistory = { showHistory = true },
            )

            if (uiState.selectedImages.isEmpty()) {
                EmptyImageState(modifier = Modifier.fillMaxSize())
            } else {
                if (uiState.selectedImages.size > 1) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${uiState.selectedImages.size}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = { i -> uiState.selectedImages.getOrNull(i)?.uri?.toString() ?: i },
                ) { page ->
                    val item = uiState.selectedImages.getOrNull(page) ?: return@HorizontalPager
                    ImageEditPage(
                        item = item,
                        activeMode = settings.activeMode,
                        onTextChanged = { vm.onTextChanged(page, it) },
                        onRemove = { vm.onRemoveImage(page) },
                        onPreview = { vm.onPreviewImage(item) },
                    )
                }
            }
        }

        if (uiState.showSettings) {
            SettingsBottomSheet(
                settings = settings,
                onDismiss = vm::toggleSettings,
                onSettingsChanged = vm::onSettingsChanged,
                onManageModels = { vm.toggleSettings(); vm.toggleModelManager() },
                onManageProfiles = { vm.toggleSettings(); vm.toggleProfileManager() },
            )
        }
        if (uiState.showModelManager) {
            ModelManagerScreen(
                settings = settings,
                onTagModelPicked = vm::onTagModelPicked,
                onCaptionModelPicked = vm::onCaptionModelPicked,
                onSettingsChanged = vm::onSettingsChanged,
                onDismiss = vm::toggleModelManager,
            )
        }
        if (uiState.showProfileManager) {
            ProfileManagerScreen(
                activeProfileId = settings.activeProfileId,
                onLoadProfile = vm::onLoadProfile,
                onSaveProfile = vm::onSaveProfile,
                onDeleteProfile = vm::onDeleteProfile,
                onDismiss = vm::toggleProfileManager,
            )
        }
        if (showHistory) {
            HistoryBottomSheet(
                sessions = uiState.recentSessions,
                onResume = { session -> showHistory = false; vm.onResumeBatch(session) },
                onDeleteSession = vm::onDeleteSession,
                onDismiss = { showHistory = false },
            )
        }
        uiState.previewItem?.let { item ->
            ImagePreviewScreen(item = item, onDismiss = vm::onDismissPreview)
        }
    }
}

@Composable
private fun TopActionBar(
    isRunning: Boolean,
    hasImages: Boolean,
    hasText: Boolean,
    activeMode: InferenceMode,
    batchProgress: BatchProgress?,
    onAddImages: () -> Unit,
    onRun: () -> Unit,
    onReset: () -> Unit,
    onSaveTxts: () -> Unit,
    onModeToggle: (InferenceMode) -> Unit,
    onSettings: () -> Unit,
    onHistory: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Add images
            FilledTonalIconButton(onClick = onAddImages, enabled = !isRunning) {
                Icon(Icons.Default.Add, contentDescription = "Add images")
            }
            // Run AI
            FilledTonalIconButton(onClick = onRun, enabled = hasImages && !isRunning) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Run AI")
            }
            // Reset
            FilledTonalIconButton(onClick = onReset, enabled = hasImages && !isRunning) {
                Icon(Icons.Default.ClearAll, contentDescription = "Reset")
            }
            // Save TXTs
            FilledTonalIconButton(onClick = onSaveTxts, enabled = hasText && !isRunning) {
                Icon(Icons.Default.FolderOpen, contentDescription = "Save TXT files")
            }

            Spacer(Modifier.weight(1f))

            // Tag / Caption toggle
            InferenceMode.entries.forEach { mode ->
                FilterChip(
                    selected = activeMode == mode,
                    onClick = { onModeToggle(mode) },
                    label = {
                        Text(
                            when (mode) {
                                InferenceMode.TAG -> "Tag"
                                InferenceMode.CAPTION -> "Cap"
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    modifier = Modifier.height(32.dp),
                )
            }

            // History
            IconButton(onClick = onHistory) {
                Icon(Icons.Default.History, contentDescription = "History")
            }
            // Settings
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }

        // Running indicator row
        if (isRunning && batchProgress != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LinearProgressIndicator(
                    progress = { batchProgress.current.toFloat() / batchProgress.total },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${batchProgress.current}/${batchProgress.total}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun EmptyImageState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.PhotoLibrary,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.outlineVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Tap + to add images",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.outline,
            )
            Text(
                "Swipe between images after adding",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

@Composable
private fun ImageEditPage(
    item: BatchImageItem,
    activeMode: InferenceMode,
    onTextChanged: (String) -> Unit,
    onRemove: () -> Unit,
    onPreview: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Image area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            AsyncImage(
                model = item.uri,
                contentDescription = item.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )

            // Status badge (top-left)
            if (item.status != BatchItemStatus.PENDING) {
                Box(modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                    StatusBadge(status = item.status)
                }
            }

            // Remove button (top-right)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
            ) {
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Full-screen preview (bottom-right)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
            ) {
                IconButton(onClick = onPreview, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.OpenInFull,
                        contentDescription = "Preview",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Processing overlay
            if (item.status == BatchItemStatus.PROCESSING) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(48.dp),
                    strokeWidth = 4.dp,
                )
            }
        }

        // Text input
        OutlinedTextField(
            value = item.text,
            onValueChange = onTextChanged,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp, max = 180.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            label = {
                Text(if (activeMode == InferenceMode.TAG) "Tags" else "Caption")
            },
            placeholder = {
                Text(
                    if (activeMode == InferenceMode.TAG)
                        "Run AI or type tags manually…"
                    else
                        "Run AI or type caption manually…"
                )
            },
            maxLines = 6,
        )
    }
}
