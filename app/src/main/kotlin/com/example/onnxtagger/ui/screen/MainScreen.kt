package com.example.onnxtagger.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.onnxtagger.data.model.ActOnExisting
import com.example.onnxtagger.data.model.InferenceMode
import com.example.onnxtagger.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var showHistory by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) vm.onImagesSelected(uris) }

    val createDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> vm.onSaveDocumentResult(uri) }

    // FIX-9: collect save event with LaunchedEffect so it doesn't replay on recomposition
    LaunchedEffect(Unit) {
        vm.saveDocumentEvent.collect { _ ->
            createDocLauncher.launch("batch_tags_${System.currentTimeMillis()}.txt")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ONNX Tagger") },
                actions = {
                    // Mode toggle
                    ModeToggleButton(
                        current = settings.activeMode,
                        onToggle = { vm.onSettingsChanged(settings.copy(activeMode = it)) }
                    )
                    if (uiState.selectedImages.isNotEmpty()) {
                        IconButton(onClick = vm::onClearAll, enabled = !uiState.isRunning) {
                            Icon(Icons.Default.ClearAll, contentDescription = "Clear all images")
                        }
                    }
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = vm::toggleSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        },
        snackbarHost = {
            // Snackbar driven by uiState.error
            uiState.error?.let { msg ->
                LaunchedEffect(msg) {
                    // In a real impl, use SnackbarHostState; simplified here
                    vm.dismissError()
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // IGNORE warning banner
            if (uiState.showIgnoreWarning) {
                IgnoreWarningBanner()
            }

            // Image thumbnail strip
            ImagePickerArea(
                images = uiState.selectedImages,
                isRunning = uiState.isRunning,
                onAddImages = { imagePickerLauncher.launch(arrayOf("image/*")) },
                onRemoveImage = vm::onRemoveImage,
                onReorder = vm::onReorderImages,
                onPreview = vm::onPreviewImage,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            )

            // Per-image expandable result list
            BatchResultList(
                images = uiState.selectedImages,
                onToggleExpanded = vm::onToggleExpanded,
                onPreview = vm::onPreviewImage,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            // Progress + combined output
            OutputArea(
                outputText = uiState.outputText,
                batchProgress = uiState.batchProgress,
                isRunning = uiState.isRunning,
                canRun = uiState.selectedImages.isNotEmpty(),
                onOutputTextChanged = vm::onOutputTextChanged,
                onRunClicked = vm::onRunClicked,
                onCancelClicked = vm::onCancelBatch,
                onCopyClicked = { /* copy handled by SelectionContainer or clipboard */ },
                onSaveClicked = vm::onSaveTapped,
            )
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

        // Snackbars for auto-detect and save
        uiState.detectedSizeSnackbar?.let { msg ->
            LaunchedEffect(msg) { vm.dismissDetectedSizeSnackbar() }
        }
        uiState.saveResultSnackbar?.let { msg ->
            LaunchedEffect(msg) { vm.dismissSaveSnackbar() }
        }
    }
}

@Composable
private fun IgnoreWarningBanner() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Warning, contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                "Output field has existing content — it will be replaced by new results",
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ModeToggleButton(current: InferenceMode, onToggle: (InferenceMode) -> Unit) {
    Row {
        InferenceMode.entries.forEach { mode ->
            FilterChip(
                selected = current == mode,
                onClick = { onToggle(mode) },
                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}
