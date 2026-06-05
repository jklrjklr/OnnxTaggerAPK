package com.example.onnxtagger.util

import com.example.onnxtagger.data.model.ActOnExisting

object OutputFormatter {

    // IGNORE semantics (corrected): no longer gates the batch.
    // existingAnchor is used only for APPEND/PREPEND positioning.
    // IGNORE produces the batch output fresh, without merging with pre-existing content.
    // IGNORE vs OVERWRITE: both produce `decorated`; the UI distinguishes them by showing
    // a warning chip when IGNORE is active and the field is non-empty.
    fun apply(
        newContent: String,
        existingAnchor: String,
        prepend: String,
        append: String,
        actOnExisting: ActOnExisting,
        separator: String,
    ): String {
        val decorated = buildString {
            if (prepend.isNotEmpty()) append(prepend)
            append(newContent)
            if (append.isNotEmpty()) append(append)
        }
        return when (actOnExisting) {
            ActOnExisting.IGNORE    -> decorated
            ActOnExisting.OVERWRITE -> decorated
            ActOnExisting.APPEND    ->
                if (existingAnchor.isBlank()) decorated
                else "$existingAnchor$separator$decorated"
            ActOnExisting.PREPEND   ->
                if (existingAnchor.isBlank()) decorated
                else "$decorated$separator$existingAnchor"
        }
    }
}
