package com.example.onnxtagger.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "batch_sessions")
data class BatchSessionEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val mode: String,
    val imageCount: Int,
    val successCount: Int,
    val isComplete: Boolean,
    val resultsJson: String,
    val queueStateJson: String,
)

// FIX-8: topTags is always confidence-sorted (top-5 by probability), regardless of
// the user's display sort order setting, so history preview chips are meaningful.
@Serializable
data class PerImageResultJson(
    val displayName: String,
    val resultText: String,
    val topTags: List<String> = emptyList(),
    val mode: String,
)

@Serializable
data class BatchQueueItemJson(
    val uriString: String,
    val displayName: String,
    val status: String,
    val resultText: String = "",
    val topTags: List<String> = emptyList(),
    val errorMessage: String = "",
)
