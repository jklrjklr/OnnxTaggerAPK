package com.example.onnxtagger.data.model

import android.net.Uri

data class BatchImageItem(
    val uri: Uri,
    val displayName: String,
    val status: BatchItemStatus = BatchItemStatus.PENDING,
    val result: InferenceResult? = null,
    val isExpanded: Boolean = false,
    val text: String = "",
    val undoText: String? = null,      // previous text before last AI run, for single-level undo
    val isSelected: Boolean = true,    // whether this image is included in the next run
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
)

enum class BatchItemStatus { PENDING, PROCESSING, DONE, FAILED }

