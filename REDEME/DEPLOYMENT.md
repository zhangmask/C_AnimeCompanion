# Anime Companion 部署指南

## 环境要求

| 工具 | 版本 | 说明 |
|---|---|---|
| JDK | 17+ | Android Studio 自带的 JBR 即可 |
| Android SDK | API 36 | Build Tools 36.1.0+ |
| Android NDK | r27c | 用于编译 native 代码 (llama.cpp, stable-diffusion.cpp) |
| CMake | 3.22.1+ | NDK 自带 |
| Gradle | 8.7 | 项目自带 wrapper |
| ADB | - | Android SDK platform-tools |

## 1. 环境配置

### 1.1 配置 gradle.properties

编辑 `Anime Companion/gradle.properties`，设置 JDK 路径：

```properties
# 替换为你本机的 JDK 路径，例如：
# Windows:  D:/Android/Android Studio/jbr
# macOS:    /Applications/Android Studio.app/Contents/jbr/Contents/Home
org.gradle.java.home=D:/Android/Android Studio/jbr
```

### 1.2 配置 local.properties

编辑 `Anime Companion/local.properties`，设置 Android SDK 路径：

```properties
# 替换为你本机的 Android SDK 路径，例如：
# Windows:  C:\\Users\\你的用户名\\AppData\\Local\\Android\\Sdk
# macOS:    /Users/你的用户名/Library/Android/sdk
sdk.dir=C:\\Users\\<你的用户名>\\AppData\\Local\\Android\\Sdk
```

## 2. 编译

```bash
cd Anime Companion

# Windows
set JAVA_HOME=<你的JDK路径，如 D:\Android\Android Studio\jbr>
gradlew.bat assembleDebug

# macOS / Linux
export JAVA_HOME=<你的JDK路径>
./gradlew assembleDebug
```

编译产物：`app/build/outputs/apk/debug/app-debug.apk`

> 首次编译需要下载依赖和编译 native 代码，耗时约 10-15 分钟。后续增量编译约 1-2 分钟。

## 3. 安装到手机

### 3.1 连接手机

1. 手机开启 **开发者选项** → **USB 调试**
2. USB 连接电脑
3. 手机上点击 **允许 USB 调试**

验证连接：

```bash
adb devices
# 应显示：xxxxxxxx    device
```

### 3.2 安装 APK

```bash
# 覆盖安装（已安装时）
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 首次安装（签名不同时需先卸载）
adb uninstall com.companion.chat
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 4. 推送模型

所有模型源文件存放在项目根目录的 `models/` 文件夹下，推送到手机的 `/sdcard/Android/data/com.companion.chat/files/models/` 目录。

> 以下命令均在项目根目录执行，`models/` 为相对路径。

### 4.1 创建目录

```bash
adb shell mkdir -p /sdcard/Android/data/com.companion.chat/files/models/tts/moss-tts-nano/tts
adb shell mkdir -p /sdcard/Android/data/com.companion.chat/files/models/tts/moss-tts-nano/audio_tokenizer
adb shell mkdir -p /sdcard/Android/data/com.companion.chat/files/models/asr/sensevoice
```

### 4.2 推送 GGUF 主模型（llama.cpp 后端）

```bash
adb push models/<主模型文件名>.gguf \
    /sdcard/Android/data/com.companion.chat/files/models/
```

### 4.3 推送 mmproj（多模态投影器）

```bash
adb push models/mmproj-Gemma-4-E2B-Uncensored-HauhauCS-Aggressive-f16.gguf \
    /sdcard/Android/data/com.companion.chat/files/models/
```

### 4.4 推送 LiteRT 模型

```bash
adb push models/gemma-4-E2B-it.litertlm \
    /sdcard/Android/data/com.companion.chat/files/models/
```

### 4.5 推送 MOSS TTS 语音合成模型

TTS 模型文件（推送到 `tts/moss-tts-nano/tts/`）：

```bash
adb push models/MOSS-TTS-Nano-100M-ONNX/moss_tts_prefill.onnx \
    /sdcard/Android/data/com.companion.chat/files/models/tts/moss-tts-nano/tts/
adb push models/MOSS-TTS-Nano-100M-ONNX/moss_tts_decode_step.onnx \
    /sdcard/Android/data/com.companion.chat/files/models/tts/moss-tts-nano/tts/
adb push models/MOSS-TTS-Nano-100M-ONNX/moss_tts_local_decoder.onnx \
    /sdcard/Android/data/com.companion.chat/files/models/tts/moss-tts-nano/tts/
