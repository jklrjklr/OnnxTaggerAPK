package com.example.onnxtagger.inference

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import com.example.onnxtagger.data.model.ChannelOrder
import com.example.onnxtagger.data.model.ModelConfig
import com.example.onnxtagger.data.model.TensorLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer

class ImagePreprocessor {

    suspend fun preprocess(context: Context, imageUri: Uri, config: ModelConfig): OnnxTensor {
        // Phase 1: decode on IO thread
        val raw = withContext(Dispatchers.IO) {
            decodeBitmap(context, imageUri, config.inputWidth, config.inputHeight)
        }

        // Phase 2: pixel conversion on Default (CPU-bound)
        val (floatArray, shape) = withContext(Dispatchers.Default) {
            convertToFloatArray(raw, config)
        }

        // FIX-1: bitmap is recycled AFTER floatArray is fully filled so the tensor
        // holds its own copy of the data and has no dependency on bitmap memory.
        raw.recycle()

        // Phase 3: create tensor on inference thread
        return withContext(InferenceDispatchers.inference) {
            val env = OrtEnvironment.getEnvironment()
            OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArray), shape)
        }
    }

    private fun decodeBitmap(context: Context, uri: Uri, targetW: Int, targetH: Int): Bitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, options)
        }
        val sampleSize = calculateInSampleSize(options.outWidth, options.outHeight, targetW, targetH)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = context.contentResolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
                ?: error("Failed to decode bitmap from $uri")
        }
        // Composite on white background to handle RGBA transparency (critical for WD14 anime images)
        val withBg = Bitmap.createBitmap(raw.width, raw.height, Bitmap.Config.ARGB_8888)
        Canvas(withBg).apply {
            drawColor(Color.WHITE)
            drawBitmap(raw, 0f, 0f, null)
        }
        raw.recycle()
        return Bitmap.createScaledBitmap(withBg, targetW, targetH, true).also { withBg.recycle() }
    }

    private fun convertToFloatArray(bitmap: Bitmap, config: ModelConfig): Pair<FloatArray, LongArray> {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val mean = config.normalizeMean.toFloatArray()
        val std = config.normalizeStd.toFloatArray()
            .map { if (it == 0f) 1f else it }.toFloatArray()

        val floatArray = FloatArray(h * w * 3)

        when (config.inputLayout) {
            TensorLayout.NHWC -> {
                pixels.forEachIndexed { i, px ->
                    val r = ((px shr 16 and 0xFF).toFloat() - mean[0]) / std[0]
                    val g = ((px shr 8  and 0xFF).toFloat() - mean[1]) / std[1]
                    val b = ((px        and 0xFF).toFloat() - mean[2]) / std[2]
                    val base = i * 3
                    when (config.channelOrder) {
                        ChannelOrder.RGB -> { floatArray[base] = r; floatArray[base+1] = g; floatArray[base+2] = b }
                        ChannelOrder.BGR -> { floatArray[base] = b; floatArray[base+1] = g; floatArray[base+2] = r }
                    }
                }
            }
            TensorLayout.NCHW -> {
                val cs = h * w
                pixels.forEachIndexed { i, px ->
                    val r = ((px shr 16 and 0xFF).toFloat() - mean[0]) / std[0]
                    val g = ((px shr 8  and 0xFF).toFloat() - mean[1]) / std[1]
                    val b = ((px        and 0xFF).toFloat() - mean[2]) / std[2]
                    when (config.channelOrder) {
                        ChannelOrder.RGB -> { floatArray[i] = r; floatArray[cs+i] = g; floatArray[2*cs+i] = b }
                        ChannelOrder.BGR -> { floatArray[i] = b; floatArray[cs+i] = g; floatArray[2*cs+i] = r }
                    }
                }
            }
        }

        val shape = when (config.inputLayout) {
            TensorLayout.NHWC -> longArrayOf(1, h.toLong(), w.toLong(), 3)
            TensorLayout.NCHW -> longArrayOf(1, 3, h.toLong(), w.toLong())
        }
        return Pair(floatArray, shape)
    }

    // Derives inSampleSize directly from model config dimensions.
    private fun calculateInSampleSize(srcW: Int, srcH: Int, targetW: Int, targetH: Int): Int {
        var size = 1
        if (srcH > targetH || srcW > targetW) {
            val halfH = srcH / 2
            val halfW = srcW / 2
            while ((halfH / size) >= targetH && (halfW / size) >= targetW) size *= 2
        }
        return size
    }
}
