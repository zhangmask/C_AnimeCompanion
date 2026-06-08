# 2026-05-15 Architecture Simplification Implementation

## Summary

This implementation simplified the app architecture after the full-project review. The main goal was to reduce direct dependency construction in UI code, shrink the responsibilities inside `ChatViewModel`, and remove the risky chat auto-scroll sentinel offset.

## Implemented Changes

- Added a lightweight application dependency container through `AppContainer` and `AppViewModelFactory`.
- Routed `MainActivity` and feature screens through shared ViewModel dependencies instead of letting screens create repositories directly.
- Extracted stage-four preference learning into `PreferenceLearningCoordinator`, including idle scheduling, throttling, retry handling, fallback rule memories, and second-engine summary result handling.
- Moved model configuration state into `ModelConfigViewModel`.
- Moved voice settings state into `VoiceSettingsViewModel`.
- Replaced chat auto-scroll usage of `scrollOffset = Int.MAX_VALUE` with a small `ScrollToLatestMessageEffect` that scrolls to the latest message index through the normal LazyListState API.

## Architecture Notes

- The project keeps manual dependency injection instead of adding Hilt or Koin.
- Existing factory and strategy patterns remain in place, including `InferenceEngineFactory` and `ImageGenerationEngineSelector`.
- ViewModels remain the UI facade, while coordinators own longer-running workflow logic.
- Existing tests can still inject fake repositories through the existing constructor parameters.

## Verification

- `./gradlew compileDebugKotlin`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

All verification commands passed during implementation.
