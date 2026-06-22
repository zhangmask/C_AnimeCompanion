# Model Card (MNN 版本)

> **本版本使用 MNN 推理框架 + Qwen3.5 2B，针对 ARM 架构深度优化。**

## 模型总览

| 模型 | 用途 | 参数量 | 推理框架 | 许可证 | 商用限制 |
|------|------|--------|----------|--------|----------|
| **Qwen3.5 2B** | 主对话模型 | 2B | MNN | Apache 2.0 | **无限制** |
| **Qwen3.5 2B Embedding** | 文本嵌入（生图用） | 2B | MNN | Apache 2.0 | **无限制** |
| MOSS TTS Nano | 语音合成/克隆 | 100M | ONNX | Apache 2.0 | 无限制 |
| MOSS Audio Tokenizer | 音频编解码 | - | ONNX | Apache 2.0 | 无限制 |
| SenseVoice | 语音识别 (ASR) | - | ONNX | MIT | 无限制 |
| Silero VAD | 语音活动检测 | - | ONNX | MIT | 无限制 |
| DreamLite 0.3B | 文本生图 | 0.3B | ONNX | Apache 2.0 | 无限制 |
| Stable Diffusion.cpp | 文本生图 (推理引擎) | - | C++ | MIT | 无限制 |
| Sherpa-ONNX | ASR/VAD 推理框架 | - | JNI | Apache 2.0 | 无限制 |
| ONNX Runtime | 模型推理运行时 | - | - | MIT | 无限制 |
| **MNN** | **端侧推理框架** | **-** | **C++** | **Apache 2.0** | **无限制** |

---

## 各模型详情

### 1. Qwen3.5 2B -- 主对话模型

