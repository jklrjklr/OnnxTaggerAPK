package com.example.onnxtagger.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ModelProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val tagModelConfig: ModelConfig,
    val captionModelConfig: ModelConfig,
    val temperature: Float = 0.4f,
    val maxTags: Int = 30,
    val maxTokens: Int = 200,
    val tagSeparator: String = ", ",
)
