# Model Card

## 模型总览

| 模型 | 用途 | 参数量 | 许可证 | 商用限制 |
|------|------|--------|--------|----------|
| Gemma 4 E2B | 主对话模型 | 2B | Gemma Terms | 需遵守 Google 使用条款 |
| Gemma 4 mmproj | 多模态投影器 | - | Gemma Terms | 同上 |
| MOSS TTS Nano | 语音合成/克隆 | 100M | Apache 2.0 | 无限制 |
| MOSS Audio Tokenizer | 音频编解码 | - | Apache 2.0 | 无限制 |
| SenseVoice | 语音识别 (ASR) | - | MIT | 无限制 |
| Silero VAD | 语音活动检测 | - | MIT | 无限制 |
| DreamLite 0.3B | 文本生图 | 0.3B | Apache 2.0 | 无限制 |
| Stable Diffusion.cpp | 文本生图 (推理引擎) | - | MIT | 无限制 |
| Sherpa-ONNX | ASR/VAD 推理框架 | - | Apache 2.0 | 无限制 |
| ONNX Runtime | 模型推理运行时 | - | MIT | 无限制 |
| Qwen3-VL 4B | DreamLite 文本嵌入 | 4B | Apache 2.0 | 无限制 |

---

## 各模型详情

### 1. Gemma 4 E2B -- 主对话模型