| 项目 | 说明 |
|------|------|
| 用途 | 陪伴式对话生成、角色表达、关系意识对话、记忆/偏好提取 |
| 格式 | MNN (INT4 / FP16) |
| 来源 | [Qwen/Qwen3.5-2B](https://huggingface.co/Qwen/Qwen3.5-2B) |
| 参数量 | 2B |
| 量化 | INT4 (默认) / FP16 (高端设备) |
| 许可证 | Apache 2.0 |
| 商用限制 | **无限制**，可自由商用 |
| 中文能力 | **原生中文训练**，对话自然度优于 Gemma/Llama |
| 推理速度 | 8-12 tokens/s (骁龙 8 Gen 2, INT4) |

**为什么选择 Qwen3.5 2B：**

| 对比 | Qwen3.5 2B | Gemma 4 E2B | Llama 3.2 2B |
|------|------------|-------------|--------------|
| 中文能力 | **最优** | 一般 | 一般 |
| 推理速度 (MNN) | **8-12 tokens/s** | 不支持 MNN | 6-8 tokens/s |
| 内存占用 (INT4) | **1.2GB** | 1.5GB | 1.2GB |
| 角色扮演 | **优秀** | 良好 | 一般 |
| 许可证 | **Apache 2.0** | Gemma Terms | Llama License |
| 商用限制 | **无** | 需确认 | 有限制 |

---

### 2. Qwen3.5 2B Embedding -- 文本嵌入模型

| 项目 | 说明 |
|------|------|
| 用途 | DreamLite 图片生成专用的文本嵌入模型 (text encoder) |
| 格式 | MNN |
| 来源 | [Qwen/Qwen3.5-2B](https://huggingface.co/Qwen/Qwen3.5-2B) |
| 参数量 | 2B |
| 许可证 | Apache 2.0 |
| 商用限制 | 无 |
| 优势 | 中文 Prompt 原生理解，不需要翻译；MNN 推理，ARM 优化 |

**对比原方案：**

| 对比 | MNN + Qwen3.5 Embedding | ONNX + Qwen3-VL 4B |
|------|--------------------------|---------------------|
| 推理框架 | MNN (ARM 原生优化) | ONNX Runtime |
| 模型大小 | ~600MB | ~1.5GB |
| 中文支持 | **原生** | 需要额外适配 |
| 推理速度 | **更快** | 一般 |

---

### 3. MOSS TTS Nano -- 语音合成

| 项目 | 说明 |
|------|------|
| 用途 | 本地语音合成、角色语音克隆 |
| 格式 | ONNX (100M 参数) |
| 来源 | [OpenMOSS/MOSS-TTS](https://github.com/OpenMOSS/MOSS-TTS) |
| 许可证 | Apache 2.0 |
| 商用限制 | 无，可自由商用 |
| 说明 | 需配合 MOSS Audio Tokenizer 使用；需要参考音频进行语音克隆 |

---

### 4. MOSS Audio Tokenizer -- 音频编解码

| 项目 | 说明 |
|------|------|
| 用途 | 将音频编码为语义 token / 将 token 解码为音频波形 |
| 格式 | ONNX |
| 来源 | [OpenMOSS/MOSS-Audio-Tokenizer-Nano](https://github.com/OpenMOSS/MOSS-TTS) |
| 许可证 | Apache 2.0 |
| 商用限制 | 无 |

---

### 5. SenseVoice -- 语音识别

| 项目 | 说明 |
|------|------|
| 用途 | 本地语音转文字 (ASR) |
| 格式 | ONNX (INT8 量化) |
| 来源 | [FunAudioLLM/SenseVoice](https://github.com/FunAudioLLM/SenseVoice) |
| 许可证 | MIT |
| 商用限制 | 无 |
| 限制 | 主要针对中英文优化；最大录音时长 15 秒 |

---

### 6. Silero VAD -- 语音活动检测

| 项目 | 说明 |
|------|------|
| 用途 | 检测语音起止点，配合 SenseVoice 使用 |
| 格式 | ONNX |
| 来源 | [snakers4/silero-vad](https://github.com/snakers4/silero-vad) |
| 许可证 | MIT |
| 商用限制 | 无 |

---

### 7. DreamLite 0.3B -- 文本生图

| 项目 | 说明 |
|------|------|
| 用途 | 本地文本生成图片，端侧轻量级图像生成 |
| 格式 | ONNX (UNet + VAE) |
| 来源 | DreamLite (从 iOS 迁移至 Android 并适配) |
| 参数量 | 0.3B |
| 支持分辨率 | 128 / 256 / 384 / 512 / 1024 |
| 许可证 | Apache 2.0 |
| 商用限制 | 无 |
| 文本编码 | **MNN + Qwen3.5 2B Embedding** (替代原 ONNX + Qwen3-VL) |

---

### 8. Stable Diffusion.cpp -- 图片生成推理引擎

| 项目 | 说明 |
|------|------|
| 用途 | C++ 实现的 Stable Diffusion 推理引擎，通过 JNI 调用 |
| 格式 | C++ 库 |
| 来源 | [leejet/stable-diffusion.cpp](https://github.com/leejet/stable-diffusion.cpp) |
| 许可证 | MIT |
| 商用限制 | 无 |

---

### 9. MNN -- 端侧推理框架

| 项目 | 说明 |
|------|------|
| 用途 | 主对话模型和文本嵌入模型的推理框架 |
| 格式 | C++ 库 (libMNN.so + libMNN_Express.so) |
| 来源 | [alibaba/MNN](https://github.com/alibaba/MNN) |
| 许可证 | Apache 2.0 |
| 商用限制 | 无 |
| 核心特性 | ARM NEON/SVE 原生优化、INT4 量化、GPU 加速 (OpenCL/Vulkan) |
| 模型大小 | 核心库仅 2MB |

**MNN vs 其他推理框架：**

| 对比 | MNN | LiteRT-LM | llama.cpp |
|------|-----|-----------|-----------|
| ARM 优化 | **原生 NEON/SVE** | XNNPACK (通用) | 通用 |
| INT4 支持 | **原生** | 不支持 | 支持 |
| 启动速度 | **最快** | 一般 | 较慢 |
| 内存占用 | **最低** | 一般 | 较高 |
| GPU 加速 | OpenCL/Vulkan | OpenCL | CUDA/Vulkan |
| 中文模型适配 | **Qwen 深度适配** | Gemma 适配 | 通用 |

---

### 10. Sherpa-ONNX -- ASR/VAD 推理框架

| 项目 | 说明 |
|------|------|
| 用途 | 统一的端侧语音处理框架，承载 SenseVoice ASR 和 Silero VAD |
| 格式 | AAR (含 JNI native 库) |
| 来源 | [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) |
| 版本 | 1.13.0 |
| 许可证 | Apache 2.0 |
| 商用限制 | 无 |

---

### 11. ONNX Runtime -- 模型推理运行时

| 项目 | 说明 |
|------|------|
| 用途 | TTS、ASR、VAD 等 ONNX 模型的推理运行时 |
| 格式 | AAR / SO |
| 来源 | [microsoft/onnxruntime](https://github.com/microsoft/onnxruntime) |
| 版本 | 1.20.0 (Android) |
| 许可证 | MIT |
| 商用限制 | 无 |

---

## 商用合规说明

| 项目 | 说明 |
|------|------|
| Qwen3.5 2B | Apache 2.0 许可，**可自由商用，无限制** |
| MNN | Apache 2.0 许可，可自由商用 |
| 语音克隆 | 使用时需获得参考音频所有者授权 |
| 端侧推理 | 所有推理在本地完成，无云端数据传输，符合 GDPR 等数据隐私法规 |

---

## 许可证汇总

| 模型 | 许可证 | 商用 |
|------|--------|------|
| **Qwen3.5 2B** | **Apache 2.0** | **可自由商用** |
| **Qwen3.5 2B Embedding** | **Apache 2.0** | **可自由商用** |
| **MNN** | **Apache 2.0** | **可自由商用** |
| MOSS TTS Nano | Apache 2.0 | 可自由商用 |
| MOSS Audio Tokenizer | Apache 2.0 | 可自由商用 |
| SenseVoice | MIT | 可自由商用 |
| Silero VAD | MIT | 可自由商用 |
| DreamLite 0.3B | Apache 2.0 | 可自由商用 |
| Stable Diffusion.cpp | MIT | 可自由商用 |
| Sherpa-ONNX | Apache 2.0 | 可自由商用 |
| ONNX Runtime | MIT | 可自由商用 |

**本版本所有核心模型和框架均为 Apache 2.0 / MIT 许可，商用无任何限制。**
