package com.example.onnxtagger.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class TensorLayout { NCHW, NHWC }

@Serializable
enum class ChannelOrder { RGB, BGR }

@Serializable
enum class OutputActivation { NONE, SIGMOID, SOFTMAX }

// FIX-1 / FIX-2: normalizeMean/Std stored as List<Float> (canonical JSON schema,
// no custom serializer needed). TensorLayout drives warm-up shape selection (FIX-2).
@Serializable
data class ModelConfig(
    val mode: InferenceMode,
    val modelUriString: String = "",
    val labelsUriString: String = "",
    val tokenizerUriString: String = "",
    val inputWidth: Int = 448,
    val inputHeight: Int = 448,
    val inputLayout: TensorLayout = TensorLayout.NHWC,
    val channelOrder: ChannelOrder = ChannelOrder.BGR,
    val normalizeMean: List<Float> = listOf(0f, 0f, 0f),
    val normalizeStd: List<Float> = listOf(1f, 1f, 1f),
    val outputActivation: OutputActivation = OutputActivation.SIGMOID,
    val useNnapi: Boolean = false,
    val inputNodeName: String = "",
    val outputNodeName: String = "",
)
