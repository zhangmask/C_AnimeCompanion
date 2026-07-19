# MNN LLM 后端集成实施方案

## 概要 (Summary)

将 MNN LLM 推理后端集成到 CompanionChat 应用中，支持 Qwen3.5-0.8B-MNN 和 Qwen3.5-2B-MNN 两个多模态模型。包括：重新编译 libMNN.so 启用 LLM + 视觉 + ARM 指令集优化、编写 JNI 桥接代码（含图片输入）、创建 Kotlin 推理引擎、更新设置页面 UI 添加 MNN 后端选项。

## 当前状态分析 (Current State Analysis)

### 已完成

* MNN 模型已下载到 `C:\Users\72952\OneDrive\Desktop\ui\models\qwen-mnn\Qwen3.5-0.8B-MNN\` 和 `Qwen3.5-2B-MNN\`

* 每个模型目录包含：`config.json`(运行时配置)、`llm_config.json`(模型架构，`is_visual:true`)、`llm.mnn`、`llm.mnn.weight`、`tokenizer.txt`、`visual.mnn`、`visual.mnn.weight`

* MNN 源码位于 `C:\Users\72952\OneDrive\Desktop\ui\reference\MNN`

* 现有 `libMNN.so` 仅含 TTS 符号，**不含 LLM 符号**，需重新编译

* 现有 `mnn_jni.cpp` 仅用于 TTS 推理

* 现有 `LlamaCppInferenceEngine` 已支持图片输入（`ChatMessage.images: List<Uri>` → `readImageBytes` → 传给 native），MNN LLM 需提供同等能力

### 现有架构

* `ModelRuntime` 枚举：`LITERT_LM`、`LLAMA_CPP_GGUF`、`CUSTOM_API`

* `InferenceEngine` 接口：`initialize`、`sendMessageStream`、`cancel`、`release` 等

* `InferenceEngineFactory` 根据 `ModelRuntime` 创建对应引擎

* `ModelConfigScreen.kt` 显示后端选项（单选 RadioButton）

* `ModelConfigRepository` 持久化配置到 SharedPreferences

* JNI 模式：Kotlin `external fun` ↔ C++ `extern "C" JNIEXPORT` ↔ 加载 `.so` 库

* 图片输入模式（参考 `LlamaCppInferenceEngine`）：Kotlin 读取 `Uri` → `ByteArray` → JNI 传 `Array<ByteArray>` → C++ 解码

* MNN 参考实现：`C:\Users\72952\OneDrive\Desktop\ui\reference\MNN\apps\Android\MnnLlmChat\` 提供 LLM JNI 流式输出模式（但图片加载在 Android 上是 stub，需自行实现）

### 关键约束

* 用户明确要求：**不用 GPU**（Vulkan/OpenCL 都比 CPU 慢）

* 用户要求：**开启 ARM 指令集优化**（`MNN_ARM82`、`MNN_CPU_WEIGHT_DEQUANT_GEMM`）

* 用户要求：**开启视觉**（Qwen3.5 是多模态，要能发图片）

* 用户重视响应速度（延迟超 10 秒不可接受）→ 应禁用 thinking 模式

* libMNN.so 被 TTS 和 LLM 共享 → 重新编译后 TTS 仍需正常工作

### MNN LLM 配置项说明（源码 `llmconfig.hpp` 验证）

* `backend_type`: "cpu"（用户指定）

* `thread_num`: 4（用户指定，固定 4 线程大核）

* `use_mmap`: true（用户指定，mmap 加载权重减少内存）

* `memory`: "low"（用户指定，低内存模式）

* `quant_qkv`: 8（MNN 默认值，源码 `llm.cpp:132` `config_.value("quant_qkv", 8)`，无需显式设置）

* `enable_thinking`: false（禁用思考模式加速响应，通过 `set_config` 设置）

## 实施步骤 (Proposed Changes)

### 步骤 1：重新编译 libMNN.so（启用 LLM + 视觉 + ARM 优化）

**文件**: `C:\Users\72952\OneDrive\Desktop\ui\reference\MNN\build_android_lib.bat`

**修改内容**: 更新 CMake 编译参数

```bat
"%CMAKE%\cmake.exe" -S "%MNN%" -B "%BUILD%" -G "Ninja" ^
    -DCMAKE_MAKE_PROGRAM="%CMAKE%\ninja.exe" ^
    -DCMAKE_TOOLCHAIN_FILE="%NDK%\build\cmake\android.toolchain.cmake" ^
    -DANDROID_ABI=arm64-v8a ^
    -DANDROID_PLATFORM=android-24 ^
    -DMNN_BUILD_SHARED_LIBS=ON ^
    -DMNN_SEP_BUILD=OFF ^
    -DCMAKE_BUILD_TYPE=Release ^
    -DMNN_OPENCL=OFF ^
    -DMNN_VULKAN=OFF ^
    -DMNN_CUDA=OFF ^
    -DMNN_ARM82=ON ^
    -DMNN_BUILD_LLM=true ^
    -DMNN_BUILD_LLM_OMNI=ON ^
    -DMNN_CPU_WEIGHT_DEQUANT_GEMM=true ^
    -DMNN_BUILD_OPENCV=ON ^
    -DMNN_IMGCODECS=ON ^
    -DLLM_SUPPORT_VISION=true ^
    -DMNN_BUILD_CONVERTER=OFF ^
    -DMNN_BUILD_TRAIN=OFF ^
    -DMNN_BUILD_DEMO=OFF ^
    -DMNN_BUILD_TEST=OFF ^
    -DCMAKE_SHARED_LINKER_FLAGS="-Wl,-z,max-page-size=16384"
