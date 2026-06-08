package com.example.onnxtagger.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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

    // Bundles all images + their .txt labels into a single ZIP file in the given tree directory.
    // items: list of (displayName, imageUri, textContent)
    // Returns count of label files written (images without text are still included).
    suspend fun exportZip(
        context: Context,
        treeDirUri: Uri,
        zipFileName: String,
        items: List<Triple<String, Uri, String>>,
        onProgress: ((current: Int, total: Int) -> Unit)? = null,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val treeDocId = DocumentsContract.getTreeDocumentId(treeDirUri)
            val treeDocUri = DocumentsContract.buildDocumentUriUsingTree(treeDirUri, treeDocId)

            val name = if (zipFileName.endsWith(".zip", ignoreCase = true)) zipFileName else "$zipFileName.zip"
            val zipUri = DocumentsContract.createDocument(resolver, treeDocUri, "application/zip", name)
                ?: error("Could not create ZIP file in target directory")

            var labelCount = 0
            resolver.openOutputStream(zipUri)!!.use { out ->
                ZipOutputStream(BufferedOutputStream(out)).use { zos ->
                    items.forEachIndexed { idx, (displayName, imageUri, text) ->
                        val imageName = displayName.substringAfterLast('/').ifBlank { displayName }

                        // Add image bytes
                        resolver.openInputStream(imageUri)?.use { imgIn ->
                            zos.putNextEntry(ZipEntry(imageName))
                            imgIn.copyTo(zos)
                            zos.closeEntry()
                        }

                        // Add label .txt if present
                        if (text.isNotBlank()) {
                            val txtName = imageName.substringBeforeLast('.') + ".txt"
                            zos.putNextEntry(ZipEntry(txtName))
                            zos.write(text.toByteArray(Charsets.UTF_8))
                            zos.closeEntry()
                            labelCount++
                        }

                        onProgress?.invoke(idx + 1, items.size)
                    }
                }
            }
            labelCount
        }
    }
}