| 项目 | 说明 |
|------|------|
| 用途 | 陪伴式对话生成、角色表达、关系意识对话、记忆/偏好提取 |
| 格式 | GGUF (llama.cpp) / LiteRT LM |
| 来源 | [google/gemma-4-e2b](https://ai.google.dev/gemma) |
| 参数量 | 2B |
| 量化 | Q4_K_P (GGUF) / INT8 (LiteRT) |
| 许可证 | [Gemma Terms of Use](https://ai.google.dev/gemma/terms) |
| 商用限制 | Google Gemma 模型的使用需遵守其服务条款，包括但不限于：不得用于生成有害内容、需标注模型来源等。商业部署前需确认合规性。 |

### 2. Gemma 4 E2B mmproj -- 多模态投影器

| 项目 | 说明 |
|------|------|
| 用途 | 处理图片输入，将视觉信息编码为文本空间表示 |
| 格式 | GGUF (f16) |
| 来源 | 同 Gemma 4 E2B |
| 许可证 | 同上 |

### 3. MOSS TTS Nano -- 语音合成

| 项目 | 说明 |
|------|------|
| 用途 | 本地语音合成、角色语音克隆 |
| 格式 | ONNX (100M 参数) |
| 来源 | [OpenMOSS/MOSS-TTS](https://github.com/OpenMOSS/MOSS-TTS) |
| 许可证 | Apache 2.0 |
| 商用限制 | 无，可自由商用 |
| 说明 | 需配合 MOSS Audio Tokenizer 使用；需要参考音频进行语音克隆 |

### 4. MOSS Audio Tokenizer -- 音频编解码

| 项目 | 说明 |
|------|------|
| 用途 | 将音频编码为语义 token / 将 token 解码为音频波形 |
| 格式 | ONNX |
| 来源 | [OpenMOSS/MOSS-Audio-Tokenizer-Nano](https://github.com/OpenMOSS/MOSS-TTS) |
| 许可证 | Apache 2.0 |
| 商用限制 | 无 |

### 5. SenseVoice -- 语音识别

| 项目 | 说明 |
|------|------|
| 用途 | 本地语音转文字 (ASR) |
| 格式 | ONNX (INT8 量化) |
| 来源 | [FunAudioLLM/SenseVoice](https://github.com/FunAudioLLM/SenseVoice) |
| 许可证 | MIT |
| 商用限制 | 无 |
| 限制 | 主要针对中英文优化；最大录音时长 15 秒 |

### 6. Silero VAD -- 语音活动检测

| 项目 | 说明 |
|------|------|
| 用途 | 检测语音起止点，配合 SenseVoice 使用 |
| 格式 | ONNX |
| 来源 | [snakers4/silero-vad](https://github.com/snakers4/silero-vad) |
| 许可证 | MIT |
| 商用限制 | 无 |

### 7. DreamLite 0.3B -- 文本生图

| 项目 | 说明 |
|------|------|
| 用途 | 本地文本生成图片，端侧轻量级图像生成 |
| 格式 | ONNX (UNet + VAE + Text Encoder) |
| 来源 | DreamLite (从 iOS 迁移至 Android 并适配) |
| 参数量 | 0.3B |
| 支持分辨率 | 128 / 256 / 384 / 512 / 1024 |
| 许可证 | Apache 2.0 |
| 商用限制 | 无 |
| 说明 | 原为 iOS 端实现，已迁移至 Android 并通过 ONNX Runtime / Stable Diffusion.cpp 适配端侧推理 |

### 8. Stable Diffusion.cpp -- 图片生成推理引擎

| 项目 | 说明 |
|------|------|
| 用途 | C++ 实现的 Stable Diffusion 推理引擎，通过 JNI 调用 |
| 格式 | C++ 库 |
| 来源 | [leejet/stable-diffusion.cpp](https://github.com/leejet/stable-diffusion.cpp) |
| 许可证 | MIT |
| 商用限制 | 无 |

### 9. Sherpa-ONNX -- ASR/VAD 推理框架

| 项目 | 说明 |
|------|------|
| 用途 | 统一的端侧语音处理框架，承载 SenseVoice ASR 和 Silero VAD |
| 格式 | AAR (含 JNI native 库) |
| 来源 | [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) |
| 版本 | 1.13.0 |
| 许可证 | Apache 2.0 |
| 商用限制 | 无 |

### 10. ONNX Runtime -- 模型推理运行时

| 项目 | 说明 |
|------|------|
| 用途 | 统一的 ONNX 模型推理运行时，承载 MOSS TTS、SenseVoice、DreamLite 等 ONNX 模型 |
| 格式 | AAR / SO |
| 来源 | [microsoft/onnxruntime](https://github.com/microsoft/onnxruntime) |
| 版本 | 1.20.0 (Android) / 1.21.0 (Windows) |
| 许可证 | MIT |
| 商用限制 | 无 |

### 11. Qwen3-VL 4B -- DreamLite 文本嵌入模型

| 项目 | 说明 |
|------|------|
| 用途 | DreamLite 图片生成专用的文本嵌入模型 (text encoder) |
| 格式 | MNN |
| 来源 | [Qwen/Qwen3-VL-4B](https://github.com/QwenLM/Qwen2.5-VL) |
| 参数量 | 4B |
| 许可证 | Apache 2.0 |
| 商用限制 | 无 |
| 备注 | DreamLite 的 text encoder 可选择使用 Qwen3-VL 或 Gemma 4|

---

## 商用合规说明

| 项目 | 说明 |
|------|------|
| Gemma 模型 | 受 Google Gemma Terms of Use 约束，商业部署需确认合规性 |
| 语音克隆 | 使用时需获得参考音频所有者授权 |
| 端侧推理 | 所有推理在本地完成，无云端数据传输，符合 GDPR 等数据隐私法规 |

---

## 许可证汇总

| 模型 | 许可证 | 商用 |
|------|--------|------|
| Gemma 4 E2B | Gemma Terms of Use | 需确认 |
| MOSS TTS Nano | Apache 2.0 | 可自由商用 |
| MOSS Audio Tokenizer | Apache 2.0 | 可自由商用 |
| SenseVoice | MIT | 可自由商用 |
| Silero VAD | MIT | 可自由商用 |
| DreamLite 0.3B | Apache 2.0 | 可自由商用 |
| Stable Diffusion.cpp | MIT | 可自由商用 |
| Sherpa-ONNX | Apache 2.0 | 可自由商用 |
| ONNX Runtime | MIT | 可自由商用 |
| Qwen3-VL 4B | Apache 2.0 | 可自由商用 |
