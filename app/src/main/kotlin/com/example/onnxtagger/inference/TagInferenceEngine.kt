package com.example.onnxtagger.inference

import android.content.Context
import android.net.Uri
import com.example.onnxtagger.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.exp

class TagInferenceEngine(
    private val sessionManager: OnnxSessionManager,
    private val preprocessor: ImagePreprocessor,
) {

    suspend fun run(
        context: Context,
        imageUri: Uri,
        labels: List<String>,
        settings: AppSettings,
        modelConfig: ModelConfig,
    ): InferenceResult = runCatching {
        val tensor = preprocessor.preprocess(context, imageUri, modelConfig)
        val session = sessionManager.getOrLoadTagSession(modelConfig)
        val inputName = modelConfig.inputNodeName.ifBlank { session.inputNames.first() }
        val outputName = modelConfig.outputNodeName.ifBlank { session.outputNames.first() }

        val output = withContext(InferenceDispatchers.inference) {
            session.run(mapOf(inputName to tensor))
        }
        tensor.close()

        val rawScores = (output[outputName].get().value as Array<FloatArray>)[0]
        output.close()

        val probs = withContext(Dispatchers.Default) {
            when (modelConfig.outputActivation) {
                OutputActivation.SIGMOID -> sigmoid(rawScores)
                OutputActivation.SOFTMAX -> softmax(rawScores)
                OutputActivation.NONE    -> rawScores
            }
        }

        val threshold = settings.temperature
        val sorted = withContext(Dispatchers.Default) {
            probs.mapIndexed { idx, score ->
                TagEntry(
                    label = labels.getOrElse(idx) { "label_$idx" },
                    confidence = score,
                    originalIndex = idx,
                )
            }
                .filter { it.confidence >= threshold }
                .let { list ->
                    when (settings.tagSortOrder) {
                        TagSortOrder.CONFIDENCE_DESC -> list.sortedByDescending { it.confidence }
                        TagSortOrder.ALPHABETICAL    -> list.sortedBy { it.label }
                        TagSortOrder.ORIGINAL_INDEX  -> list.sortedBy { it.originalIndex }
                    }
                }
                .take(settings.maxTags)
        }

        InferenceResult.TagResult(tags = sorted, rawOutputSize = rawScores.size)
    }.getOrElse { InferenceResult.Failure(it) }

    suspend fun loadLabels(context: Context, labelsUri: Uri): List<String> =
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(labelsUri)!!.bufferedReader().use { reader ->
                val lines = reader.readLines()
                if (lines.firstOrNull()?.contains(',') == true) {
                    // WD14 selected_tags.csv: skip header, take column index 1 (name)
                    lines.drop(1).mapNotNull { line ->
                        line.split(',').getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
                    }
                } else {
                    lines.map { it.trim() }.filter { it.isNotBlank() }
                }
            }
        }

    private fun sigmoid(arr: FloatArray) = FloatArray(arr.size) { i ->
        (1f / (1f + exp(-arr[i].toDouble())).toFloat())
    }

    private fun softmax(arr: FloatArray): FloatArray {
        val max = arr.max()
        val exps = FloatArray(arr.size) { i -> exp((arr[i] - max).toDouble()).toFloat() }
        val sum = exps.sum()
        return FloatArray(arr.size) { i -> exps[i] / sum }
    }
}
