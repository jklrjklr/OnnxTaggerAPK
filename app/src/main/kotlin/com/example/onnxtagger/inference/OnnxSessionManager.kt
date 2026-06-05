package com.example.onnxtagger.inference

import ai.onnxruntime.*
import android.content.Context
import android.net.Uri
import android.os.Build
import com.example.onnxtagger.data.model.ModelConfig
import com.example.onnxtagger.data.model.TensorLayout
import com.example.onnxtagger.util.SafUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer

data class SessionInfo(
    val inputs: List<Pair<String, NodeInfo?>>,
    val outputs: List<Pair<String, NodeInfo?>>,
) {
    fun inferInputSize(): Pair<Int, Int>? {
        val shape = (inputs.firstOrNull()?.second as? TensorInfo)?.shape ?: return null
        if (shape.size < 4) return null
        // NHWC: [N, H, W, C], NCHW: [N, C, H, W]
        val h = if (shape[1] > 0) shape[1].toInt() else if (shape[2] > 0) shape[2].toInt() else return null
        val w = if (shape[1] > 0) shape[2].toInt() else if (shape[3] > 0) shape[3].toInt() else return null
        return if (h > 0 && w > 0) Pair(w, h) else null
    }
}

class OnnxSessionManager(private val context: Context) {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    private var tagSession: OrtSession? = null
    private var captionEncoderSession: OrtSession? = null
    private var captionDecoderSession: OrtSession? = null

    private var loadedTagUri: String = ""
    private var loadedCaptionUri: String = ""

    suspend fun getOrLoadTagSession(config: ModelConfig): OrtSession =
        withContext(InferenceDispatchers.inference) {
            if (loadedTagUri == config.modelUriString && tagSession != null) {
                return@withContext tagSession!!
            }
            tagSession?.close()
            // FIX-4: lazy SAF URI validation before opening stream
            val uri = Uri.parse(config.modelUriString)
            if (!SafUtils.isUriPermissionPersisted(context, uri)) {
                error("SAF permission revoked for model — re-pick the file in Model Manager")
            }
            tagSession = loadSessionFromUri(uri, config)
            loadedTagUri = config.modelUriString
            warmUp(tagSession!!, config)
            tagSession!!
        }

    suspend fun getOrLoadCaptionSessions(config: ModelConfig): Pair<OrtSession, OrtSession?> =
        withContext(InferenceDispatchers.inference) {
            if (loadedCaptionUri == config.modelUriString && captionEncoderSession != null) {
                return@withContext Pair(captionEncoderSession!!, captionDecoderSession)
            }
            captionEncoderSession?.close()
            captionDecoderSession?.close()
            val uri = Uri.parse(config.modelUriString)
            if (!SafUtils.isUriPermissionPersisted(context, uri)) {
                error("SAF permission revoked for caption model — re-pick the file")
            }
            captionEncoderSession = loadSessionFromUri(uri, config)
            loadedCaptionUri = config.modelUriString
            warmUp(captionEncoderSession!!, config)

            if (config.outputNodeName.isNotBlank()) {
                val decoderUri = Uri.parse(config.outputNodeName)
                if (SafUtils.isUriPermissionPersisted(context, decoderUri)) {
                    captionDecoderSession = loadSessionFromUri(decoderUri, config)
                }
            }
            Pair(captionEncoderSession!!, captionDecoderSession)
        }

    private fun loadSessionFromUri(uri: Uri, config: ModelConfig): OrtSession {
        val opts = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            if (config.useNnapi && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                addNnapi()
            }
        }
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        return env.createSession(bytes, opts)
    }

    // FIX-2: warm-up tensor shape respects inputLayout (NHWC vs NCHW).
    // Falls back to safe default if any reported dimension is dynamic (≤ 0).
    fun warmUp(session: OrtSession, config: ModelConfig) {
        val info = getSessionInfo(session)
        val rawShape = (info.inputs.firstOrNull()?.second as? TensorInfo)?.shape ?: longArrayOf()
        val anyDynamic = rawShape.isEmpty() || rawShape.any { it <= 0L }
        val safeShape = if (anyDynamic) {
            when (config.inputLayout) {
                TensorLayout.NHWC -> longArrayOf(1, config.inputHeight.toLong(), config.inputWidth.toLong(), 3)
                TensorLayout.NCHW -> longArrayOf(1, 3, config.inputHeight.toLong(), config.inputWidth.toLong())
            }
        } else {
            rawShape
        }
        val size = safeShape.fold(1L) { acc, d -> acc * d }.toInt()
        val dummyData = FloatArray(size)
        val inputName = info.inputs.firstOrNull()?.first ?: return
        runCatching {
            val dummy = OnnxTensor.createTensor(env, FloatBuffer.wrap(dummyData), safeShape)
            session.run(mapOf(inputName to dummy)).use { dummy.close() }
        }
    }

    fun getSessionInfo(session: OrtSession): SessionInfo {
        val inputs = session.inputNames.map { name -> name to session.inputInfo[name] }
        val outputs = session.outputNames.map { name -> name to session.outputInfo[name] }
        return SessionInfo(inputs, outputs)
    }

    fun closeTagSession() {
        tagSession?.close()
        tagSession = null
        loadedTagUri = ""
    }

    fun closeAll() {
        tagSession?.close(); tagSession = null
        captionEncoderSession?.close(); captionEncoderSession = null
        captionDecoderSession?.close(); captionDecoderSession = null
    }
}
