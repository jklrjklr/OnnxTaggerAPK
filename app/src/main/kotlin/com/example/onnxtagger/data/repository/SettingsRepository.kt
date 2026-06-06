package com.example.onnxtagger.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.example.onnxtagger.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    companion object {
        private val KEY_MODE = stringPreferencesKey("active_mode")
        private val KEY_TEMPERATURE = floatPreferencesKey("temperature")
        private val KEY_PREPEND = stringPreferencesKey("prepend_text")
        private val KEY_APPEND = stringPreferencesKey("append_text")
        private val KEY_ACT_ON_EXISTING = stringPreferencesKey("act_on_existing")
        private val KEY_MAX_TAGS = intPreferencesKey("max_tags")
        private val KEY_TAG_SORT = stringPreferencesKey("tag_sort_order")
        private val KEY_TAG_SEP = stringPreferencesKey("tag_separator")
        private val KEY_BATCH_SEP = stringPreferencesKey("batch_item_separator")
        private val KEY_MAX_TOKENS = intPreferencesKey("max_tokens")
        private val KEY_ACTIVE_PROFILE = stringPreferencesKey("active_profile_id")
        private val KEY_SAVE_DIR = stringPreferencesKey("save_dir_uri")
        private val KEY_TAG_MODEL_JSON = stringPreferencesKey("tag_model_config_json")
        private val KEY_CAPTION_MODEL_JSON = stringPreferencesKey("caption_model_config_json")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val settingsFlow: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            activeMode = prefs[KEY_MODE]?.let { runCatching { InferenceMode.valueOf(it) }.getOrNull() }
                ?: InferenceMode.TAG,
            temperature = prefs[KEY_TEMPERATURE] ?: 0.4f,
            prependText = prefs[KEY_PREPEND] ?: "",
            appendText = prefs[KEY_APPEND] ?: "",
            actOnExisting = prefs[KEY_ACT_ON_EXISTING]
                ?.let { runCatching { ActOnExisting.valueOf(it) }.getOrNull() } ?: ActOnExisting.IGNORE,
            maxTags = prefs[KEY_MAX_TAGS] ?: 30,
            tagSortOrder = prefs[KEY_TAG_SORT]
                ?.let { runCatching { TagSortOrder.valueOf(it) }.getOrNull() } ?: TagSortOrder.CONFIDENCE_DESC,
            tagSeparator = prefs[KEY_TAG_SEP] ?: ", ",
            batchItemSeparator = prefs[KEY_BATCH_SEP] ?: "\n",
            maxTokens = prefs[KEY_MAX_TOKENS] ?: 200,
            activeProfileId = prefs[KEY_ACTIVE_PROFILE] ?: "",
            saveDirUriString = prefs[KEY_SAVE_DIR] ?: "",
            tagModelConfig = prefs[KEY_TAG_MODEL_JSON]
                ?.let { runCatching { json.decodeFromString<ModelConfig>(it) }.getOrNull() }
                ?: ModelConfig(mode = InferenceMode.TAG),
            captionModelConfig = prefs[KEY_CAPTION_MODEL_JSON]
                ?.let { runCatching { json.decodeFromString<ModelConfig>(it) }.getOrNull() }
                ?: ModelConfig(mode = InferenceMode.CAPTION),
        )
    }

    suspend fun save(settings: AppSettings) {
        dataStore.edit { prefs ->
            prefs[KEY_MODE] = settings.activeMode.name
            prefs[KEY_TEMPERATURE] = settings.temperature
            prefs[KEY_PREPEND] = settings.prependText
            prefs[KEY_APPEND] = settings.appendText
            prefs[KEY_ACT_ON_EXISTING] = settings.actOnExisting.name
            prefs[KEY_MAX_TAGS] = settings.maxTags
            prefs[KEY_TAG_SORT] = settings.tagSortOrder.name
            prefs[KEY_TAG_SEP] = settings.tagSeparator
            prefs[KEY_BATCH_SEP] = settings.batchItemSeparator
            prefs[KEY_MAX_TOKENS] = settings.maxTokens
            prefs[KEY_ACTIVE_PROFILE] = settings.activeProfileId
            prefs[KEY_SAVE_DIR] = settings.saveDirUriString
            prefs[KEY_TAG_MODEL_JSON] = json.encodeToString(settings.tagModelConfig)
            prefs[KEY_CAPTION_MODEL_JSON] = json.encodeToString(settings.captionModelConfig)
        }
    }
}
