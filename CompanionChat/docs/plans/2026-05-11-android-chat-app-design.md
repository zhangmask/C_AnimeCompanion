# 私人陪伴对话 App - 设计文档

> **日期:** 2026-05-11
> **状态:** 已确认

## 1. 产品愿景

打造一款 **Hermes / OpenClaw 风格的私人即时陪伴对话平台**，支持本地大模型推理、语音交互、图片理解，未来扩展为 AI 伴侣（男友/女友）角色。

## 2. 技术选型

| 层级 | 技术 | 说明 |
|---|---|---|
| UI 框架 | Jetpack Compose + Material 3 | 声明式 UI，适合动态交互和动画 |
| 导航 | Navigation Compose | 底部导航 + 页面路由 |
| 状态管理 | ViewModel + StateFlow | 响应式状态驱动 |
| 模型推理 | LiteRT-LM Kotlin API | Google 官方端侧大模型框架 |
| 语音输入 | Android SpeechRecognizer | 系统级语音识别 |
| 语音输出 | Android TextToSpeech | 系统级 TTS |
| 图片选择 | Android PhotoPicker | 现代图片选择器 |
| 构建 | Gradle (Kotlin DSL) + AGP | Android 标准构建 |

## 3. 页面结构

### 3.1 底部导航（4 个 Tab）

| Tab | 页面 | 当前状态 | 未来规划 |
|---|---|---|---|
| 首页 | HomeScreen | 空壳占位 | 海报/宣传/角色展示 |
| 对话 | ChatScreen | 核心功能页 | 私人对话主界面 |
| 记忆 | MemoryScreen | 空壳占位 | 对话记忆/长期记忆库 |
| 设置 | SettingsScreen | 空壳占位 | 模型配置/角色切换/语音设置 |

### 3.2 对话页 ChatScreen

```
┌─────────────────────────────┐
│  对话                    [···]│  顶部栏
├─────────────────────────────┤
│                             │
│  [AI头像] 你好！有什么能帮忙的？│  消息列表（LazyColumn）
│                             │
│           今天天气不错 [用户] │  用户消息（右对齐）
│                             │
│  [AI头像] 正在输入...         │  流式输出占位
│                             │
├─────────────────────────────┤
│ [📷] [🎤] [输入消息...] [➤]  │  输入栏
└─────────────────────────────┘
```

**功能清单：**
- 文本输入 + 发送按钮
- 消息列表（用户消息右对齐，AI 消息左对齐）
- 流式输出（逐 token 填充 AI 消息气泡）
- 图片上传按钮（PhotoPicker → 显示预览 → 发送）
- 语音输入按钮（SpeechRecognizer → 转文字 → 发送）
- 语音输出（TTS 朗读 AI 回复）
- 打字指示器动画

## 4. 核心架构

### 4.1 推理引擎抽象层

```kotlin
interface InferenceEngine {
    suspend fun initialize(config: EngineConfig)
    fun sendMessageStream(message: ChatMessage): Flow<String>
    suspend fun sendMultimodalMessage(text: String, images: List<ByteArray>): String
    fun cancel()
    fun release()
}

data class EngineConfig(
    val modelPath: String,
    val backend: BackendType = BackendType.CPU,
    val maxTokens: Int = 2048,
)

enum class BackendType { CPU, GPU }
```

### 4.2 语音引擎抽象层

```kotlin
interface VoiceInputEngine {
    fun startListening(): Flow<VoiceEvent>
    fun stopListening()
    fun release()
}

interface VoiceOutputEngine {
    suspend fun speak(text: String)
    fun stop()
    fun release()
}

sealed class VoiceEvent {
    data class PartialResult(val text: String) : VoiceEvent()
    data class FinalResult(val text: String) : VoiceEvent()
    data class Error(val message: String) : VoiceEvent()
}
```

### 4.3 陪伴角色接口（未来扩展）

```kotlin
interface CompanionCharacter {
    val id: String
    val name: String
    val avatarRes: Int
    val personality: String
    val systemPrompt: String
}
```

## 5. 数据模型

```kotlin
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val images: List<Uri> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
)

enum class MessageRole { USER, ASSISTANT, SYSTEM }
```

## 6. 目录结构

```
app/src/main/java/com/companion/chat/
├── MainActivity.kt
├── ui/
│   ├── theme/
│   │   ├── Theme.kt
│   │   ├── Color.kt
│   │   └── Type.kt
│   ├── navigation/
│   │   └── AppNavigation.kt
│   ├── home/
│   │   └── HomeScreen.kt
│   ├── chat/
│   │   ├── ChatScreen.kt
│   │   ├── ChatViewModel.kt
│   │   └── components/
│   │       ├── MessageBubble.kt
│   │       ├── ChatInputBar.kt
│   │       └── TypingIndicator.kt
│   ├── memory/
│   │   └── MemoryScreen.kt
│   └── settings/
│       └── SettingsScreen.kt
├── data/
│   ├── model/
│   │   └── ChatMessage.kt
│   └── engine/
│       ├── InferenceEngine.kt
│       ├── VoiceInputEngine.kt
│       └── VoiceOutputEngine.kt
└── viewmodel/
    └── MainViewModel.kt
```

## 7. 模型部署策略

- 当前手机已有 Edge Gallery 下载的模型：
  `/sdcard/Android/data/com.google.ai.edge.gallery/files/Gemma_4_E2B_it/`
- 第一阶段：UI 骨架搭建，不接真实模型
- 第二阶段：通过 LiteRT-LM Kotlin API 接入模型推理
- 第三阶段：接入语音/图片多模态能力