```

**说明**:

* `MNN_VULKAN=OFF` / `MNN_OPENCL=OFF`：按用户要求禁用 GPU

* `MNN_ARM82=ON`：启用 ARM v8.2 FP16/INT8 指令集

* `MNN_BUILD_LLM=true`：启用 LLM 库编译（自动开启 `MNN_LOW_MEMORY` 和 `MNN_SUPPORT_TRANSFORMER_FUSE`）

* `MNN_BUILD_LLM_OMNI=ON`：启用 Omni 类（多模态），`is_visual=true` 时 MNN 创建 `Omni` 而非 `Llm`

* `MNN_CPU_WEIGHT_DEQUANT_GEMM=true`：INT4 权重反量化 GEMM 优化

* `MNN_BUILD_OPENCV=ON` + `MNN_IMGCODECS=ON`：启用 `cv::imdecode` 解码图片字节流

* `LLM_SUPPORT_VISION=true`：LLM 编译时启用视觉处理代码

* `MNN_SEP_BUILD=OFF`：将 LLM 代码编入 libMNN.so（不生成独立 libllm.so）

* `CMAKE_SHARED_LINKER_FLAGS`：Android 16KB page size 兼容（参考 MnnLlmChat build.sh）

**编译后**: 将 `build_android\libMNN.so` 复制到 `app\src\main\jniLibs\arm64-v8a\libMNN.so`（覆盖现有版本）

**验证**: TTS 仍正常工作（LLM 符号是新增的，不影响 TTS 既有符号）

### 步骤 2：创建 MNN LLM JNI 桥接代码

**新文件**: `c:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\cpp\mnn_llm_jni.cpp`

**设计要点**:

* 使用 `Llm::createLLM(config_path)` 创建实例（`is_visual=true` 时内部创建 `Omni`）

* 使用自定义 `std::streambuf`（`LlmStreamBuffer`）实现流式输出

* 通过 JNI 回调将每个 token chunk 发送到 Kotlin 层

* 支持取消生成（通过 `stop_requested_` 标志 + `Llm::stoped()` 检查）

* 禁用 thinking 模式（`set_config("{\"jinja\":{\"context\":{\"enable_thinking\":false}}}")`）

* 图片输入：`nativeGenerate` 接收 `imageBytesArray`，用 `cv::imdecode` 解码为 VARP，构建 `MultimodalPrompt`

**JNI 函数签名**:

```cpp
// 创建 LLM 实例并加载模型
// configPath: 指向模型目录下的 config.json
// 返回: Llm* 指针（0 表示失败）
JNIEXPORT jlong JNICALL Java_com_companion_chat_engine_MnnLlmJni_nativeCreate(
    JNIEnv*, jobject, jstring configPath);

