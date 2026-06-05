package com.example.onnxtagger.inference

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import android.content.Context
import android.net.Uri
import com.example.onnxtagger.data.model.AppSettings
import com.example.onnxtagger.data.model.InferenceResult
import com.example.onnxtagger.data.model.ModelConfig
import com.example.onnxtagger.inference.tokenizer.BertTokenizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.LongBuffer
import kotlin.math.exp
import kotlin.random.Random

class CaptionInferenceEngine(
    private val sessionManager: OnnxSessionManager,
    private val preprocessor: ImagePreprocessor,
) {
    // FIX-5: tokenizer loaded once and cached per URI
    private var tokenizer: BertTokenizer? = null
    private var loadedTokenizerUri: String = ""

    suspend fun ensureTokenizer(context: Context, tokenizerUriString: String) {
        if (loadedTokenizerUri == tokenizerUriString && tokenizer != null) return
        tokenizer = BertTokenizer.fromUri(context, Uri.parse(tokenizerUriString))
        loadedTokenizerUri = tokenizerUriString
    }

    suspend fun run(
        context: Context,
        imageUri: Uri,
        settings: AppSettings,
        modelConfig: ModelConfig,
    ): InferenceResult = runCatching {
        ensureTokenizer(context, modelConfig.tokenizerUriString)
        val tok = tokenizer ?: error("Tokenizer not loaded")

        val (encoderSession, decoderSession) = sessionManager.getOrLoadCaptionSessions(modelConfig)
        val inputTensor = preprocessor.preprocess(context, imageUri, modelConfig)

        val encoderInputName = encoderSession.inputNames.first()
        val encoderOutputName = encoderSession.outputNames.first()

        val hiddenStates = withContext(InferenceDispatchers.inference) {
            encoderSession.run(mapOf(encoderInputName to inputTensor))
        }
        inputTensor.close()

        val hiddenStatesTensor = hiddenStates[encoderOutputName].get()
        val env = OrtEnvironment.getEnvironment()
        val tokenIds = mutableListOf(tok.bosId)
        var stoppedEarly = false

        repeat(settings.maxTokens) { _ ->
            if (stoppedEarly) return@repeat
            val ids = tokenIds.map { it.toLong() }.toLongArray()
            val inputIdsTensor = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(ids),
                longArrayOf(1, ids.size.toLong())
            )
            val attMask = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(LongArray(ids.size) { 1L }),
                longArrayOf(1, ids.size.toLong())
            )
            val decoderInputs = mapOf(
                "input_ids" to inputIdsTensor,
                "encoder_hidden_states" to hiddenStatesTensor,
                "attention_mask" to attMask,
            )
            val decoderOut = withContext(InferenceDispatchers.inference) {
                (decoderSession ?: encoderSession).run(decoderInputs)
            }
            inputIdsTensor.close(); attMask.close()

            val logits = (decoderOut["logits"]!!.get().value as Array<Array<FloatArray>>)[0].last()
            decoderOut.close()

            val nextToken = withContext(Dispatchers.Default) {
                sampleToken(logits, settings.temperature)
            }
            if (nextToken == tok.eosId) { stoppedEarly = true; return@repeat }
            tokenIds.add(nextToken)
        }
        hiddenStates.close()

        val text = tok.decode(tokenIds.drop(1).toIntArray())
        InferenceResult.CaptionResult(text = text, tokenCount = tokenIds.size - 1, stoppedEarly = stoppedEarly)
    }.getOrElse { InferenceResult.Failure(it) }

    private fun sampleToken(logits: FloatArray, temperature: Float): Int {
        if (temperature <= 0f) return logits.indices.maxByOrNull { logits[it] }!!
        val scaled = FloatArray(logits.size) { i -> logits[i] / temperature }
        val maxLogit = scaled.max()
        val exps = FloatArray(logits.size) { i -> exp((scaled[i] - maxLogit).toDouble()).toFloat() }
        val sum = exps.sum()
        val probs = FloatArray(logits.size) { i -> exps[i] / sum }
        val r = Random.nextFloat()
        var cumulative = 0f
        for (i in probs.indices) { cumulative += probs[i]; if (r < cumulative) return i }
        return probs.indices.maxByOrNull { probs[it] }!!
    }
}