adb push models/MOSS-TTS-Nano-100M-ONNX/moss_tts_local_cached_step.onnx \
    /sdcard/Android/data/com.companion.chat/files/models/tts/moss-tts-nano/tts/
adb push models/MOSS-TTS-Nano-100M-ONNX/moss_tts_local_fixed_sampled_frame.onnx \
    /sdcard/Android/data/com.companion.chat/files/models/tts/moss-tts-nano/tts/
adb push models/MOSS-TTS-Nano-100M-ONNX/moss_tts_global_shared.data \
    /sdcard/Android/data/com.companion.chat/files/models/tts/moss-tts-nano/tts/
adb push models/MOSS-TTS-Nano-100M-ONNX/moss_tts_local_shared.data \
    /sdcard/Android/data/com.companion.chat/files/models/tts/moss-tts-nano/tts/
adb push models/MOSS-TTS-Nano-100M-ONNX/tokenizer.model \
    /sdcard/Android/data/com.companion.chat/files/models/tts/moss-tts-nano/tts/
adb push models/MOSS-TTS-Nano-100M-ONNX/tts_browser_onnx_meta.json \
    /sdcard/Android/data/com.companion.chat/files/models/tts/moss-tts-nano/tts/
adb push models/MOSS-TTS-Nano-100M-ONNX/browser_poc_manifest.json \
    /sdcard/Android/data/com.companion.chat/files/models/tts/moss-tts-nano/tts/
```

Audio Tokenizer 文件（推送到 `tts/moss-tts-nano/audio_tokenizer/`）：

```bash
adb push models/MOSS-Audio-Tokenizer-Nano-ONNX/moss_audio_tokenizer_encode.onnx \
    /sdcard/Android/data/com.companion.chat/files/models/tts/moss-tts-nano/audio_tokenizer/
adb push models/MOSS-Audio-Tokenizer-Nano-ONNX/moss_audio_tokenizer_encode.data \
    /sdcard/Android/data/com.companion.chat/files/models/tts/moss-tts-nano/audio_tokenizer/
adb push models/MOSS-Audio-Tokenizer-Nano-ONNX/moss_audio_tokenizer_decode_full.onnx \
    /sdcard/Android/data/com.companion.chat/files/models/tts/moss-tts-nano/audio_tokenizer/
adb push models/MOSS-Audio-Tokenizer-Nano-ONNX/moss_audio_tokenizer_decode_step.onnx \
    /sdcard/Android/data/com.companion.chat/files/models/tts/moss-tts-nano/audio_tokenizer/
adb push models/MOSS-Audio-Tokenizer-Nano-ONNX/moss_audio_tokenizer_decode_shared.data \
    /sdcard/Android/data/com.companion.chat/files/models/tts/moss-tts-nano/audio_tokenizer/
adb push models/MOSS-Audio-Tokenizer-Nano-ONNX/codec_browser_onnx_meta.json \
    /sdcard/Android/data/com.companion.chat/files/models/tts/moss-tts-nano/audio_tokenizer/
```

### 4.6 推送 SenseVoice ASR 语音识别模型

```bash
adb push models/model.int8.onnx \
    /sdcard/Android/data/com.companion.chat/files/models/asr/sensevoice/
adb push models/tokens.txt \
    /sdcard/Android/data/com.companion.chat/files/models/asr/sensevoice/
adb push models/silero_vad.onnx \
    /sdcard/Android/data/com.companion.chat/files/models/asr/sensevoice/
```

## 5. 验证

1. 打开 Anime Companion App
2. 进入 **设置** → **模型配置**，检查各模型路径显示绿色状态
3. 进入 **语音设置**，检查 MOSS TTS 和 SenseVoice ASR 状态正常
4. 测试聊天和语音功能

## 6. 常见问题

### 编译报错 `JAVA_HOME not set`

设置环境变量后再编译：

```cmd
:: 替换为你本机的 JDK 路径
set JAVA_HOME=D:\Android\Android Studio\jbr
```

### 安装报错 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`

签名不一致，需先卸载旧版本：

```bash
adb uninstall com.companion.chat
```

### ASR 报错 `dlopen failed: cannot locate symbol "OrtGetApiBase"`

sherpa-onnx 依赖 ONNX Runtime，需确保 `libonnxruntime.so` 先加载。项目中已通过 `SherpaOnnxNativeLoader` 处理，如仍有问题请 clean 后重新编译：

```bash
gradlew.bat clean assembleDebug
```

### 模型推送后 App 不识别

重启 App 让它重新扫描模型文件。
