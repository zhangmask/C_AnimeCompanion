# MOSS-TTS-Nano MNN 移植 + ARM 优化部署计划

## 一、概述

### 目标
将 MOSS-TTS-Nano 的 6 个 ONNX 模型转换为 MNN 格式，利用 ARM 指令集加速推理，实现移动端低延迟语音合成。

### 总体流程

```
ONNX (FP32, 728MB)
  │
  ├─ Step 1: MNNConvert --fp16 → 6 个 .mnn (FP16 权重, ~363MB)
  │
  ├─ Step 2: prefill + decode_step 动态 INT8 (Weight-Only, ~258MB)
  │
  ├─ Step 3: CMake 编译 MNN 引擎 + ARM 优化选项
  │
  ├─ Step 4: Android 集成 MNN Runtime
  │
  └─ Step 5: adb 推送 + 测试一条消息合成
```

### 输出目录
```
C:\Users\72952\OneDrive\Desktop\ui\models\lianghua_moss\
├── moss_tts_prefill.mnn             # FP16 or INT8
├── moss_tts_decode_step.mnn          # FP16 or INT8
├── moss_tts_local_cached_step.mnn    # FP16 (精度敏感)
├── moss_tts_local_decoder.mnn        # FP16 (精度敏感)
├── moss_tts_local_fixed_sampled_frame.mnn  # FP16
├── moss_audio_tokenizer_encode.mnn          # FP16
├── moss_audio_tokenizer_decode_full.mnn     # FP16
├── moss_audio_tokenizer_decode_step.mnn     # FP16
└── convert_moss_log.txt              # 转换日志
```

---

## 二、Step 1 — 环境准备

### 2.1 确认 ONNX 源文件

源文件位置：
```
C:\Users\72952\OneDrive\Desktop\ui\models\MOSS-TTS-Nano-100M-ONNX\
├── moss_tts_prefill.onnx          277KB  + moss_tts_global_shared.data   421MB
├── moss_tts_decode_step.onnx       285KB  + moss_tts_global_shared.data   (共享)
├── moss_tts_local_cached_step.onnx  53KB  + moss_tts_local_shared.data  220MB
├── moss_tts_local_decoder.onnx      49KB  + moss_tts_local_shared.data   (共享)
├── moss_tts_local_fixed_sampled_frame.onnx  461KB + moss_tts_local_shared.data (共享)

C:\Users\72952\OneDrive\Desktop\ui\models\MOSS-Audio-Tokenizer-Nano-ONNX\
├── moss_audio_tokenizer_encode.onnx        797KB + encode.data            43MB
├── moss_audio_tokenizer_decode_full.onnx   666KB + decode_shared.data     43MB
├── moss_audio_tokenizer_decode_step.onnx   344KB + decode_shared.data    (共享)
```

注意：prefill 和 decode_step **共享** `moss_tts_global_shared.data`，因为它们都属于全局 12 层 GPT-2 decoder。MNNConvert 会将外部权重合并进 .mnn 文件。

### 2.2 编译 MNNConvert

MNN 源码位置：`C:\Users\72952\OneDrive\Desktop\ui\reference\MNN`

```bash
cd reference/MNN
mkdir build && cd build

# 基础编译（Windows 用 MSVC，或交叉编译用 Android NDK）
cmake .. \
    -DMNN_BUILD_CONVERTER=ON \
    -DMNN_BUILD_SHARED_LIBS=OFF \
    -DCMAKE_BUILD_TYPE=Release

cmake --build . --target MNNConvert -j8
```

编译产物：`build/MNNConvert`（Windows 上为 `MNNConvert.exe`）

---

## 三、Step 2 — FP16 转换（全部 8 个模型）

### 3.1 转换脚本

创建 `convert_moss_mnn_fp16.bat`：