// 流式生成回复（纯文本）
JNIEXPORT void JNICALL Java_com_companion_chat_engine_MnnLlmJni_nativeGenerate(
    JNIEnv*, jobject, jlong handle, jobjectArray roles, jobjectArray contents,
    jint maxTokens, jfloat temperature, jint topK, jfloat topP, jobject callback);

// 流式生成回复（带图片，最后一条 user 消息附带图片）
// imageBytes: 图片字节数组数组（每张图为一个 byte[]）
JNIEXPORT void JNICALL Java_com_companion_chat_engine_MnnLlmJni_nativeGenerateWithImages(
    JNIEnv*, jobject, jlong handle, jobjectArray roles, jobjectArray contents,
    jobjectArray imageBytes, jint maxTokens, jfloat temperature, jint topK, jfloat topP,
    jobject callback);

// 重置 KV 缓存（切换会话时调用）
JNIEXPORT void JNICALL Java_com_companion_chat_engine_MnnLlmJni_nativeReset(
    JNIEnv*, jobject, jlong handle);

// 取消当前生成
JNIEXPORT void JNICALL Java_com_companion_chat_engine_MnnLlmJni_nativeCancel(
    JNIEnv*, jobject, jlong handle);

// 释放 LLM 实例
JNIEXPORT void JNICALL Java_com_companion_chat_engine_MnnLlmJni_nativeRelease(
    JNIEnv*, jobject, jlong handle);
```

**关键实现细节**:

1. **LlmStreamBuffer**: 继承 `std::streambuf`，重写 `xsputn` 将数据通过回调发出（参考 `MnnLlmChat/llm_stream_buffer.hpp`）

```cpp
class LlmStreamBuffer : public std::streambuf {
public:
    using CallBack = std::function<void(const char*, size_t)>;
    explicit LlmStreamBuffer(CallBack cb) : callback_(std::move(cb)) {}
protected:
    std::streamsize xsputn(const char* s, std::streamsize n) override {
        if (callback_) callback_(s, n);
        return n;
    }
private:
    CallBack callback_;
};
```

1. **流式生成循环**（参考 `llm_demo.cpp` 和 `llm_session.cpp`）:

```cpp
// 1. 构建输出流（带回调）
LlmStreamBuffer stream_buffer([&](const char* str, size_t len) {
    // 通过 JNI 回调发送 chunk
    jstring javaStr = env->NewStringUTF(std::string(str, len).c_str());
    jboolean cancel = env->CallBooleanMethod(callback, onTokenMethod, javaStr);
    env->DeleteLocalRef(javaStr);
    if (cancel) stop_requested = true;
});
std::ostream ostream(&stream_buffer);

// 2. 调用 response（max_new_tokens=0 表示仅 prefill）
if (has_images) {
    llm->response(multimodal_prompt, &ostream, nullptr, 0);
} else {
    llm->response(chat_messages, &ostream, nullptr, 0);
}

