package com.example.onnxtagger.data.model

data class AppSettings(
    val activeMode: InferenceMode = InferenceMode.TAG,
    val temperature: Float = 0.4f,
    val prependText: String = "",
    val appendText: String = "",
    val actOnExisting: ActOnExisting = ActOnExisting.IGNORE,
    val maxTags: Int = 30,
    val tagSortOrder: TagSortOrder = TagSortOrder.CONFIDENCE_DESC,
    val tagSeparator: String = ", ",
    val batchItemSeparator: String = "\n",
    val maxTokens: Int = 200,
    val activeProfileId: String = "",
    val replaceUnderscoreWithSpace: Boolean = false,
    val triggerWord: String = "",
    val warmUpOnStart: Boolean = false,
    val pagerPrefetchLimit: Int = 1,
    val autoResumeLast: Boolean = false,
    val tagModelConfig: ModelConfig = ModelConfig(mode = InferenceMode.TAG),
    val captionModelConfig: ModelConfig = ModelConfig(mode = InferenceMode.CAPTION),
)

enum class TagSortOrder { CONFIDENCE_DESC, ALPHABETICAL, ORIGINAL_INDEX }
