package com.example.onnxtagger.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.onnxtagger.data.model.ModelProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ProfileRepository(private val dataStore: DataStore<Preferences>) {

    private val KEY = stringPreferencesKey("model_profiles_json")
    private val json = Json { ignoreUnknownKeys = true }

    val profilesFlow: Flow<List<ModelProfile>> = dataStore.data.map { prefs ->
        prefs[KEY]?.let { runCatching { json.decodeFromString<List<ModelProfile>>(it) }.getOrNull() }
            ?: emptyList()
    }

    // FIX-6: atomic read-modify-write via updateData to prevent concurrent save interleaving
    suspend fun saveProfile(profile: ModelProfile) {
        dataStore.updateData { prefs ->
            val current = prefs[KEY]
                ?.let { runCatching { json.decodeFromString<List<ModelProfile>>(it) }.getOrNull() }
                ?: emptyList()
            val updated = current.filter { it.id != profile.id } + profile
            prefs.toMutablePreferences().also { it[KEY] = json.encodeToString(updated) }
        }
    }

    suspend fun deleteProfile(id: String) {
        dataStore.updateData { prefs ->
            val current = prefs[KEY]
                ?.let { runCatching { json.decodeFromString<List<ModelProfile>>(it) }.getOrNull() }
                ?: emptyList()
            val updated = current.filter { it.id != id }
            prefs.toMutablePreferences().also { it[KEY] = json.encodeToString(updated) }
        }
    }
}
