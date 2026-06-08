# 2026-05-15 UI/UX Refresh

## Goal

Make the main app surfaces feel quieter, more consistent, and easier to use in repeated private companion sessions, with the largest change focused on the chat input area.

## Implemented Scope

- Rebuilt the chat input as a single bottom panel instead of separate outer buttons plus an outlined text field.
- Moved image upload, voice replay or stop, text entry, and send or microphone into the same input surface.
- Kept the send action visible when text or selected images exist, and kept microphone as the primary empty-input action.
- Kept selected image previews inside the input panel with stable sizing and a compact remove control.
- Reduced chat top-bar and message visual weight by using quieter model status text, narrower message rhythm, subdued assistant surfaces, and lower-contrast timestamps.
- Tuned discover controls so search, create-role entry, mature visibility, filters, and role tags use a calmer Material 3 vocabulary.
- Updated memory filters to use selected filter chips, changed memory tags to lightweight surfaces, and replaced large text action buttons with compact icon buttons.
- Grouped settings rows into consistent Material surfaces with quieter section labels, icon color, row density, and divider treatment.

## Verification

- Ran `./gradlew :app:compileDebugKotlin`.
- Ran `./gradlew :app:assembleDebug`.
- Ran `git diff --check`.
- Installed the debug APK on a connected Android device.
- Manually checked screenshots for Discover, Chat, Memory, and Settings.
- Verified text entry in Chat sends a message and keeps the bottom input panel usable.

## Notes

- This pass does not change repositories, database schema, model integration, voice behavior, image generation behavior, or navigation structure.
- The UI remains based on the existing Jetpack Compose Material 3 theme and semantic color roles.
- Manual screenshot validation used a high font/display scale device, which helped catch the memory action-button crowding fixed in this pass.