// 3. 循环 generate(1) 逐 token 生成
while (!stop_requested && !llm->stoped() && gen_count < maxTokens) {
    llm->generate(1);
    gen_count++;
}
```

1. **图片解码**（使用 MNN cv::imdecode）:

```cpp
#include "cv/imgcodecs.hpp"
// 将 jbyteArray 转为 vector<uint8_t>，调用 cv::imdecode
jbyte* data = env->GetByteArrayElements(imageBytes[i], nullptr);
jsize size = env->GetArrayLength(imageBytes[i]);
std::vector<uint8_t> buf(reinterpret_cast<uint8_t*>(data), reinterpret_cast<uint8_t*>(data) + size);
auto image_var = MNN::CV::imdecode(buf, MNN::CV::IMREAD_COLOR);
env->ReleaseByteArrayElements(imageBytes[i], data, JNI_ABORT);
// 构建 PromptImagePart
PromptImagePart part; part.image_data = image_var; part.width = 0; part.height = 0;
multimodal_prompt.images["image_" + std::to_string(i)] = part;
```

1. **配置覆盖**: 创建 Llm 后调用 `set_config()` 设置：

   * `{"jinja":{"context":{"enable_thinking":false}}}` - 禁用思考模式

   * `{"max_new_tokens":N}` - 从 EngineConfig 读取

2. **线程绑定**: 复用 `mnn_jni.cpp` 中的 `bindToBigCoresIfNeeded()` 逻辑绑定大核（CPU 4-7）

3. **config.json 修改**: 确保模型目录下的 `config.json` 包含用户指定参数：

   * `backend_type: "cpu"`

   * `thread_num: 4`

   * `use_mmap: true`

   * `memory: "low"`

   * `precision: "low"`（INT4 量化模型用 low）

   * `mllm.backend_type: "cpu"`（视觉模型也用 CPU）

### 步骤 3：创建 MnnLlmJni.kt（Kotlin JNI 接口）

**新文件**: `c:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java\com\companion\chat\engine\MnnLlmJni.kt`

```kotlin
package com.companion.chat.engine

import android.util.Log

object MnnLlmJni {
    private const val TAG = "MnnLlmJni"
    private var loaded = false

    fun ensureLoaded() {
        if (!loaded) {
            try {
                System.loadLibrary("MNN")
                System.loadLibrary("mnn_llm_jni")
                loaded = true
                Log.i(TAG, "MNN LLM JNI loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load MNN LLM: ${e.message}")
            }
        }
    }

    fun isLoaded(): Boolean = loaded

    interface TokenCallback {
        fun onToken(text: String): Boolean  // 返回 true 表示取消
    }

    external fun nativeCreate(configPath: String): Long
    external fun nativeGenerate(
        handle: Long,
        roles: Array<String>,
        contents: Array<String>,
        maxTokens: Int,
        temperature: Float,
        topK: Int,
        topP: Float,
        callback: TokenCallback
    ): Boolean
    external fun nativeGenerateWithImages(
        handle: Long,
        roles: Array<String>,
        contents: Array<String>,
        imageBytes: Array<ByteArray>,
        maxTokens: Int,
        temperature: Float,
        topK: Int,
        topP: Float,
        callback: TokenCallback
    ): Boolean
    external fun nativeReset(handle: Long)
    external fun nativeCancel(handle: Long)
    external fun nativeRelease(handle: Long)
}
```

### 步骤 4：创建 MnnLlmInferenceEngine.kt（推理引擎实现）

**新文件**: `c:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java\com\companion\chat\engine\MnnLlmInferenceEngine.kt`

**设计**: 仿照 `LlamaCppInferenceEngine.kt` 模式

* 单线程 `ExecutorService` 运行推理（避免并发）

* `callbackFlow` 实现流式输出

* 复用 `toPromptMessages()` 逻辑（取最近 6 条消息）

* 复用 `readImageBytes(uri)` 读取图片

* `rebuildConversation` / `replayMessages` 调用 `nativeReset` 重置 KV 缓存

**关键方法实现**:

```kotlin
class MnnLlmInferenceEngine(private val context: Context) : InferenceEngine {
    companion object {
        private const val TAG = "MnnLlmEngine"
        private val StopMarkers = listOf("<|im_end|>")
    }