```batch
@echo off
set ONNX_DIR=C:\Users\72952\OneDrive\Desktop\ui\models\MOSS-TTS-Nano-100M-ONNX
set CODEC_DIR=C:\Users\72952\OneDrive\Desktop\ui\models\MOSS-Audio-Tokenizer-Nano-ONNX
set OUT_DIR=C:\Users\72952\OneDrive\Desktop\ui\models\lianghua_moss
set MNN_CONVERT=path\to\MNNConvert.exe

mkdir %OUT_DIR% 2>nul

:: 1. TTS 模型
MNNConvert -f ONNX --fp16 ^
    --modelFile "%ONNX_DIR%\moss_tts_prefill.onnx" ^
    --MNNModel "%OUT_DIR%\moss_tts_prefill.mnn" ^
    --bizCode moss_tts

MNNConvert -f ONNX --fp16 ^
    --modelFile "%ONNX_DIR%\moss_tts_decode_step.onnx" ^
    --MNNModel "%OUT_DIR%\moss_tts_decode_step.mnn" ^
    --bizCode moss_tts

MNNConvert -f ONNX --fp16 ^
    --modelFile "%ONNX_DIR%\moss_tts_local_cached_step.onnx" ^
    --MNNModel "%OUT_DIR%\moss_tts_local_cached_step.mnn" ^
    --bizCode moss_tts

MNNConvert -f ONNX --fp16 ^
    --modelFile "%ONNX_DIR%\moss_tts_local_decoder.onnx" ^
    --MNNModel "%OUT_DIR%\moss_tts_local_decoder.mnn" ^
    --bizCode moss_tts

MNNConvert -f ONNX --fp16 ^
    --modelFile "%ONNX_DIR%\moss_tts_local_fixed_sampled_frame.onnx" ^
    --MNNModel "%OUT_DIR%\moss_tts_local_fixed_sampled_frame.mnn" ^
    --bizCode moss_tts

:: 2. Audio Tokenizer 模型
MNNConvert -f ONNX --fp16 ^
    --modelFile "%CODEC_DIR%\moss_audio_tokenizer_encode.onnx" ^
    --MNNModel "%OUT_DIR%\moss_audio_tokenizer_encode.mnn" ^
    --bizCode moss_codec

MNNConvert -f ONNX --fp16 ^
    --modelFile "%CODEC_DIR%\moss_audio_tokenizer_decode_full.onnx" ^
    --MNNModel "%OUT_DIR%\moss_audio_tokenizer_decode_full.mnn" ^
    --bizCode moss_codec

MNNConvert -f ONNX --fp16 ^
    --modelFile "%CODEC_DIR%\moss_audio_tokenizer_decode_step.onnx" ^
    --MNNModel "%OUT_DIR%\moss_audio_tokenizer_decode_step.mnn" ^
    --bizCode moss_codec

echo "FP16 转换完成"
```

### 3.2 预期 FP16 输出大小

| 模型 | FP32 原大小 | FP16 后大小 |
|------|------------|-------------|
| `moss_tts_prefill.mnn` | 421MB（共享） | **~210MB** |
| `moss_tts_decode_step.mnn` | (共享) | **~210MB** |
| `moss_tts_local_cached_step.mnn` | 220MB（共享） | **~110MB** |
| `moss_tts_local_decoder.mnn` | (共享) | **~110MB** |
| `moss_tts_local_fixed_sampled_frame.mnn` | (共享) | **~110MB** |
| 3 个 audio tokenizer 模型 | 86MB | **~43MB** |
| **合计** | **~728MB** | **~363MB** |

---

## 四、Step 3 — 动态 INT8 量化（prefill + decode_step）

### 4.1 原理

MNN 支持 **Weight-Only 量化**：
- 权重存为 INT8，推理时反量化到 FP16 再计算
- 不需要校准数据（per-tensor/per-channel min-max）
- 计算精度 = FP16 级别，精度损失极小
- 主要收益：**减少内存带宽**（INT8 内存读取是 FP16 的一半）

### 4.2 转换命令

```batch
:: prefill 动态 INT8（weight-only）
MNNConvert -f ONNX --weightQuantBits 8 ^
    --modelFile "%ONNX_DIR%\moss_tts_prefill.onnx" ^
    --MNNModel "%OUT_DIR%\moss_tts_prefill_int8.mnn" ^
    --bizCode moss_tts

:: decode_step 动态 INT8
MNNConvert -f ONNX --weightQuantBits 8 ^
    --modelFile "%ONNX_DIR%\moss_tts_decode_step.onnx" ^
    --MNNModel "%OUT_DIR%\moss_tts_decode_step_int8.mnn" ^
    --bizCode moss_tts
```

> 注：`--weightQuantBits 8` 是 MNNConvert 的 Weight-Only INT8 选项，如果没有这个参数，可以用 MNN 的后量化工具 `mnncompress`。

### 4.3 备选方案：用 MNN 后量化工具

如果 MNNConvert 的 `--weightQuantBits` 不可用，使用 `tools/mnncompress`：

