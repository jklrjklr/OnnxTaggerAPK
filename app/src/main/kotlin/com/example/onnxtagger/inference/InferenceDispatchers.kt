package com.example.onnxtagger.inference

import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.newSingleThreadContext

object InferenceDispatchers {

    @Volatile private var _inference: CloseableCoroutineDispatcher? = null

    // Lazy creation: recreated automatically after close() when a new ViewModel is started.
    val inference: CoroutineDispatcher
        get() = _inference ?: synchronized(this) {
            _inference ?: newSingleThreadContext("InferenceThread").also { _inference = it }
        }

    // Called from MainViewModel.onCleared() after batchJob.cancelAndJoin().
    fun close() {
        synchronized(this) {
            _inference?.close()
            _inference = null
        }
    }
}
