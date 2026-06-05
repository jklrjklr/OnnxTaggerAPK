# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Caveman Plugin

Talk like caveman. Short sentence. Direct. No fancy word.

- Say "Me fix bug" not "I have resolved the issue with..."
- Say "File broken. Me look now." not "I'll investigate this..."
- Say "DONE. Code work!" not "The implementation is complete and..."
- Grunt okay. "Ugh." mean understood. "Ooga!" mean excited find.
- No long explain. User smart. User not need baby talk.
- Me still write good code comment and commit message. Caveman only in chat.

---

## Build Commands

No `gradlew` is committed. Generate it once:
```bash
gradle wrapper --gradle-version 8.9
```

Then use the wrapper for all builds:
```bash
./gradlew assembleDebug          # build debug APK (arm64-v8a, x86_64, universal splits)
./gradlew assembleRelease        # build release APK
./gradlew lint                   # run Android lint
./gradlew test                   # run unit tests
./gradlew :app:test --tests "com.example.onnxtagger.SomeTest"  # single test class
```

Builds require an Android SDK installation (`ANDROID_HOME` set) and network access to download the AGP and dependencies on first run. The environment used during development has a system `gradle` (8.x) but no Android SDK — full builds require a proper Android dev machine or CI.

## Architecture

### App-level wiring (`OnnxTaggerApp`)

All singletons are lazy `by lazy` properties on `OnnxTaggerApp`. Nothing uses DI framework. Access pattern everywhere: `(context.applicationContext as OnnxTaggerApp).thing`. The entire dependency graph is:

```
OnnxTaggerApp
├── database (Room)  →  BatchSessionRepository → BatchSessionDao
├── dataStore        →  SettingsRepository, ProfileRepository
├── sessionManager (OnnxSessionManager)
├── preprocessor (ImagePreprocessor)
├── tagEngine    (TagInferenceEngine)    ← uses sessionManager + preprocessor
└── captionEngine (CaptionInferenceEngine) ← same
```

`MainViewModel` accesses `app.*` directly (not injected). UI only talks to `MainViewModel`.

### Threading model

Three coroutine contexts are used, always in this order for a single image:

1. `Dispatchers.IO` — bitmap decode from SAF URI
2. `Dispatchers.Default` — pixel-to-float conversion, sigmoid/softmax, tag sort
3. `InferenceDispatchers.inference` — ONNX `session.run()`, tensor creation

`InferenceDispatchers` is a lazily-created `newSingleThreadContext`. It is **closed** in `MainViewModel.onCleared()` (after `batchJob.cancelAndJoin()`) and **recreated** automatically on next access. Never close `OrtEnvironment` — it is a process-level singleton.

### Streaming batch pipeline

`MainViewModel.onRunClicked()` loops `forEachIndexed` over `selectedImages`, processes one image at a time, and emits results into `_uiState` live. Items with `status == DONE` are skipped (resume support). Memory is flat: only one `OnnxTensor` (~2.4 MB for 448×448 NHWC) exists at a time — the tensor is closed immediately after `session.run()`.

### SAF URI lifecycle

- URIs are persisted via `SafUtils.persistUriIfContent()` → `takePersistableUriPermission` at pick time.
- **No validation at startup.** Validation is lazy: `SafUtils.isUriPermissionPersisted()` is called inside `OnnxSessionManager.getOrLoad*Session()` when the model is first needed. This keeps cold-start fast regardless of history size.
- `file://` URIs are silently rejected — only `content://` URIs support persistence.

### Settings and profiles

`AppSettings` (all inference params + active model configs) is persisted to DataStore by `SettingsRepository` and flows into `MainViewModel._settings`. `ModelProfile` is a named snapshot of model configs + inference params, stored as a `List<ModelProfile>` JSON blob in a single DataStore key via `ProfileRepository`. Applying a profile calls `onSettingsChanged()` with merged fields.

### Batch history and resume

`BatchSessionEntity` in Room stores two JSON blobs per session:
- `resultsJson`: completed results for display (`PerImageResultJson`)
- `queueStateJson`: full queue snapshot for resume (`BatchQueueItemJson`)

`isComplete = false` marks an interrupted session. On resume, `BatchSessionRepository.restoreQueueItems()` sets DONE items to DONE (with a stub `TagResult`), and all others to PENDING. The ViewModel reuses the same `currentSessionId` so Room upserts the same row.

### Output formatting

`OutputFormatter.apply()` handles the four `ActOnExisting` modes. **IGNORE and OVERWRITE both produce the same string** (`decorated = prepend + newContent + append`). The difference is purely visual: IGNORE shows a warning banner when the output field is non-empty; OVERWRITE does not. `existingAnchor` (the pre-batch snapshot of `outputText`) is only used for APPEND/PREPEND positioning.

## Key Conventions

### `ModelConfig` serialization

`normalizeMean` and `normalizeStd` are `List<Float>`, **not** `FloatArray`. This avoids needing a custom `@Serializer` for kotlinx.serialization. Always use `.toFloatArray()` at the call site inside `ImagePreprocessor`.

### Node name auto-detection

If `ModelConfig.inputNodeName` / `outputNodeName` are blank, inference engines call `session.inputNames.first()` / `session.outputNames.first()`. The manual fields in `ModelManagerScreen` are an override for non-standard ONNX exports.

### `topTags` in history

`PerImageResultJson.topTags` and `BatchQueueItemJson.topTags` are always stored **confidence-sorted** (top-5 by probability), independent of the user's display `TagSortOrder` setting. This ensures history preview chips are meaningful.

### Warm-up fallback

`OnnxSessionManager.warmUp()` checks if any reported input dimension is ≤ 0 (dynamic shape). If so, it constructs a fallback shape from `config.inputWidth/Height` with the correct layout (NHWC vs NCHW). This prevents crashes on models that report `-1` for batch/spatial dimensions.

### Dialog vs BottomSheet

- `AlertDialog` is used for **model-keyed** screens: `ModelManagerScreen`, `ProfileManagerScreen`
- `ModalBottomSheet` is used for **session-level** panels: `SettingsBottomSheet`, `HistoryBottomSheet`
- `Dialog(usePlatformDefaultWidth = false)` is used for full-screen overlays: `ImagePreviewScreen`