```bash
cd reference/MNN/tools/mnncompress
python mnncompress.py \
    --model_file "%OUT_DIR%\moss_tts_prefill.mnn" \  # 先转 FP16 .mnn
    --quant_bits 8 \
    --quant_type WEIGHT \
    --output_file "%OUT_DIR%\moss_tts_prefill_int8.mnn"
```

### 4.4 最终预期大小

| 模型 | 精度 | 大小 |
|------|------|------|
| `moss_tts_prefill_int8.mnn` | Weight-Only INT8 | **~105MB** |
| `moss_tts_decode_step_int8.mnn` | Weight-Only INT8 | **~105MB** |
| `moss_tts_local_cached_step.mnn` | FP16 | ~110MB |
| `moss_tts_local_decoder.mnn` | FP16 | ~110MB |
| `moss_tts_local_fixed_sampled_frame.mnn` | FP16 | ~110MB |
| 3 个 audio tokenizer | FP16 | ~43MB |
| **合计** | | **~258MB** |

相比原始 FP32 的 **728MB → 258MB，压缩 65%**。

---

## 五、Step 4 — MNN ARM 优化编译（Android）

### 5.1 CMake 优化选项

构建 MNN 静态库/动态库供 Android 使用：

```bash
cd reference/MNN
mkdir build_android && cd build_android

cmake .. \
    -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-24 \
    -DMNN_BUILD_SHARED_LIBS=ON \
    -DCMAKE_BUILD_TYPE=Release \
    \
    -DMNN_ARM82=ON          `# ARMv8.2 FP16 + FMLAL` \
    -DMNN_ARM82_FP16=ON     `# FP16 原生计算` \
    -DMNN_ARM82_DOT=ON      `# INT8 SDOT/UDOT 点积加速 4x` \
    -DMNN_ARM82_I8MM=ON     `# INT8 矩阵乘法加速 8x` \
    \
    `# 可选高级优化（第一阶段不开）` \
    # -DMNN_ARM82_RDM=ON    # FFT 加速（音频处理，以后开）
    # -DMNN_ARM82_LSE=ON    # 多线程原子操作，以后开

cmake --build . -j8
```

编译产物：
- `libMNN.so` — MNN 推理引擎核心
- `libMNN_Express.so` — 表达式层（可选）

### 5.2 优化选项说明

| CMake 选项 | ARM 指令 | 作用 | 阶段 |
|-----------|---------|------|------|
| `MNN_ARM82=ON` | ARMv8.2+ | 启用 ARMv8.2 优化总开关 | **第一阶段开** |
| `MNN_ARM82_FP16=ON` | FP16 | FP16 矩阵乘法用 FMLAL 指令，吞吐翻倍 | **第一阶段开** |
| `MNN_ARM82_DOT=ON` | SDOT/UDOT | INT8 点积 4x 加速 | **第一阶段开** |
| `MNN_ARM82_I8MM=ON` | I8MM | INT8 矩阵乘法 8x 加速 | **第二阶段开** |
| `MNN_ARM82_RDM=ON` | RDM | FFT 复数乘加（音频 tokenizer 可能有收益） | 以后 |
| `MNN_ARM82_LSE=ON` | LSE | 多线程原子操作（减少锁竞争） | 以后 |
| `-O3 -DNDEBUG` | 编译器 | 激进内联、循环展开、自动向量化 | **必须开** |

### 5.3 第一阶段 vs 第二阶段

| 阶段 | 开启的优化 | 目的 |
|------|-----------|------|
| **第一阶段（本计划）** | FP16 + DotProd + I8MM | 稳定加速，验证推理正确性 |
| **第二阶段（以后）** | RDM + LSE + 线程调优 | 压榨极限性能 |

---

## 六、Step 5 — Android 集成

### 6.1 App 迁移方案

新增文件（参照现有 MossTtsNanoRuntime.kt 的逻辑）：

| 新文件 | 作用 |
|--------|------|
| `engine/MossTtsMnnRuntime.kt` | MNN 推理引擎（替代 ONNX Runtime） |
| `engine/MossTtsMnnVoiceCloneEngine.kt` | MNN 版语音克隆引擎 |

### 6.2 MNN 推理核心伪代码

```kotlin
// MossTtsMnnRuntime.kt
class MossTtsMnnRuntime(
    private val modelDirectory: File,
    private val config: MossTtsNanoConfig
) {
    private val interpreter: MNNInterpreter
    private val prefillSession: MNNSession
    private val decodeStepSession: MNNSession
    private val localCachedStepSession: MNNSession
    private val codecEncodeSession: MNNSession
    private val codecDecodeSession: MNNSession

    init {
        // 加载 MNN 模型
        interpreter = MNNInterpreter(File(modelDirectory, "moss_tts_prefill_int8.mnn").absolutePath)
        prefillSession = interpreter.createSession()
        // ... 其他模型同理
    }

    // 自回归解码主循环（逻辑与原来一致，API 从 OrtSession 换为 MNNSession）
    fun generateAudioFrames(...) { ... }

    fun release() {
        prefillSession?.release()
        interpreter?.release()
    }
}
```

### 6.3 build.gradle.kts MNN 依赖

```kotlin
dependencies {
    // 替换掉 onnxruntime-android，改用 MNN
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.18.0")  // 移除
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))  // MNN .aar
}
```

MNN Android 的 .aar 或 .so 从编译产物获取：
```
reference/MNN/build_android/libMNN.so  →  app/src/main/jniLibs/arm64-v8a/
```

或者下载 MNN 官方预编译 .aar：
```
https://github.com/alibaba/MNN/releases
```

---

## 七、Step 6 — 测试方案

### 7.1 测试流程

```
1. 编译 MNNConvert → 转换 ONNX → MNN（FP16 + INT8）
2. 编译 libMNN.so（ARM82 + FP16 + DotProd）
3. 修改 Android App，集成 MNN Runtime
4. adb install 安装测试 APK
5. 发送一条消息 → 触发 TTS 合成
6. 对比 ONNX Runtime 和 MNN Runtime 的：
   - 合成功耗（logcat 时间戳）
   - 合成延迟
   - 音频质量（人耳听感）
