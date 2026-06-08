# 真实模型推理 + 语音交互集成设计

> **日期:** 2026-05-11
> **状态:** 执行中

## 1. 目标

将 CompanionChat 从 UI 骨架升级为可真实对话的应用：
- 接入 LiteRT-LM Kotlin API，对接手机上已有的 Gemma 模型
- 接入 Android SpeechRecognizer（语音输入）+ TextToSpeech（语音输出）

## 2. LiteRT-LM 接入方案

### 2.1 Maven 依赖
```gradle
implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")
```

### 2.2 API 调用流程
```
EngineConfig(modelPath, backend, cacheDir)
  → Engine(engineConfig)
    → engine.initialize()
      → engine.createConversation(ConversationConfig)
        → conversation.sendMessageAsync(text).collect { token -> ... }
```

### 2.3 模型路径
手机上已有模型：`/sdcard/Android/data/com.google.ai.edge.gallery/files/Gemma_4_E2B_it/gemma-4-E2B-it.litertlm`

### 2.4 核心类映射

| 设计接口 | LiteRT-LM 实现 |
|---|---|
| InferenceEngine | 包装 Engine + Conversation |
| sendMessageStream() | conversation.sendMessageAsync().collect {} |
| cancel() | conversation.cancelProcess() |
| release() | conversation.close() + engine.close() |

## 3. 语音方案

### 3.1 语音输入 (SpeechRecognizer)
- Android 原生 `SpeechRecognizer` API
- 运行时权限 `RECORD_AUDIO`
- 支持部分识别结果实时显示

### 3.2 语音输出 (TextToSpeech)
- Android 原生 `TextToSpeech` API
- 中文语音支持
- 语速/音调可配置

## 4. 修改范围

| 文件 | 变更 |
|---|---|
| app/build.gradle.kts | 添加 litertlm-android 依赖 |
| InferenceEngine.kt | 接口微调适配 LiteRT-LM API |
| LiteRTLMInferenceEngine.kt | 新增：真实推理实现 |
| AndroidVoiceInputEngine.kt | 新增：SpeechRecognizer 实现 |
| AndroidVoiceOutputEngine.kt | 新增：TTS 实现 |
| ChatViewModel.kt | 接入真实引擎，替换模拟逻辑 |
| ChatScreen.kt | 添加语音权限请求、语音输出控制 |
| SettingsScreen.kt | 模型路径配置入口 |
