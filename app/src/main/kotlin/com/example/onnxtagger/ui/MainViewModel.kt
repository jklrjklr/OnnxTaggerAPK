package com.example.onnxtagger.ui

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.onnxtagger.OnnxTaggerApp
import com.example.onnxtagger.data.model.*
import com.example.onnxtagger.data.repository.BatchSession
import com.example.onnxtagger.inference.InferenceDispatchers
import com.example.onnxtagger.util.FileExporter
import com.example.onnxtagger.util.OutputFormatter
import com.example.onnxtagger.util.SafUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.UUID

data class MainUiState(
    val selectedImages: List<BatchImageItem> = emptyList(),
    val isRunning: Boolean = false,
    val batchProgress: BatchProgress? = null,
    val outputText: String = "",
    val previewItem: BatchImageItem? = null,
    val showIgnoreWarning: Boolean = false,
    val detectedSizeSnackbar: String? = null,
    val saveResultSnackbar: String? = null,
    val error: String? = null,
    val showSettings: Boolean = false,
    val showModelManager: Boolean = false,
    val showProfileManager: Boolean = false,
    val recentSessions: List<BatchSession> = emptyList(),
    val resumableSessions: List<BatchSession> = emptyList(),
)

data class BatchProgress(val current: Int, val total: Int)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as OnnxTaggerApp

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    // FIX-9: Channel.CONFLATED survives rotation without replaying stale events
    private val _saveDocumentEvent = Channel<String>(Channel.CONFLATED)
    val saveDocumentEvent: Flow<String> = _saveDocumentEvent.receiveAsFlow()

    private var batchJob: Job? = null
    private var currentSessionId: String = UUID.randomUUID().toString()
    private var labelCache = mutableMapOf<String, List<String>>()

    companion object {
        const val MAX_QUEUE_SIZE = 200
    }

    init {
        viewModelScope.launch {
            app.settingsRepository.settingsFlow.collect { s ->
                _settings.value = s
                recomputeIgnoreWarning()
            }
        }
        viewModelScope.launch {
            app.batchSessionRepository.recentFlow.collect { sessions ->
                _uiState.update { it.copy(recentSessions = sessions) }
            }
        }
        viewModelScope.launch {
            app.batchSessionRepository.resumableFlow.collect { sessions ->
                _uiState.update { it.copy(resumableSessions = sessions) }
            }
        }
    }

    // FIX-7: enforces MAX_QUEUE_SIZE, skipping excess rather than appending
    fun onImagesSelected(uris: List<Uri>) {
        val current = _uiState.value.selectedImages
        val available = MAX_QUEUE_SIZE - current.size
        if (available <= 0) {
            _uiState.update { it.copy(error = "Queue is full (max $MAX_QUEUE_SIZE images)") }
            return
        }
        val admitted = uris.take(available)
        val skipped = uris.size - admitted.size
        if (skipped > 0) {
            _uiState.update { it.copy(error = "Queue capped at $MAX_QUEUE_SIZE. $skipped image(s) skipped.") }
        }
        val newItems = admitted.map { uri ->
            BatchImageItem(uri = uri, displayName = uri.lastPathSegment ?: uri.toString())
        }
        val combined = (current + newItems).distinctBy { it.uri }
        _uiState.update { it.copy(selectedImages = combined.take(MAX_QUEUE_SIZE)) }
    }

    fun onRunClicked() {
        val images = _uiState.value.selectedImages
        if (images.isEmpty()) return
        val settings = _settings.value
        currentSessionId = UUID.randomUUID().toString()
        _uiState.update { it.copy(isRunning = true, error = null, showIgnoreWarning = false) }

        batchJob = viewModelScope.launch {
            val existingBefore = _uiState.value.outputText
            val perImageOutputs = mutableListOf<String>()

            images.forEachIndexed { i, item ->
                if (item.status == BatchItemStatus.DONE) {
                    perImageOutputs.add(resultToText(item.result, settings))
                    return@forEachIndexed
                }

                updateItemStatus(i, BatchItemStatus.PROCESSING)
                _uiState.update { it.copy(batchProgress = BatchProgress(i + 1, images.size)) }
                persistQueueSnapshot(isComplete = false)

                val result = when (settings.activeMode) {
                    InferenceMode.TAG -> {
                        val labels = getCachedLabels(settings.tagModelConfig)
                        app.tagEngine.run(getApplication(), item.uri, labels, settings, settings.tagModelConfig)
                    }
                    InferenceMode.CAPTION ->
                        app.captionEngine.run(getApplication(), item.uri, settings, settings.captionModelConfig)
                }

                val itemText = resultToText(result, settings)
                perImageOutputs.add(itemText)
                updateItemResult(i, item, result)
            }

            val batchOutput = perImageOutputs.filter { it.isNotBlank() }.joinToString(settings.batchItemSeparator)
            val finalOutput = OutputFormatter.apply(
                newContent = batchOutput,
                existingAnchor = existingBefore,
                prepend = settings.prependText,
                append = settings.appendText,
                actOnExisting = settings.actOnExisting,
                separator = settings.batchItemSeparator,
            )
            _uiState.update { it.copy(outputText = finalOutput, isRunning = false, batchProgress = null) }
            recomputeIgnoreWarning()
            persistQueueSnapshot(isComplete = true)
        }
    }

    fun onCancelBatch() {
        batchJob?.cancel()
        viewModelScope.launch {
            persistQueueSnapshot(isComplete = false)
        }
        _uiState.update { it.copy(isRunning = false, batchProgress = null) }
        recomputeIgnoreWarning()
    }

    fun onResumeBatch(session: BatchSession) {
        val restored = app.batchSessionRepository.restoreQueueItems(session.queueItems)
        currentSessionId = session.id
        _uiState.update { it.copy(selectedImages = restored) }
        onRunClicked()
    }

    fun onTagModelPicked(modelUri: Uri, labelsUri: Uri) {
        if (!app.modelRepository.persistUri(getApplication(), modelUri) ||
            !app.modelRepository.persistUri(getApplication(), labelsUri)) {
            _uiState.update { it.copy(error = "This file picker is not supported. Use the system Files app to pick model files.") }
            return
        }
        val config = _settings.value.tagModelConfig.copy(
            modelUriString = modelUri.toString(),
            labelsUriString = labelsUri.toString(),
        )
        onSettingsChanged(_settings.value.copy(tagModelConfig = config))
        labelCache.remove(config.labelsUriString)
        viewModelScope.launch {
            app.sessionManager.closeTagSession()
            runCatching {
                val session = app.sessionManager.getOrLoadTagSession(config)
                val info = app.sessionManager.getSessionInfo(session)
                val size = info.inferInputSize()
                if (size != null) {
                    val autoConfig = config.copy(inputWidth = size.first, inputHeight = size.second)
                    onSettingsChanged(_settings.value.copy(tagModelConfig = autoConfig))
                    _uiState.update { it.copy(detectedSizeSnackbar = "Auto-detected: ${size.first}×${size.second}") }
                }
            }
        }
    }

    fun onCaptionModelPicked(modelUri: Uri, tokenizerUri: Uri) {
        if (!app.modelRepository.persistUri(getApplication(), modelUri) ||
            !app.modelRepository.persistUri(getApplication(), tokenizerUri)) {
            _uiState.update { it.copy(error = "This file picker is not supported. Use the system Files app to pick model files.") }
            return
        }
        val config = _settings.value.captionModelConfig.copy(
            modelUriString = modelUri.toString(),
            tokenizerUriString = tokenizerUri.toString(),
        )
        onSettingsChanged(_settings.value.copy(captionModelConfig = config))
    }

    fun onSettingsChanged(newSettings: AppSettings) {
        _settings.value = newSettings
        viewModelScope.launch { app.settingsRepository.save(newSettings) }
        recomputeIgnoreWarning()
    }

    fun onReorderImages(from: Int, to: Int) {
        val list = _uiState.value.selectedImages.toMutableList()
        if (from < 0 || to < 0 || from >= list.size || to >= list.size) return
        val item = list.removeAt(from)
        list.add(to, item)
        _uiState.update { it.copy(selectedImages = list) }
    }

    fun onRemoveImage(index: Int) {
        val list = _uiState.value.selectedImages.toMutableList()
        if (index < 0 || index >= list.size) return
        list.removeAt(index)
        _uiState.update { it.copy(selectedImages = list) }
    }

    fun onToggleExpanded(index: Int) {
        val list = _uiState.value.selectedImages.toMutableList()
        if (index < 0 || index >= list.size) return
        list[index] = list[index].copy(isExpanded = !list[index].isExpanded)
        _uiState.update { it.copy(selectedImages = list) }
    }

    fun onPreviewImage(item: BatchImageItem) = _uiState.update { it.copy(previewItem = item) }
    fun onDismissPreview() = _uiState.update { it.copy(previewItem = null) }

    fun onOutputTextChanged(text: String) {
        _uiState.update { it.copy(outputText = text) }
        recomputeIgnoreWarning()
    }

    fun onClearAll() = _uiState.update { it.copy(selectedImages = emptyList()) }

    fun onSavePerImageTapped() {
        val settings = _settings.value
        val items = _uiState.value.selectedImages
            .filter { it.status == BatchItemStatus.DONE }
            .map { item -> item.displayName to resultToText(item.result, settings) }
        if (items.isEmpty()) return
        viewModelScope.launch {
            val result = FileExporter.exportPerImage(getApplication(), items)
            val msg = result.fold(
                { "$it file(s) saved to Downloads" },
                { "Save failed: ${it.message}" },
            )
            _uiState.update { it.copy(saveResultSnackbar = msg) }
        }
    }

    fun onSaveTapped() {
        val content = _uiState.value.outputText
        if (content.isBlank()) return
        viewModelScope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val result = FileExporter.export(
                    getApplication(), content, "batch_tags_${System.currentTimeMillis()}.txt"
                )
                val msg = result.fold({ "Saved to Downloads" }, { "Save failed: ${it.message}" })
                _uiState.update { it.copy(saveResultSnackbar = msg) }
            } else {
                // FIX-9: Channel.CONFLATED fires exactly once, survives rotation
                _saveDocumentEvent.send(content)
            }
        }
    }

    fun onSaveDocumentResult(uri: Uri?) {
        if (uri == null) return
        val content = _uiState.value.outputText
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri)!!.use {
                    it.write(content.toByteArray(Charsets.UTF_8))
                }
                _uiState.update { it.copy(saveResultSnackbar = "Saved") }
            }.onFailure {
                _uiState.update { s -> s.copy(saveResultSnackbar = "Save failed: ${it.message}") }
            }
        }
    }

    fun onLoadProfile(profile: com.example.onnxtagger.data.model.ModelProfile) {
        onSettingsChanged(
            _settings.value.copy(
                temperature = profile.temperature,
                maxTags = profile.maxTags,
                maxTokens = profile.maxTokens,
                tagSeparator = profile.tagSeparator,
                tagModelConfig = profile.tagModelConfig,
                captionModelConfig = profile.captionModelConfig,
                activeProfileId = profile.id,
            )
        )
    }

    fun onSaveProfile(name: String) {
        val s = _settings.value
        val profile = com.example.onnxtagger.data.model.ModelProfile(
            name = name,
            tagModelConfig = s.tagModelConfig,
            captionModelConfig = s.captionModelConfig,
            temperature = s.temperature,
            maxTags = s.maxTags,
            maxTokens = s.maxTokens,
            tagSeparator = s.tagSeparator,
        )
        viewModelScope.launch { app.profileRepository.saveProfile(profile) }
    }

    fun onDeleteProfile(id: String) {
        viewModelScope.launch { app.profileRepository.deleteProfile(id) }
    }

    fun onDeleteSession(id: String) {
        viewModelScope.launch { app.batchSessionRepository.delete(id) }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
    fun dismissDetectedSizeSnackbar() = _uiState.update { it.copy(detectedSizeSnackbar = null) }
    fun dismissSaveSnackbar() = _uiState.update { it.copy(saveResultSnackbar = null) }

    fun toggleSettings() = _uiState.update { it.copy(showSettings = !it.showSettings) }
    fun toggleModelManager() = _uiState.update { it.copy(showModelManager = !it.showModelManager) }
    fun toggleProfileManager() = _uiState.update { it.copy(showProfileManager = !it.showProfileManager) }

    // FIX-3: cancel then join before closing dispatcher to avoid RejectedExecutionException
    override fun onCleared() {
        viewModelScope.launch {
            batchJob?.cancelAndJoin()
            InferenceDispatchers.close()
        }
        app.sessionManager.closeAll()
        super.onCleared()
    }

    private fun recomputeIgnoreWarning() {
        _uiState.update { it.copy(
            showIgnoreWarning = _settings.value.actOnExisting == ActOnExisting.IGNORE
                && _uiState.value.outputText.isNotBlank()
        )}
    }

    private suspend fun getCachedLabels(config: ModelConfig): List<String> {
        if (config.labelsUriString.isBlank()) return emptyList()
        return labelCache.getOrPut(config.labelsUriString) {
            app.tagEngine.loadLabels(getApplication(), Uri.parse(config.labelsUriString))
        }
    }

    private fun resultToText(result: InferenceResult?, settings: AppSettings): String =
        when (result) {
            is InferenceResult.TagResult -> result.tags.joinToString(settings.tagSeparator) { it.label }
            is InferenceResult.CaptionResult -> result.text
            is InferenceResult.Failure -> ""
            null -> ""
        }

    private fun updateItemStatus(index: Int, status: BatchItemStatus) {
        _uiState.update { state ->
            val list = state.selectedImages.toMutableList()
            if (index < list.size) list[index] = list[index].copy(status = status)
            state.copy(selectedImages = list)
        }
    }

    private fun updateItemResult(index: Int, original: BatchImageItem, result: InferenceResult) {
        _uiState.update { state ->
            val list = state.selectedImages.toMutableList()
            if (index < list.size) {
                list[index] = original.copy(
                    status = if (result is InferenceResult.Failure) BatchItemStatus.FAILED else BatchItemStatus.DONE,
                    result = result,
                )
            }
            state.copy(selectedImages = list)
        }
    }

    private suspend fun persistQueueSnapshot(isComplete: Boolean) {
        val images = _uiState.value.selectedImages
        val settings = _settings.value
        val queueJson = app.batchSessionRepository.buildQueueJson(images) { resultToText(it.result, settings) }
        val resultsJson = app.batchSessionRepository.buildResultsJson(images, settings.activeMode) { resultToText(it.result, settings) }
        val session = BatchSession(
            id = currentSessionId,
            timestamp = System.currentTimeMillis(),
            mode = settings.activeMode,
            imageCount = images.size,
            successCount = images.count { it.status == BatchItemStatus.DONE },
            isComplete = isComplete,
            results = emptyList(),
            queueItems = emptyList(),
        )
        val entity = com.example.onnxtagger.data.db.BatchSessionEntity(
            id = session.id,
            timestamp = session.timestamp,
            mode = session.mode.name,
            imageCount = session.imageCount,
            successCount = session.successCount,
            isComplete = isComplete,
            resultsJson = resultsJson,
            queueStateJson = queueJson,
        )
        app.database.batchSessionDao().insert(entity)
    }
}
