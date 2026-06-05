package com.example.onnxtagger.data.model

sealed class InferenceResult {
    data class TagResult(
        val tags: List<TagEntry>,
        val rawOutputSize: Int,
    ) : InferenceResult()

    data class CaptionResult(
        val text: String,
        val tokenCount: Int,
        val stoppedEarly: Boolean,
    ) : InferenceResult()

    data class Failure(val error: Throwable) : InferenceResult()
}

data class TagEntry(
    val label: String,
    val confidence: Float,
    val originalIndex: Int,
)
