package com.example.onnxtagger.data.model

import android.net.Uri

data class BatchImageItem(
    val uri: Uri,
    val displayName: String,
    val status: BatchItemStatus = BatchItemStatus.PENDING,
    val result: InferenceResult? = null,
    val isExpanded: Boolean = false,
)

enum class BatchItemStatus { PENDING, PROCESSING, DONE, FAILED }
