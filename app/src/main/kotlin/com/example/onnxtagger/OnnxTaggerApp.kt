package com.example.onnxtagger

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.example.onnxtagger.data.db.AppDatabase
import com.example.onnxtagger.data.repository.*
import com.example.onnxtagger.inference.*

val Application.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class OnnxTaggerApp : Application() {

    val database by lazy { AppDatabase.getInstance(this) }

    val settingsRepository by lazy { SettingsRepository(dataStore) }
    val profileRepository by lazy { ProfileRepository(dataStore) }
    val modelRepository by lazy { ModelRepository() }
    val batchSessionRepository by lazy { BatchSessionRepository(database.batchSessionDao()) }

    val sessionManager by lazy { OnnxSessionManager(this) }
    val preprocessor by lazy { ImagePreprocessor() }
    val tagEngine by lazy { TagInferenceEngine(sessionManager, preprocessor) }
    val captionEngine by lazy { CaptionInferenceEngine(sessionManager, preprocessor) }
}
