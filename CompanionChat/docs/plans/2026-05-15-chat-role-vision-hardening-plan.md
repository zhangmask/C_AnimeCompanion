# 2026-05-15 Chat, Role, Voice, And Vision Hardening

## Goal

Fix the rough edges found after the UI refresh: role-card chat should open the correct character conversation, long replies should land at the latest content, repeated GGUF output should stop early, and image recognition should receive clearer visual prompts.

## Implemented Scope

- Added `ChatViewModel.startRoleConversation(roleId)` so a role-card chat action activates the role and creates a fresh session seeded with that role's opening message.
- Wired discover role detail and character management "对话" actions through the new role conversation path instead of only switching the global active role.
- Added a "对话" action to role cards in character management.
- Adjusted chat auto-scroll to target the bottom of the latest long message, not only the top of the last item.
- Added a llama.cpp streaming repetition guard that cancels generation when the same line or sentence repeats several times.
- Reworked GGUF multimodal prompt construction so ambiguous image questions are treated as image-description requests and the model is instructed to ground answers in visible content only.
- Added safe RoleAware voice-output logs so MOSS clone attempts report whether generated audio played or the path fell back to system TTS.

## MOSS Test Result

- The connected device has the expected MOSS model package files under `/sdcard/Android/data/com.companion.chat/files/models/tts/moss-tts-nano`.
- Existing unit tests confirm role-aware voice output chooses generated audio when clone synthesis succeeds and falls back to system TTS when clone synthesis reports fallback.
- The current `MossTtsNanoVoiceCloneEngine` still validates the OpenMOSS browser ONNX bundle but intentionally falls back because the autoregressive runner for `tts_browser_onnx_meta.json` and `codec_browser_onnx_meta.json` is not implemented yet.

## Verification

- Ran `./gradlew :app:compileDebugKotlin`.
- Ran `./gradlew :app:assembleDebug`.
- Ran `./gradlew :app:testDebugUnitTest --tests com.companion.chat.engine.RoleAwareVoiceOutputEngineTest --tests com.companion.chat.data.voice.MossTtsNanoModelPackageTest`.
- Ran `git diff --check`.
- Installed the debug APK on a connected Android device.
- Checked the device MOSS model directory for required `tts` and `audio_tokenizer` files.

## Notes

- This pass does not implement the full MOSS autoregressive ONNX runner. It makes the current fallback observable and keeps the app usable.
- The image-recognition change is prompt and generation-control hardening; it does not change the underlying `mtmd` projector or GGUF model files.