    private val runtimeExecutor = Executors.newSingleThreadExecutor { ... }
    private val _state = MutableStateFlow<InferenceState>(InferenceState.Idle)
    private var handle: Long = 0L
    private var currentConfig: EngineConfig? = null

    override suspend fun initialize(config: EngineConfig) = withContext(runtimeDispatcher) {
        MnnLlmJni.ensureLoaded()
        // modelPath 指向模型目录，config.json 在其中
        val configPath = File(config.modelPath, "config.json").absolutePath
        // 验证文件存在
        if (!File(configPath).exists()) {
            _state.value = InferenceState.Error("MNN config.json 不存在: $configPath")
            return@withContext
        }
        handle = MnnLlmJni.nativeCreate(configPath)
        if (handle == 0L) {
            _state.value = InferenceState.Error("MNN 模型加载失败")
            return@withContext
        }
        currentConfig = config
        _state.value = InferenceState.Ready
    }

    override fun sendMessageStream(messages: List<ChatMessage>): Flow<String> = callbackFlow {
        if (handle == 0L) { close(IllegalStateException("MNN 引擎未初始化")); return@callbackFlow }

        val promptMessages = messages.toPromptMessages()
        val lastUserMessage = promptMessages.lastOrNull { it.role == MessageRole.USER }
        val imageBytes = lastUserMessage?.images.orEmpty().mapNotNull(::readImageBytes)

        val roles = promptMessages.map { it.role.toNativeRole() }.toTypedArray()
        val contents = promptMessages.map { it.content }.toTypedArray()
        val config = currentConfig ?: EngineConfig(modelPath = "")

        _state.value = InferenceState.Generating()
        val job = launch(runtimeDispatcher) {
            try {
                val callback = object : MnnLlmJni.TokenCallback {
                    override fun onToken(text: String): Boolean {
                        trySend(text)
                        return false  // 返回 true 取消
                    }
                }
                if (imageBytes.isNotEmpty()) {
                    MnnLlmJni.nativeGenerateWithImages(
                        handle, roles, contents, imageBytes.toTypedArray(),
                        config.maxTokens, config.temperature, config.topK, config.topP, callback
                    )
                } else {
                    MnnLlmJni.nativeGenerate(
                        handle, roles, contents,
                        config.maxTokens, config.temperature, config.topK, config.topP, callback
                    )
                }
            } catch (e: CancellationException) {
                MnnLlmJni.nativeCancel(handle)
                throw e
            } catch (e: Exception) {
                trySend("[推理出错: ${e.message}]")
            } finally {
                _state.value = InferenceState.Ready
                close()
            }
        }
        awaitClose { job.cancel(); MnnLlmJni.nativeCancel(handle) }
    }

    override suspend fun rebuildConversation(systemPrompt: String): Boolean = withContext(runtimeDispatcher) {
        MnnLlmJni.nativeReset(handle)
        currentConfig = currentConfig?.copy(systemPrompt = systemPrompt)
        true
    }

    override suspend fun replayMessages(messages: List<ChatMessage>): Boolean = withContext(runtimeDispatcher) {
        MnnLlmJni.nativeReset(handle)
        true
    }

