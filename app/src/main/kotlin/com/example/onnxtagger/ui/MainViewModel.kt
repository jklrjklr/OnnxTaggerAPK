package com.example.onnxtagger.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.onnxtagger.OnnxTaggerApp
import com.example.onnxtagger.data.model.*
import com.example.onnxtagger.data.repository.BatchSession
import com.example.onnxtagger.inference.InferenceDispatchers
import com.example.onnxtagger.util.FileExporter
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class MainUiState(
    val selectedImages: List<BatchImageItem> = emptyList(),
    val isRunning: Boolean = false,
    val batchProgress: BatchProgress? = null,
    val previewItem: BatchImageItem? = null,
    val detectedSizeSnackbar: String? = null,
    val saveResultSnackbar: String? = null,
    val error: String? = null,
    val showSettings: Boolean = false,
    val showModelManager: Boolean = false,
    val showProfileManager: Boolean = false,
    val recentSessions: List<BatchSession> = emptyList(),
    val resumableSessions: List<BatchSession> = emptyList(),
    val tagFilters: Map<String, TagFilter> = emptyMap(),
)

data class BatchProgress(val current: Int, val total: Int)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as OnnxTaggerApp

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _pickSaveDirEvent = Channel<Unit>(Channel.CONFLATED)
    val pickSaveDirEvent: Flow<Unit> = _pickSaveDirEvent.receiveAsFlow()

    private var batchJob: Job? = null
    private var currentSessionId: String = UUID.randomUUID().toString()
    private var labelCache = mutableMapOf<String, List<String>>()

    companion object {
        const val MAX_QUEUE_SIZE = 200
    }

    init {
        viewModelScope.launch {
            app.settingsRepository.settingsFlow.collect { s -> _settings.value = s }
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
        _uiState.update { it.copy(isRunning = true, error = null) }

        batchJob = viewModelScope.launch {
            images.forEachIndexed { i, item ->
                if (item.status == BatchItemStatus.DONE) return@forEachIndexed

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
                updateItemResult(i, item, result, settings)
            }
            _uiState.update { it.copy(isRunning = false, batchProgress = null) }
            persistQueueSnapshot(isComplete = true)
        }
    }

    fun onCancelBatch() {
        batchJob?.cancel()
        viewModelScope.launch { persistQueueSnapshot(isComplete = false) }
        _uiState.update { it.copy(isRunning = false, batchProgress = null) }
    }

    fun onResumeBatch(session: BatchSession) {
        val restored = app.batchSessionRepository.restoreQueueItems(session.queueItems)
        currentSessionId = session.id
        _uiState.update { it.copy(selectedImages = restored) }
        onRunClicked()
    }

    fun onTextChanged(index: Int, text: String) {
        _uiState.update { state ->
            val list = state.selectedImages.toMutableList()
            if (index in list.indices) list[index] = list[index].copy(text = text)
            state.copy(selectedImages = list)
        }
    }

    fun onResetTapped() {
        batchJob?.cancel()
        _uiState.update { it.copy(selectedImages = emptyList(), isRunning = false, batchProgress = null) }
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

    fun onPreviewImage(item: BatchImageItem) = _uiState.update { it.copy(previewItem = item) }
    fun onDismissPreview() = _uiState.update { it.copy(previewItem = null) }

    fun onClearAll() = _uiState.update { it.copy(selectedImages = emptyList()) }

    fun onTagFilterToggled(tag: String) {
        val next = when (_uiState.value.tagFilters[tag] ?: TagFilter.NONE) {
            TagFilter.NONE -> TagFilter.INCLUDED
            TagFilter.INCLUDED -> TagFilter.EXCLUDED
            TagFilter.EXCLUDED -> TagFilter.NONE
        }
        val updated = _uiState.value.tagFilters.toMutableMap()
        if (next == TagFilter.NONE) updated.remove(tag) else updated[tag] = next
        _uiState.update { it.copy(tagFilters = updated) }
    }

    fun onClearTagFilters() = _uiState.update { it.copy(tagFilters = emptyMap()) }

    fun onDeleteTag(tag: String) {
        val sep = _settings.value.tagSeparator
        _uiState.update { state ->
            state.copy(selectedImages = state.selectedImages.map { item ->
                if (item.text.isBlank()) item
                else item.copy(text = item.text.split(sep)
                    .map { it.trim() }.filter { it.isNotBlank() && it != tag }
                    .joinToString(sep))
            })
        }
    }

    fun onReplaceTag(oldTag: String, newTag: String) {
        val trimmed = newTag.trim()
        if (trimmed.isBlank()) return
        val sep = _settings.value.tagSeparator
        _uiState.update { state ->
            state.copy(selectedImages = state.selectedImages.map { item ->
                if (item.text.isBlank()) item
                else item.copy(text = item.text.split(sep)
                    .map { it.trim() }.filter { it.isNotBlank() }
                    .map { if (it == oldTag) trimmed else it }
                    .joinToString(sep))
            })
        }
    }

    fun onSavePerImageTapped() {
        viewModelScope.launch { _pickSaveDirEvent.send(Unit) }
    }

    fun onSaveDirPicked(uri: Uri) {
        doSavePerImage(uri)
    }

    private fun doSavePerImage(dirUri: Uri) {
        val items = _uiState.value.selectedImages
            .filter { it.text.isNotBlank() }
            .map { item -> item.displayName to item.text }
        if (items.isEmpty()) return
        viewModelScope.launch {
            val result = FileExporter.exportPerImage(getApplication(), dirUri, items)
            val msg = result.fold({ "$it file(s) saved" }, { "Save failed: ${it.message}" })
            _uiState.update { it.copy(saveResultSnackbar = msg) }
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

    override fun onCleared() {
        viewModelScope.launch {
            batchJob?.cancelAndJoin()
            InferenceDispatchers.close()
        }
        app.sessionManager.closeAll()
        super.onCleared()
    }

    private suspend fun getCachedLabels(config: ModelConfig): List<String> {
        if (config.labelsUriString.isBlank()) return emptyList()
        return labelCache.getOrPut(config.labelsUriString) {
            app.tagEngine.loadLabels(getApplication(), Uri.parse(config.labelsUriString))
        }
    }

    private fun resultToText(result: InferenceResult?, settings: AppSettings): String =
        when (result) {
            is InferenceResult.TagResult -> result.tags.joinToString(settings.tagSeparator) { tag ->
                if (settings.replaceUnderscoreWithSpace) tag.label.replace('_', ' ') else tag.label
            }
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

    private fun updateItemResult(index: Int, original: BatchImageItem, result: InferenceResult, settings: AppSettings) {
        val text = resultToText(result, settings)
        _uiState.update { state ->
            val list = state.selectedImages.toMutableList()
            if (index < list.size) {
                list[index] = original.copy(
                    status = if (result is InferenceResult.Failure) BatchItemStatus.FAILED else BatchItemStatus.DONE,
                    result = result,
                    text = text,
                )
            }
            state.copy(selectedImages = list)
        }
    }

    private suspend fun persistQueueSnapshot(isComplete: Boolean) {
        val images = _uiState.value.selectedImages
        val settings = _settings.value
        val queueJson = app.batchSessionRepository.buildQueueJson(images) { it.text.ifBlank { resultToText(it.result, settings) } }
        val resultsJson = app.batchSessionRepository.buildResultsJson(images, settings.activeMode) { it.text.ifBlank { resultToText(it.result, settings) } }
        val entity = com.example.onnxtagger.data.db.BatchSessionEntity(
            id = currentSessionId,
            timestamp = System.currentTimeMillis(),
            mode = settings.activeMode.name,
            imageCount = images.size,
            successCount = images.count { it.status == BatchItemStatus.DONE },
            isComplete = isComplete,
            resultsJson = resultsJson,
            queueStateJson = queueJson,
        )
        app.database.batchSessionDao().insert(entity)
    }
}
