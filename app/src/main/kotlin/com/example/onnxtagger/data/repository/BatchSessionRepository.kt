package com.example.onnxtagger.data.repository

import android.net.Uri
import com.example.onnxtagger.data.db.*
import com.example.onnxtagger.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class BatchSession(
    val id: String,
    val timestamp: Long,
    val mode: InferenceMode,
    val imageCount: Int,
    val successCount: Int,
    val isComplete: Boolean,
    val results: List<PerImageResultJson>,
    val queueItems: List<BatchQueueItemJson>,
)

class BatchSessionRepository(private val dao: BatchSessionDao) {

    private val json = Json { ignoreUnknownKeys = true }

    val recentFlow: Flow<List<BatchSession>> = dao.queryRecent().map { list ->
        list.map { it.toDomain() }
    }

    val resumableFlow: Flow<List<BatchSession>> = dao.queryResumable().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun save(session: BatchSession) {
        dao.insert(session.toEntity())
        dao.pruneOldSessions()
    }

    suspend fun delete(id: String) = dao.delete(id)

    private fun BatchSessionEntity.toDomain() = BatchSession(
        id = id,
        timestamp = timestamp,
        mode = runCatching { InferenceMode.valueOf(mode) }.getOrDefault(InferenceMode.TAG),
        imageCount = imageCount,
        successCount = successCount,
        isComplete = isComplete,
        results = runCatching { json.decodeFromString<List<PerImageResultJson>>(resultsJson) }.getOrDefault(emptyList()),
        queueItems = runCatching { json.decodeFromString<List<BatchQueueItemJson>>(queueStateJson) }.getOrDefault(emptyList()),
    )

    private fun BatchSession.toEntity() = BatchSessionEntity(
        id = id,
        timestamp = timestamp,
        mode = mode.name,
        imageCount = imageCount,
        successCount = successCount,
        isComplete = isComplete,
        resultsJson = json.encodeToString(results),
        queueStateJson = json.encodeToString(queueItems),
    )

    fun buildQueueJson(images: List<BatchImageItem>, getResultText: (BatchImageItem) -> String): String {
        val items = images.map { item ->
            val topTags = when (val r = item.result) {
                is InferenceResult.TagResult ->
                    // FIX-8: always confidence-sorted top-5 for history preview
                    r.tags.sortedByDescending { it.confidence }.take(5).map { it.label }
                else -> emptyList()
            }
            BatchQueueItemJson(
                uriString = item.uri.toString(),
                displayName = item.displayName,
                status = item.status.name,
                resultText = getResultText(item),
                topTags = topTags,
                errorMessage = (item.result as? InferenceResult.Failure)?.error?.message ?: "",
            )
        }
        return json.encodeToString(items)
    }

    fun buildResultsJson(images: List<BatchImageItem>, mode: InferenceMode, getResultText: (BatchImageItem) -> String): String {
        val results = images.filter { it.status == BatchItemStatus.DONE }.map { item ->
            val topTags = when (val r = item.result) {
                is InferenceResult.TagResult ->
                    r.tags.sortedByDescending { it.confidence }.take(5).map { it.label }
                else -> emptyList()
            }
            PerImageResultJson(
                displayName = item.displayName,
                resultText = getResultText(item),
                topTags = topTags,
                mode = mode.name,
            )
        }
        return json.encodeToString(results)
    }

    fun restoreQueueItems(queueItems: List<BatchQueueItemJson>): List<BatchImageItem> {
        return queueItems.map { item ->
            val status = when (item.status) {
                "DONE" -> BatchItemStatus.DONE
                else -> BatchItemStatus.PENDING  // PROCESSING, FAILED, PENDING all become PENDING on resume
            }
            val result: InferenceResult? = when (item.status) {
                "DONE" -> InferenceResult.TagResult(
                    tags = item.topTags.mapIndexed { i, label -> TagEntry(label, 1f, i) },
                    rawOutputSize = item.topTags.size,
                )
                "FAILED" -> null
                else -> null
            }
            BatchImageItem(
                uri = Uri.parse(item.uriString),
                displayName = item.displayName,
                status = status,
                result = result,
            )
        }
    }
}