    override fun cancel() = MnnLlmJni.nativeCancel(handle)
    override fun getCurrentConfig(): EngineConfig? = currentConfig
    override fun release() {
        if (handle != 0L) MnnLlmJni.nativeRelease(handle)
        handle = 0L
        runtimeExecutor.shutdown()
        _state.value = InferenceState.Idle
    }
}
```

**复用 LlamaCppInferenceEngine 的辅助方法**:

* `toPromptMessages()`: 取最近 6 条非流式消息

* `readImageBytes(uri)`: 从 ContentResolver 读取图片字节

* `MessageRole.toNativeRole()`: USER→"user", ASSISTANT→"assistant", SYSTEM→"system"

### 步骤 5：添加 MNN\_LLM 到 ModelRuntime 枚举

**文件**: `CompanionChat\app\src\main\java\com\companion\chat\data\engine\InferenceEngine.kt`

```kotlin
enum class ModelRuntime {
    LITERT_LM,
    LLAMA_CPP_GGUF,
    MNN_LLM,      // 新增
    CUSTOM_API
}
```

### 步骤 6：更新 InferenceEngineFactory

**文件**: `CompanionChat\app\src\main\java\com\companion\chat\engine\InferenceEngineFactory.kt`

```kotlin
fun create(runtime: ModelRuntime): InferenceEngine {
    return when (runtime) {
        ModelRuntime.LLAMA_CPP_GGUF -> LlamaCppInferenceEngine(context)
        ModelRuntime.LITERT_LM -> LiteRTLMInferenceEngine(context)
        ModelRuntime.MNN_LLM -> MnnLlmInferenceEngine(context)  // 新增
        ModelRuntime.CUSTOM_API -> CustomApiInferenceEngine()
    }
}
```

### 步骤 7：更新 DefaultModelConfig 和 ModelConfigRepository

**文件**: `CompanionChat\app\src\main\java\com\companion\chat\data\engine\DefaultModelConfig.kt`

新增 MNN 模型默认目录名：

```kotlin
object DefaultModelConfig {
    // ... 现有字段 ...
    const val MnnModelDir = "qwen-mnn"
    const val MnnModel0_8B = "Qwen3.5-0.8B-MNN"
    const val MnnModel2B = "Qwen3.5-2B-MNN"
}
```

**文件**: `CompanionChat\app\src\main\java\com\companion\chat\data\engine\ModelConfigRepository.kt`

更新 `resolveModelPath` 支持 MNN（返回目录路径而非文件路径）：

```kotlin
fun resolveModelPath(config: ModelConfig = getConfig()): String {
    if (config.useCustomApi) return ""
    val explicitPath = config.modelPath.trim()
    if (explicitPath.isNotBlank()) return explicitPath

    val fileName = when (config.runtime) {
        ModelRuntime.LLAMA_CPP_GGUF -> DefaultModelConfig.GgufModelFileName
        ModelRuntime.LITERT_LM -> DefaultModelConfig.LiteRtModelFileName
        ModelRuntime.MNN_LLM -> ""  // MNN 使用目录
        ModelRuntime.CUSTOM_API -> ""
    }
    if (fileName.isEmpty()) {
        if (config.runtime == ModelRuntime.MNN_LLM) {
            val externalDir = appContext.getExternalFilesDir(DefaultModelConfig.ExternalModelsDir)
            return File(externalDir, "${DefaultModelConfig.MnnModelDir}/${DefaultModelConfig.MnnModel0_8B}").absolutePath
        }
        return ""
    }
    // ... 现有逻辑 ...
}
```

`toEngineConfig` 无需修改（modelPath 已是目录路径）。

### 步骤 8：更新 CMakeLists.txt 构建新 JNI 库

**文件**: `CompanionChat\app\src\main\cpp\CMakeLists.txt`

在现有 MNN JNI 部分后追加：

```cmake
# ── MNN LLM JNI (LLM 推理) ──
if (ANDROID_ABI STREQUAL "arm64-v8a")
    set(MNN_LLM_INCLUDE_DIR "C:/Users/72952/OneDrive/Desktop/ui/reference/MNN/transformers/llm/engine/include")
    set(MNN_CV_INCLUDE_DIR "C:/Users/72952/OneDrive/Desktop/ui/reference/MNN/tools/cv/include")

    add_library(mnn_llm_jni SHARED
        mnn_llm_jni.cpp
    )
    target_include_directories(mnn_llm_jni PRIVATE
        "${MNN_INCLUDE_DIR}"
        "${MNN_INCLUDE_DIR}/MNN"
        "${MNN_INCLUDE_DIR}/expr"
        "${MNN_LLM_INCLUDE_DIR}"
        "${MNN_CV_INCLUDE_DIR}"
    )
    target_link_libraries(mnn_llm_jni
        MNN_SHARED
        android
        log
    )
    message(STATUS "MNN LLM JNI: compiling mnn_llm_jni.cpp")