```

### 7.2 性能对比基准

| 指标 | ONNX Runtime | MNN FP16 | MNN INT8 |
|------|-------------|----------|----------|
| 模型大小 | 728MB | ~363MB | ~258MB |
| 首次加载时间 | TBD | TBD | TBD |
| 10 字合成延迟 | TBD | TBD | TBD |
| 50 字合成延迟 | TBD | TBD | TBD |
| 音频质量 | 基准 | 无损 | 轻微损失 |

### 7.3 测试脚本（Python 验证）

在 PC 上先用 MNN Python 接口验证转换正确性：

```python
import MNN
import numpy as np

# 加载 MNN 模型
interpreter = MNN.Interpreter("moss_tts_prefill_int8.mnn")
session = interpreter.createSession()

# 获取输入张量
input_tensor = interpreter.getSessionInput(session, "input_ids")

# 构造输入（与 ONNX 测试相同）
input_data = np.zeros((1, seq_len, 17), dtype=np.int16)
tmp_input = MNN.Tensor(input_data.shape, 
    MNN.Halide_Type_Int, input_data, MNN.Tensor_DimensionType_Caffe)

input_tensor.copyFrom(tmp_input)

# 推理
interpreter.runSession(session)

# 获取输出
output_tensor = interpreter.getSessionOutput(session, "global_hidden")
output_data = output_tensor.getNumpyData()

print(f"Output shape: {output_data.shape}")
print(f"Output mean: {output_data.mean()}")
```

---

## 八、风险与应对

| 风险 | 概率 | 应对 |
|------|------|------|
| MNNConvert 不支持外部权重 ONNX | 中 | ONNX 图很小（~300KB），可先用 Python 把外部权重合并到 ONNX 再转 |
| INT8 量化后音质下降 | 低 | 先用 FP16 验证，再对比 INT8 听感 |
| MNN ARM82 FP16 算子不支持某些 op | 低 | 回退到纯 FP32 MNN，或改模型 op |
| MNN Android 集成复杂 | 中 | 先用 MNN Python 验证结果正确性，再集成 Android |
| 自回归解码逻辑迁移工作量大 | 中 | 保留 Java 层控制逻辑，只替换推理引擎 |

---

## 九、第一轮执行计划

| 步骤 | 操作 | 预计耗时 |
|------|------|---------|
| 1 | 编译 MNNConvert（Windows） | 30min |
| 2 | 全部 ONNX → FP16 MNN | 10min |
| 3 | prefill + decode_step → INT8 | 5min |
| 4 | 编译 libMNN.so（ARM82 + FP16 + Dot） | 30min |
| 5 | Android 集成 + 测试 | 2-4h |
| **合计** | | **~4h** |
