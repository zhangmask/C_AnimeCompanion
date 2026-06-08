# GGUF llama.cpp Runtime

The root `app/` module is the canonical Android app. It can use a CPU-only llama.cpp runtime for GGUF text and image chat through `libmtmd`, with LiteRT-LM kept as an optional backend. The first supported native ABI is `arm64-v8a`.

## Repository Setup

Initialize the pinned llama.cpp submodule:

```bash
git submodule update --init --recursive third_party/llama.cpp
git -C third_party/llama.cpp checkout 1ec7ba0c14f33f17e980daeeda5f35b225d41994
```

## Model File

GGUF and LiteRT-LM model files are intentionally ignored by Git and should not be packaged into the APK.

Place the model on the device:

```bash
adb shell mkdir -p /sdcard/Android/data/com.companion.chat/files/models
adb push Gemma-4-E2B-Uncensored-HauhauCS-Aggressive-Q4_K_P.gguf /sdcard/Android/data/com.companion.chat/files/models/
adb push mmproj-Gemma-4-E2B-Uncensored-HauhauCS-Aggressive-f16.gguf /sdcard/Android/data/com.companion.chat/files/models/
```

Default GGUF runtime path:

```text
/sdcard/Android/data/com.companion.chat/files/models/Gemma-4-E2B-Uncensored-HauhauCS-Aggressive-Q4_K_P.gguf
```

Default Gemma 4 multimodal projector path:

```text
/sdcard/Android/data/com.companion.chat/files/models/mmproj-Gemma-4-E2B-Uncensored-HauhauCS-Aggressive-f16.gguf
```

Optional LiteRT-LM runtime path:

```text
/sdcard/Android/data/com.companion.chat/files/models/gemma-4-E2B-it.litertlm
```

Keep local GGUF and mmproj downloads in an ignored cache such as `third_party/models/gguf/`, then push them to `/sdcard/Android/data/com.companion.chat/files/models/`. Do not keep GGUF or mmproj files under Android assets.

The uncensored Gemma 4 projector can be fetched with:

```bash
uvx hf download HauhauCS/Gemma-4-E2B-Uncensored-HauhauCS-Aggressive \
  mmproj-Gemma-4-E2B-Uncensored-HauhauCS-Aggressive-f16.gguf \
  --local-dir third_party/models/gguf
```

## Build

```bash
./gradlew :app:assembleDebug
```

The native build is configured through root `app/src/main/cpp/CMakeLists.txt` and links `third_party/llama.cpp` as CPU-only with `n_gpu_layers=0`.

Default runtime settings favor shorter, faster responses:

```text
contextSize=2048 for text-only, automatically at least 8192 for GGUF image input
maxTokens=256
temperature=0.7
topK=40
topP=0.95
recentPromptMessages=6
```

The GGUF runtime clamps each response to the remaining context window before decoding. If `llama_decode` returns a non-zero status during token generation, the runtime logs the status and ends the current response instead of surfacing a hard chat error.

Generated Gemma and chat-template markers are sanitized before tokens reach the chat UI:

- Stop markers such as `<end_of_turn>`, `<start_of_turn>`, `<|endoftext|>`, and `<|eot_id|>` end the current response and suppress trailing text.
- Role markers such as `<|assistant|>`, `<|user|>`, `<|system|>`, `<assistant>`, `<user>`, and `<system>` are removed without stopping generation.
- The sanitizer keeps a small pending suffix so markers split across streamed token chunks are still recognized.
- Markdown syntax characters such as `#`, `*`, `>`, backticks, and list prefixes are preserved.

Assistant chat bubbles render lightweight Markdown directly in Compose. Supported output includes headings, paragraphs, bold and italic spans, inline code, fenced code blocks, unordered and ordered lists, quote blocks, and link styling. User bubbles remain plain text so user input is not unexpectedly formatted.

## Diagnostics

Runtime logs are written to Android logcat under `LlamaCppEngine` and `CompanionLlamaJNI`. App diagnostic files include:

```text
llama_engine_log.txt
viewmodel_log.txt
```

Generation logs include prompt token count or multimodal prompt positions, prompt decode time, first token latency, generated token count, and tokens per second.

GGUF image input is v1 vision-only multimodal support. It requires the matching `mmproj` file; if that file is missing or unreadable, image requests fail with a clear missing-projector message instead of falling back to LiteRT.

Image chat prompts are wrapped with explicit visual grounding instructions before entering `mtmd`: the model is told to answer only from the image, identify the main subject first, then add key details. Ambiguous user text such as "这是" is treated as "what is in this image" to reduce meaningless location-like replies.

The llama.cpp stream path also includes a local repetition guard. If the model starts repeating the same short line or sentence several times, generation is cancelled early and the partial answer is kept instead of allowing long loops such as repeated "这里是这里" output to fill the chat.