endif()
```

### 步骤 9：更新设置页面 UI

**文件**: `CompanionChat\app\src\main\java\com\companion\chat\ui\settings\ModelConfigScreen.kt`

在 LiteRT-LM 选项后添加 MNN LLM 选项：

```kotlin
ModelRuntimeOptionItem(
    title = "MNN LLM",
    description = Strings.txt(StringsKey.model_backend_mnn_desc),
    selected = !modelConfig.useCustomApi && modelConfig.runtime == ModelRuntime.MNN_LLM,
    onClick = {
        viewModel.setRuntime(ModelRuntime.MNN_LLM)
        onModelConfigChanged()
    }
)
```

**MNN 模式下的 UI 调整**:

* GPU 开关条件改为 `runtime == LITERT_LM`（已有条件，MNN 不显示 GPU 开关）

* mmproj 状态仅在 `runtime == LLAMA_CPP_GGUF` 时显示

* modelPath 提示更新为目录路径格式（MNN 模式下显示模型目录）

### 步骤 10：更新 Strings.kt 添加本地化文本

**文件**: `CompanionChat\app\src\main\java\com\companion\chat\locale\Strings.kt`

新增 `model_backend_mnn_desc`:

* 中文: "MNN 推理框架，ARM 优化，支持 Qwen3.5 多模态 INT4 量化模型"

* 英文: "MNN inference framework with ARM optimization, supports Qwen3.5 multimodal INT4 quantized models"

### 步骤 11：修改模型 config.json 确保参数正确

**文件**: `C:\Users\72952\OneDrive\Desktop\ui\models\qwen-mnn\Qwen3.5-0.8B-MNN\config.json`
**文件**: `C:\Users\72952\OneDrive\Desktop\ui\models\qwen-mnn\Qwen3.5-2B-MNN\config.json`

更新为用户指定参数：

```json
{
    "max_new_tokens": 8192,
    "llm_model": "llm.mnn",
    "llm_weight": "llm.mnn.weight",
    "backend_type": "cpu",
    "thread_num": 4,
    "precision": "low",
    "memory": "low",
    "use_mmap": true,
    "sampler_type": "mixed",
    "mixed_samplers": ["penalty", "topK", "topP", "min_p", "temperature"],
    "penalty": 1.1,
    "temperature": 1.0,
    "topP": 0.95,
    "topK": 20,
    "min_p": 0,
    "mllm": {
        "backend_type": "cpu",
        "thread_num": 4,
        "precision": "normal",
        "memory": "low"
    },
    "jinja": {
        "context": {
            "enable_thinking": false
        }
    }
}
```

**变更**:

* 新增 `"use_mmap": true`（用户指定）

* `"enable_thinking"` 改为 `false`（加速响应，用户重视速度）

* 其他参数保持现有值（backend\_type/thread\_num/precision/memory 已符合用户要求）

## 假设与决策 (Assumptions & Decisions)

1. **禁用 thinking 模式**: Qwen3.5 默认 `enable_thinking: true` 会输出思考标签内容，增加延迟。通过 config.json 和 `set_config` 双重禁用，确保快速响应。
2. **视觉模型编译**: 启用 `MNN_BUILD_LLM_OMNI`、`MNN_BUILD_OPENCV`、`LLM_SUPPORT_VISION`，使 `Omni` 类可用，支持图片输入。`cv::imdecode` 用于解码图片字节流。
3. **图片输入路径**: Kotlin 读取 Uri→ByteArray → JNI 传 `Array<ByteArray>` → C++ 用 `cv::imdecode` 解码为 VARP → 构建 `MultimodalPrompt` → `llm->response(multimodal_prompt)`。复用现有 `ChatMessage.images` 字段和 UI。
4. **模型部署**: 用户需手动通过 adb push 将模型目录推送到手机：`/sdcard/Android/data/com.companion.chat/files/models/qwen-mnn/`。不打包进 APK（体积过大）。
5. **libMNN.so 共享**: 重新编译的 libMNN.so 同时包含 TTS、LLM、视觉、CV 符号，TTS 代码无需修改。
6. **KV 缓存管理**: 每次切换会话或重建上下文时调用 `nativeReset` 清空 KV 缓存。
7. **quant\_qkv**: MNN 默认值 8（源码 `llm.cpp:132`），无需显式设置。
8. **跳过 max\_new\_code**: 用户确信不是关键配置，且当前响应已够快，不深究。

## 验证步骤 (Verification)

1. **编译验证**:

   * 运行 `build_android_lib.bat` 成功生成 libMNN.so（含 LLM + 视觉 + CV 符号）

   * Gradle 构建成功生成 `libmnn_llm_jni.so`

2. **TTS 回归测试**:

   * 安装新 APK，进入"小夏对话"，点击播放按钮

   * 验证 TTS 仍正常工作（libMNN.so 重新编译未破坏 TTS）

3. **MNN LLM 文本对话测试**:

   * adb push 模型到手机：`adb push models\qwen-mnn\Qwen3.5-0.8B-MNN /sdcard/Android/data/com.companion.chat/files/models/qwen-mnn/`

   * 设置页面选择"MNN LLM"后端

   * 发送文本消息，验证流式输出正常

   * 验证响应时间 < 10 秒（首 token < 3 秒）

4. **MNN LLM 图片输入测试**:

   * 在对话中发送图片 + 文字（复用现有图片选择 UI）

   * 验证 AI 能识别图片内容并回答

5. **取消功能测试**:

   * 生成过程中点击停止按钮，验证立即停止

6. **会话切换测试**:

   * 切换不同会话，验证 KV 缓存正确重置，不串味

7. **错误处理测试**:

   * 模型路径错误时显示友好错误信息

   * libMNN.so 加载失败时优雅降级

## 文件清单

| 操作 | 文件路径                                                                                      |
| -- | ----------------------------------------------------------------------------------------- |
| 修改 | `reference\MNN\build_android_lib.bat`                                                     |
| 修改 | `models\qwen-mnn\Qwen3.5-0.8B-MNN\config.json`                                            |
| 修改 | `models\qwen-mnn\Qwen3.5-2B-MNN\config.json`                                              |
| 新建 | `CompanionChat\app\src\main\cpp\mnn_llm_jni.cpp`                                          |
| 新建 | `CompanionChat\app\src\main\java\com\companion\chat\engine\MnnLlmJni.kt`                  |
| 新建 | `CompanionChat\app\src\main\java\com\companion\chat\engine\MnnLlmInferenceEngine.kt`      |
| 修改 | `CompanionChat\app\src\main\java\com\companion\chat\data\engine\InferenceEngine.kt`       |
| 修改 | `CompanionChat\app\src\main\java\com\companion\chat\engine\InferenceEngineFactory.kt`     |
| 修改 | `CompanionChat\app\src\main\java\com\companion\chat\data\engine\DefaultModelConfig.kt`    |
| 修改 | `CompanionChat\app\src\main\java\com\companion\chat\data\engine\ModelConfigRepository.kt` |
| 修改 | `CompanionChat\app\src\main\cpp\CMakeLists.txt`                                           |
| 修改 | `CompanionChat\app\src\main\java\com\companion\chat\ui\settings\ModelConfigScreen.kt`     |
| 修改 | `CompanionChat\app\src\main\java\com\companion\chat\locale\Strings.kt`                    |
| 替换 | `CompanionChat\app\src\main\jniLibs\arm64-v8a\libMNN.so`（编译后复制）                           |

