package com.example.onnxtagger.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class ActOnExisting { IGNORE, OVERWRITE, APPEND, PREPEND }
