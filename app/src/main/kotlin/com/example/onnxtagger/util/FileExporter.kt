package com.example.onnxtagger.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FileExporter {

    // Saves one .txt per image into the given tree URI directory.
    // Overwrites any existing .txt with the same base name.
    // Returns count of files written.
    suspend fun exportPerImage(
        context: Context,
        treeDirUri: Uri,
        items: List<Pair<String, String>>, // displayName to content
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val treeDocId = DocumentsContract.getTreeDocumentId(treeDirUri)
            val treeDocUri = DocumentsContract.buildDocumentUriUsingTree(treeDirUri, treeDocId)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeDirUri, treeDocId)

            var count = 0
            for ((displayName, content) in items) {
                if (content.isBlank()) continue
                val base = displayName.substringBeforeLast('.').substringAfterLast('/')
                    .ifBlank { displayName }
                val txtName = "$base.txt"

                // Delete existing file with same name to overwrite cleanly
                resolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    ),
                    "${DocumentsContract.Document.COLUMN_DISPLAY_NAME} = ?",
                    arrayOf(txtName),
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val existingDocId = cursor.getString(0)
                        val existingUri = DocumentsContract.buildDocumentUriUsingTree(treeDirUri, existingDocId)
                        runCatching { DocumentsContract.deleteDocument(resolver, existingUri) }
                    }
                }

                val newUri = DocumentsContract.createDocument(
                    resolver, treeDocUri, "text/plain", txtName
                ) ?: continue
                resolver.openOutputStream(newUri)!!.use {
                    it.write(content.toByteArray(Charsets.UTF_8))
                }
                count++
            }
            count
        }
    }
}
