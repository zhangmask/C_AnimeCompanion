# 项目进度

## 2026-05-15 - DreamLite submodule 与 moss-tts-nano 端侧接入框架

### 完成内容
- 新增 `third_party/DreamLite` Git submodule，指向 `https://github.com/ByteVisionLab/DreamLite.git`，只提交 submodule 指针，不提交 DreamLite 源码副本或权重。
- 新增 `third_party/MOSS-TTS-Nano-Reader` Git submodule，指向 `https://github.com/OpenMOSS/MOSS-TTS-Nano-Reader.git`，作为 Android 端移植 MOSS browser ONNX runner 的稳定参考。
- 图片生成本地 Provider 从普通占位改为 DreamLite 模型包检查器：
  - 默认目录为 `/sdcard/Android/data/com.companion.chat/files/models/image/dreamlite`。
  - 要求 `dreamlite_config.json`，并校验 `model_name`、`runtime` 和配置声明的 `required_files`。
  - 触发本地 DreamLite 生图时不闪退；缺文件或官方端侧包未就绪时返回明确错误。
- 接入 `moss-tts-nano` 本地语音克隆框架：
  - 新增 `ai.onnxruntime:onnxruntime-android:1.25.0`。
  - 默认目录为 `/sdcard/Android/data/com.companion.chat/files/models/tts/moss-tts-nano`。
  - 已识别本地缓存 `third_party/models/tts/moss-tts-nano/` 的真实 OpenMOSS browser ONNX 拆分格式。
  - 校验 `tts/tts_browser_onnx_meta.json`、`audio_tokenizer/codec_browser_onnx_meta.json`、TTS ONNX、audio tokenizer ONNX 和外部 `.data` 权重文件。
  - `CLONE` 模式会先检查 MOSS 模型包和参考音频；真实自回归 ONNX runner 完成前自动回退系统 TTS，避免按错误 acoustic/vocoder 文件名误跑。
- 语音设置页展示 MOSS 模型目录、状态和是否可本地克隆；模型配置页展示 DreamLite 状态和官方权重/端侧包限制。

### 验证
- `./gradlew :app:testDebugUnitTest` 通过。
- `git submodule status third_party/DreamLite` 可见 DreamLite 指针。

### 已知限制
- DreamLite 当前只完成 App 侧接入框架与模型目录校验，等待官方端侧权重/包后再做真实扩散推理。
- MOSS 已按现有本地包的 browser ONNX manifest 校验；下一步需要实现 prefill/decode/local decoder/audio tokenizer decode 的自回归 runner 后再做真机端到端合成验收。

---

## 2026-05-15 - 发现页、角色导入、图片生成配置与闪退修复

### 完成内容
- 首页从占位页升级为“发现”角色目录：
  - 支持搜索角色、作者、标签。
  - 支持标签筛选、私密内容开关、热门/最新/名称排序。
  - 支持角色卡片、收藏、详情页、解锁和开始聊天入口。
- 新增发现角色数据层：
  - `DiscoverRoleCard`、`RoleCollection`、`RoleGenerationPreset` 等模型。
  - `DiscoverRoleSeeds` 内置多张本地发现角色。
  - `DiscoverRoleRepository` 持久化收藏、解锁和已导入角色 ID。
- 打通发现角色导入：
  - 发现角色可复制到“我的角色卡”并自动激活。
  - 角色详情点击“开始聊天”后导入角色并跳转对话页。
  - 已导入角色支持追加生成图片到图库。
- 图片生成能力扩展：
  - `ImageGenerationConfig` 新增 Provider 与本地模型路径。
  - 新增 `ImageGenerationEngineSelector`，支持 HTTP Provider 与本地 DreamLite 占位 Provider。
  - 模型配置页新增图片生成 Provider、Base URL、API Key、模型名、模板、响应字段和超时配置。
  - 聊天页图片生成失败会回写到 UI 状态，避免静默失败。
- 角色编辑器增强：
  - 编辑弹窗拆分为基础、人设、图片、语音四个页签。
  - 支持维护头像图片 URI、图库 URI、图片风格提示词、语音模式和语音参考 URI。
  - 修复角色管理页删除按钮回调被多包一层 lambda 导致无法正确触发的问题。
- 语音克隆占位：
  - 新增 `VoiceCloneProvider` 与 `VoiceCloneProviderSelector`。
  - 克隆后端不可用时可明确回退系统 TTS。
- 文档与仓库维护：
  - 新增 `PRODUCT.md` 和 `DESIGN.md`，记录产品定位、设计原则与 UI 约束。
  - `.gitignore` 增加 `.cxx/` 与 `app/.cxx/`，避免 Android CMake/NDK 本地构建产物进入版本控制。

### 闪退修复
- 现象：进入“下载/发现”相关界面后应用闪退。
- 根因：`DiscoverViewModel` 使用带默认参数的 Kotlin 主构造器，但 Compose `viewModel()` 运行时通过 `AndroidViewModelFactory` 反射查找精确的 `DiscoverViewModel(Application)` 构造函数；缺少该显式构造函数时抛出 `NoSuchMethodException`。
- 修复：在 `DiscoverViewModel` 中补充显式 `constructor(application: Application)`，并新增单元测试锁定该运行时构造契约。
- 真机验证：重装后启动应用，未再出现 `AndroidRuntime/FATAL`；界面树可见发现角色详情、开始聊天、收藏解锁、生成图片等控件。

### 验证
- `./gradlew :app:testDebugUnitTest --tests "com.companion.chat.ui.home.DiscoverViewModelTest"` 通过。
- `./gradlew :app:assembleDebug` 通过。
- `./gradlew test --no-daemon` 通过。
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` 成功。
- `adb logcat` 复验未再出现 `DiscoverViewModel.<init>(Application)` 相关崩溃。

### 关键文件
- 发现数据层：`app/src/main/java/com/companion/chat/data/discover/`
- 发现页与详情页：`app/src/main/java/com/companion/chat/ui/home/`
- 导航：`app/src/main/java/com/companion/chat/MainActivity.kt`、`app/src/main/java/com/companion/chat/ui/navigation/AppNavigation.kt`
- 图片生成：`app/src/main/java/com/companion/chat/data/image/`
- 语音克隆占位：`app/src/main/java/com/companion/chat/data/voice/VoiceCloneProvider*.kt`
- 角色编辑：`app/src/main/java/com/companion/chat/ui/settings/RoleCardEditorDialog.kt`
- 产品与设计文档：`PRODUCT.md`、`DESIGN.md`

---

## 2026-05-15 - 聊天栏语音输入与语音输出按钮分离

### 完成内容
- 将聊天输入栏右侧从单个动作按钮拆分为两个固定控制位：
  - 主输入按钮：有文字或图片时发送，空输入时作为麦克风语音输入。
  - 语音输出按钮：位于主输入按钮右侧，负责朗读最近一条助手回复。
- 语音输出按钮在未播放时显示朗读图标，播放中切换为停止图标并调用停止播放。
- 无可朗读助手回复时禁用语音输出按钮，避免空操作。
- 输入框 placeholder 只保留输入相关状态：启动语音识别、正在听、输入消息；不再用“正在播放...”占用输入语义。
- `ChatViewModel` 新增最近可朗读助手消息判断，并提供 `speakLatestAssistantMessage()`，只朗读非 streaming 且内容非空的最近助手消息。
- `ChatScreen` 已完成新按钮参数接线。

### 验证
- `./gradlew :app:testDebugUnitTest` 通过。
- `./gradlew :app:assembleDebug` 通过。
- `./gradlew :app:compileDebugKotlin` 通过。
- 构建过程中仍存在既有 AGP 8.5.2 与 compileSdk 35 兼容性提示，不影响本次构建结果。

### 关键文件
- 输入栏：`app/src/main/java/com/companion/chat/ui/chat/components/ChatInputBar.kt`
- 聊天页面：`app/src/main/java/com/companion/chat/ui/chat/ChatScreen.kt`
- ViewModel：`app/src/main/java/com/companion/chat/ui/chat/ChatViewModel.kt`

---

## 2026-05-11 - CompanionChat v0.1.0 UI骨架

### 完成内容
- 创建 Android Compose 项目，包名 `com.companion.chat`
- 底部导航 4 个 Tab：首页、对话、记忆、设置
- 首页空壳占位（未来放海报/宣传）
- 对话页完整 UI：消息列表、用户/AI 气泡、输入栏、发送按钮、流式输出模拟、图片上传、语音输入按钮
- 记忆页空壳占位
- 设置页空壳占位（角色/模型/语音/外观/关于）
- 推理引擎/语音引擎/角色抽象接口预留
- APK 编译通过，ADB 推送到手机

### 关键文件
- 设计文档：`docs/plans/2026-05-11-android-chat-app-design.md`
- 实施计划：`docs/plans/2026-05-11-android-chat-app-plan.md`
- Android 项目：`CompanionChat/`
- 模型文件：`models/gemma-4-E2B-it.litertlm`（2.4GB，从 Edge Gallery 复制）
- 模型在手机上：`/sdcard/Android/data/com.google.ai.edge.gallery/files/Gemma_4_E2B_it/`

---

## 2026-05-11 - CompanionChat v0.1.0 Phase 2：模型推理 + 语音输入输出

### 完成内容

#### 依赖升级
- Kotlin 2.0.21 → 2.3.20（LiteRT-LM 要求 Kotlin metadata 2.2+）
- AGP 8.4.2 → 8.5.2
- `kotlinOptions` → `tasks.withType<KotlinCompile>().configureEach { compilerOptions {} }`（适配 2.3.x）
- JDK target 17（兼容 JDK 21 编译器）

#### LiteRT-LM 推理引擎
- 集成 `com.google.ai.edge.litertlm:litertlm-android:0.11.0` Maven 依赖
- 通过 `javap` 反编译 AAR 确认精确 API 签名（源码 vs 发布版有差异）
- 实现 `LiteRTLMInferenceEngine`，流程：`EngineConfig → Engine(config) → engine.initialize() → engine.createConversation(convConfig) → conversation.sendMessageAsync(text).collect {}`
- 默认模型路径：`/sdcard/Download/gemma-4-E2B-it.litertlm`
- 模型已通过 ADB 推送到手机（2.4GB，28.3 MB/s）

#### 语音输入引擎
- 实现 `AndroidVoiceInputEngine`，基于 Android `SpeechRecognizer`
- 支持中文普通话识别，`callbackFlow` 封装回调事件
- 自动停止检测、错误处理

#### 语音输出引擎
- 实现 `AndroidVoiceOutputEngine`，基于 Android `TextToSpeech`
- 支持中文 TTS，`MutableStateFlow` 跟踪播放状态
- 语速 1.0f，音调 1.0f

#### ChatViewModel 升级
- 改为 `AndroidViewModel`，直接创建真实引擎实例
- 引擎初始化在 ViewModel init 时自动触发
- 流式推理通过 `sendMessageStream().collect` 更新 UI
- 语音输入/输出生命周期随 ViewModel 管理

#### UI 更新
- ChatScreen 增加语音权限处理（`RECORD_AUDIO`）
- ChatInputBar 增加 TTS 播放中状态显示和停止按钮
- 状态栏显示模型初始化进度

### 验证
- APK 编译通过（BUILD SUCCESSFUL in 23s）
- ADB 安装到手机成功（aa972376）
- 模型文件就位：`/sdcard/Download/gemma-4-E2B-it.litertlm`

### 关键文件（新增/修改）
- 推理引擎：`CompanionChat/app/src/main/java/com/companion/chat/engine/LiteRTLMInferenceEngine.kt`
- 语音输入：`CompanionChat/app/src/main/java/com/companion/chat/engine/AndroidVoiceInputEngine.kt`
- 语音输出：`CompanionChat/app/src/main/java/com/companion/chat/engine/AndroidVoiceOutputEngine.kt`
- ViewModel：`CompanionChat/app/src/main/java/com/companion/chat/ui/chat/ChatViewModel.kt`
- 聊天页面：`CompanionChat/app/src/main/java/com/companion/chat/ui/chat/ChatScreen.kt`
- 输入栏：`CompanionChat/app/src/main/java/com/companion/chat/ui/chat/components/ChatInputBar.kt`
- 集成设计：`docs/plans/2026-05-11-model-voice-integration-design.md`

### 遇到的问题和解决方案
1. **LiteRT-LM 要求 Kotlin 2.2+**：从 2.0.21 升级到 2.3.20
2. **`kotlinOptions` 在 2.3.x 废弃**：改用 `tasks.withType<KotlinCompile>` 的 `compilerOptions`
3. **LiteRT-LM v0.11.0 API 与源码不一致**：用 `javap` 反编译 AAR 的 classes.jar 确认真实签名
4. **`Engine` 构造函数只有 1 个参数**（不是源码里的 2 个）
5. **`SamplerConfig` 的 topP/temperature 是 Double**（不是 Float）
6. **ADB 不在 PATH**：使用 `D:\AndroidstudioSDK\platform-tools\adb.exe` 完整路径

### 待做
- 实际运行测试模型推理效果
- 记忆系统
- 角色管理
- 模型/角色下载管理

---

## 2026-05-12 - CompanionChat v0.1.0 Bug 修复 + 图片多模态支持

### 问题修复

#### 1. 模型文件读取失败（Android 分区存储）
- **问题**：Android 10+ 分区存储导致无法读取 `/sdcard/Download/` 路径
- **解决**：改为使用 `context.getExternalFilesDir("models")` → `/sdcard/Android/data/com.companion.chat/files/models/`
- **部署流程**：卸载旧包 → 安装新包 → ADB 推送模型到应用目录

#### 2. MIUI 无法查看 Logcat
- **问题**：小米手机 MIUI 默认屏蔽应用日志输出，`adb logcat` 无法看到应用日志
- **解决**：在 `ChatUiState` 增加 `diagnosticLog` 字段，引擎日志直接显示在 UI 上；同时引擎用 `openFileOutput("engine_log.txt")` 写文件日志

#### 3. 第一次对话后无法继续
- **问题**：`callbackFlow` 的 `sendMessageAsync().collect` 完成后未调用 `close()`，Flow 永远不结束，`isGenerating` 永远为 true
- **解决**：在 `finally` 块中添加 `close()` 确保 Flow 正常终止

#### 4. 图片显示虚假（UI bug）
- **问题**：ChatInputBar 和 MessageBubble 显示灰色占位方块
- **解决**：集成 Coil 图片加载库（`coil-compose:3.2.0`），用 `AsyncImage` 替代手动 Box 绘制

#### 5. 图片无法被模型识别（核心功能）
- **问题**：`sendMessageStream()` 只发送文本 `lastUserMessage.content`，完全忽略 `images` 列表
- **解决**：
  - 通过 `javap` 反编译 AAR 确认图片 API：`Content.ImageBytes(byte[])`, `Content.Text(String)`, `Contents.of(Content...)`
  - 新增 `uriToImageBytes(uri)` 工具方法：URI → InputStream → Bitmap → 缩放到 1024px → PNG byte[]
  - `sendMessageStream()` 检测到图片时构建多模态消息：`Contents.of(ImageBytes, Text)` 发送
  - `EngineConfig` 增加 `visionBackend = Backend.GPU()` 和 `maxNumImages = 4` 启用视觉能力

### 关键文件（新增/修改）
- 推理引擎：`CompanionChat/app/src/main/java/com/companion/chat/engine/LiteRTLMInferenceEngine.kt`（图片转换+多模态发送）
- ViewModel：`CompanionChat/app/src/main/java/com/companion/chat/ui/chat/ChatViewModel.kt`（诊断日志）
- 聊天页面：`CompanionChat/app/src/main/java/com/companion/chat/ui/chat/ChatScreen.kt`（诊断显示）
- 输入栏：`CompanionChat/app/src/main/java/com/companion/chat/ui/chat/components/ChatInputBar.kt`（Coil 图片预览）
- 消息气泡：`CompanionChat/app/src/main/java/com/companion/chat/ui/chat/components/MessageBubble.kt`（Coil 图片显示+全屏预览）
- 依赖：`gradle/libs.versions.toml` + `app/build.gradle.kts`（添加 Coil 库）

### 部署状态
- APK 编译通过（BUILD SUCCESSFUL in 1m 4s）
- 卸载旧包 → 安装新包 → 推送模型到应用目录完成
- 模型位置：`/sdcard/Android/data/com.companion.chat/files/models/gemma-4-E2B-it.litertlm`

### 待做
- 测试图片识别效果（用户在手机上操作验证）
- 记忆系统
- 角色管理
- 模型/角色下载管理

---

# master 分支进度记录


## 2026-05-13

- 已完成 Task 1：建立阶段二数据模型与配置入口。
- 新增 `ContextSettings`、`ContextWindow`、`ContextConfigRepository`。
- `ChatViewModel` 已接入上下文配置读取入口，但当前发送逻辑还没有切到阶段二上下文管理链路。
- 已补充最小 `JUnit4` 单元测试依赖，并新增 `ContextSettingsTest`。
- 已执行 `.\gradlew.bat :app:testDebugUnitTest`，当前通过。
- 已完成 Task 2-4：实现 `PromptAssembler`、`DefaultContextManager`、`SummaryGenerator`、`NoOpSummaryGenerator`，对应单元测试已通过。
- 已完成 Task 5-7 的第一版主链路：引擎支持 `getCurrentConfig()`、`rebuildConversation()`、`replayMessages()` 接口，发送前可触发上下文压缩判断、Conversation 重建、回放失败降级日志。
- `replayMessages()` 已升级为基于 LiteRT-LM `ConversationConfig.initialMessages` 的原生回放实验实现，不再只是占位降级。
- 已完成 Task 8：设置页新增“上下文窗口大小”入口，可在模型配置页选择保留轮数并持久化。
- 多次执行 `.\gradlew.bat :app:assembleDebug` 成功，当前进入真机部署与长对话验证阶段。
- 真机已验证：应用可启动、模型可进入 `Ready`、可发送至少两轮真实对话、聊天页与设置页可正常切换。
- 真机当前阻塞：ADB 自动输入受到系统拼音输入法组合态影响，连续批量发消息不稳定，导致长对话压缩路径尚未在真机上稳定触发。
- Task 9 当前结论：代码侧已切到原生 `initialMessages` 回放实验方案。
- 2026-05-13 真机阶段二专项验证通过：
- 设置项 `N=5` 生效，`shouldCompress()` 阈值实际为 `20`。
- 真机长对话在 `messageCount=21` 起成功触发压缩。
- 修复后 `Conversation` 重建成功，不再出现 “A session already exists”。
- 最近消息回放成功，日志显示 `initialMessages=10`。
- 追问“我们刚才聊了什么”时，模型成功回忆出关键信息：电竞竞猜、明天早上 9 点、5 人参与。
- 2026-05-13 阶段二补齐版已重新部署到真机，规则摘要器与降级摘要注入代码已生效，单测与编译通过。
- 本轮真机补测结果：`retainedRounds=10` 初始加载后，实际发送前阈值日志为 `threshold=20`，说明运行时设置已切到 `N=5`。
- 本轮日志确认：`summaryEmpty=false`，说明被裁剪历史已生成非空规则摘要。
- 本轮日志确认：`Conversation` 重建成功，且最近消息回放成功，未触发降级摘要注入。
- 本轮人工反馈：上下文管理“没什么问题”，界面可继续操作，未出现明显卡死。
- 但本轮日志只覆盖到 `messageCount=19` 后触发首次压缩，并非严格的 `30+` 条消息专项验收；若要完全对齐阶段二清单，仍需补一轮 `30+` 条真实消息记录。
- 2026-05-13 已补齐 `30+` 条真机长对话专项：日志确认从 `12` 持续到 `30` 的过程中，多次触发压缩。
- 本轮 `30+` 专项中，每次压缩均表现为：
- `summaryEmpty=false`，说明规则摘要持续生成成功。
- `Conversation` 重建完成。
- `最近消息回放成功: initialMessages=10`。
- 最新真机专项说明：在长对话连续推进到 `30` 条后，应用未崩溃、未 ANR，聊天页仍可继续交互。
- 阶段二当前结论：主链路、规则摘要、压缩重建、最近消息回放、`30+` 条长对话稳定性、UI 可操作性均已具备明确证据。
- 2026-05-13 已完成阶段三文档准备：
- 新增 `docs/plans/2026-05-13-stage3-memory-system-design.md`
- 新增 `docs/plans/2026-05-13-stage3-memory-system-plan.md`
- 阶段三方向确定为“完整阶段三覆盖，但按后端主链路先行、UI 随后收口”的实现策略。
- 2026-05-13 阶段三代码主链路已完成：
- 已新增 `ExtractedMemory`、`MemoryExtractor`、`RuleBasedMemoryExtractor`。
- 已新增 `MemoryRepository`、`MemoryRetriever`、`MemoryPromptBuilder`、`MemoryLifecycleManager`。
- `ChatViewModel` 已接入发送前相关记忆检索与注入、发送后规则提取自动落库。
- `CompanionChatApplication` 已接入启动时过期短期记忆清理与可提升短期记忆提升。
- 已新增 `MemoryViewModel`、`MemoryUiState`，并将 `MemoryScreen` 从占位页替换为可筛选、手动新增、编辑、删除、提升的最小可用版。
- 设置页已新增“记忆管理”入口，可跳转到记忆页。
- 2026-05-13 阶段三本地验证通过：
- `:app:testDebugUnitTest --tests "com.companion.chat.data.memory.*"` 通过。
- `:app:testDebugUnitTest --tests "com.companion.chat.ui.memory.*"` 通过。
- `:app:testDebugUnitTest` 全量通过。
- `:app:assembleDebug` 通过。
- 2026-05-13 阶段三真机部署验证通过：
- 已按“编译成功 -> 卸载旧 app -> 安装新 app -> 推送模型”流程完成重装。
- `app_init_log.txt` 确认应用启动后执行了 `ensureInitialized` 和“记忆生命周期维护完成”。
- `viewmodel_log.txt` 确认模型路径存在、模型文件大小正确，且 `engine.initialize` 返回 `state = Ready`。
- 设备数据库导出检查确认：`skills` 表已有 4 条内置技能，`conversations` 表当前为空，`memories` 表当前为空。
- 当前真机自动化限制：
- 设备仅有微信输入法，ADB 直接中文输入失败，导致“记住我叫小明 -> 我叫什么”的全自动对话验收暂未完成。
- 底部导航与设置入口的 ADB 坐标点击在当前系统桌面/输入法状态下不稳定，阶段三 UI 真机验证目前以编译通过、布局树可见、日志与数据库证据为主。
- 2026-05-13 记忆页闪退已修复：
- 根因是 `MemoryViewModel` 仅保留了 Kotlin 默认参数主构造器，运行时 `viewModel()` 通过 `AndroidViewModelFactory` 反射查找 `MemoryViewModel(Application)` 失败，抛出 `NoSuchMethodException` 并导致进入记忆页闪退。
- 已在 `MemoryViewModel` 中补充显式的 `constructor(application: Application)`，保持测试注入构造方式不变。
- 修复后已重新 `assembleDebug` 并真机重装。
- 复验结果：点击底部“记忆”后未再出现 `AndroidRuntime` 崩溃栈，`dumpsys window windows` 显示前台窗口仍为 `com.companion.chat/.MainActivity`。
- 2026-05-13 记忆页真机可用性补测：
- 已验证空状态展示正确，页面显示“还没有记忆”与引导文案。
- 已验证顶部分类筛选存在：全部 / 事实 / 偏好 / 事件 / 关系。
- 已验证手动新增：通过 ADB 英文输入新增一条事实类长期记忆，列表立即出现新卡片。
- 已验证分类筛选：切到“偏好”后列表为空，切回“事实”后新增记忆重新出现。
- 已验证编辑：打开编辑弹窗后追加文本保存，列表标题与更新时间发生变化。
- 已验证删除：点击删除后出现确认弹窗，确认后列表回到空状态。
- 已验证设置页入口：从“设置 -> 记忆管理”可正确回到记忆页。
- 当前仍未自动化验证“手动提升短期记忆”为长期：
- 原因 1：设备无可用 `sqlite3`，无法直接在真机库中快速注入 `short_term` 测试数据。
- 原因 2：当前设备中文 ADB 输入受微信输入法限制，无法稳定走“记住我叫小明”这类中文规则提取路径来生成短期记忆。
- 2026-05-13 已修复“记忆主语归属错误”：
- 现象：当用户写入“`小王是我的哥哥`”这类关系记忆后，模型可能把“我的”误解为助手自身，而不是用户。
- 根因：记忆虽然已进入上下文，但原有 `MemoryPromptBuilder` 和 `PromptAssembler` 没有明确约束“这些记忆都属于用户”，导致 prompt 视角歧义。
- 修复方式：
- `PromptAssembler` 在存在任一记忆段时新增统一“记忆解释规则”，明确记忆属于用户，且“我/我的”默认指用户。
- `MemoryPromptBuilder` 在长期记忆段和动态记忆段内新增局部说明“以下内容均为用户本人的记忆，不代表助手自身”。
- 本次未改数据库、FTS、记忆内容存储格式，只做 prompt 层最小修复。
- 本次新增文档：
- `docs/plans/2026-05-13-memory-user-perspective-fix-design.md`
- `docs/plans/2026-05-13-memory-user-perspective-fix-plan.md`
- 本次验证结果：
- `:app:testDebugUnitTest --tests "com.companion.chat.data.memory.MemoryPromptBuilderTest" --tests "com.companion.chat.data.context.PromptAssemblerTest" --tests "com.companion.chat.data.context.DefaultContextManagerTest"` 通过。
- `:app:testDebugUnitTest` 全量通过。
- `:app:assembleDebug` 通过。
- 2026-05-13 已补强“第二人称记忆归属”规则：
- 补充约束：在记忆解释规则中明确“我/我的”默认指用户，“你/你的”默认指助手或模型自己，避免“你是我的搭档”这类记忆继续发生第二人称误判。
- 已同步更新 `PromptAssemblerTest` 的完整断言，并新增第二人称归属专项断言。
- 定向验证结果：`:app:testDebugUnitTest --tests "com.companion.chat.data.context.PromptAssemblerTest"` 通过。
- 2026-05-13 第二人称归属补丁已重新完成真机部署：
- `:app:testDebugUnitTest` 全量通过。
- `:app:assembleDebug` 通过。
- 已执行卸载旧包、推送新 `app-debug.apk`、`pm install -r -t --user 0` 安装成功。
- 已重新启动应用并推送模型到 `/storage/emulated/0/Android/data/com.companion.chat/files/models/`。
- ADB 校验通过：模型文件存在，前台窗口为 `com.companion.chat/.MainActivity`，可进入人工复测阶段。
- 2026-05-13 已进入阶段四规划：
- 已新增 `docs/plans/2026-05-13-stage4-self-evolution-design.md`
- 已新增 `docs/plans/2026-05-13-stage4-self-evolution-plan.md`
- 阶段四范围确定为：Engine-B 管理、触发调度、偏好总结 JSON 解析、`user_preferences` 合并、confirmed 偏好 prompt 注入、设置页“自动学习偏好”开关。
- 当前判断：阶段三分支从测试、编译和真机重装证据看已具备合并检查点条件，但工作区仍有未提交改动，需先整理提交后再合并。
- 2026-05-13 阶段四第一版代码主链路已完成：
- 已在 `ContextConfigRepository` 增加 `autoPreferenceLearningEnabled` 开关读写，并在 `SettingsScreen` 增加“自动学习偏好”开关项。
- 已新增 `ExtractedPreference`、`PreferenceSummaryPromptBuilder`、`PreferenceSummaryParser`、`PreferenceRepository`、`SecondEngineManager`。
- `ChatViewModel` 已接入 confirmed 偏好 prompt 注入、3 分钟静置触发、切换会话触发、应用进后台触发、发送新消息时取消 Engine-B。
- `MainActivity` 已接入应用级 `ON_STOP` 生命周期监听，用于触发当前会话的后台偏好总结检查。
- 当前阶段四仍保持阶段三规则提取链路不变：
- “记住我叫小明”这类规则提取仍写入 `memories`，不走 `user_preferences`。
- 关闭“自动学习偏好”后仅阻止阶段四后台总结触发，不影响阶段三记忆提取与已有 confirmed 偏好注入。
- 2026-05-13 阶段四本地验证通过：
- `:app:testDebugUnitTest --tests "com.companion.chat.data.context.ContextConfigRepositoryTest"` 通过。
- `:app:testDebugUnitTest --tests "com.companion.chat.data.preferences.PreferenceSummaryPromptBuilderTest" --tests "com.companion.chat.data.preferences.PreferenceSummaryParserTest"` 通过。
- `:app:testDebugUnitTest --tests "com.companion.chat.data.preferences.PreferenceRepositoryTest"` 通过。
- `:app:testDebugUnitTest --tests "com.companion.chat.data.preferences.SecondEngineManagerTest"` 通过。
- `:app:testDebugUnitTest --tests "com.companion.chat.data.context.PromptAssemblerTest" --tests "com.companion.chat.data.context.DefaultContextManagerTest"` 通过。
- `:app:testDebugUnitTest` 全量通过。
- `:app:assembleDebug` 通过。
- 当前待完成项：
- 还未做阶段四真机联调，下一步需按“卸载旧 app -> 安装新 app -> 推送模型”完成设备侧验收。
- 真机阶段四验收需要人工配合观察开关行为、等待静置触发和检查回答是否体现 confirmed 偏好注入。
- 2026-05-13 阶段四第一轮真机联调结果：
- 已按“编译成功 -> 卸载旧 app -> 安装新 app -> 推送模型”完成重装，第二次启动后日志确认模型文件存在且 `engine.initialize` 返回 `Ready`。
- 真机日志确认阶段四后台触发链路正常：开启“自动学习偏好”后，`应用进入后台` 可触发 `阶段四总结完成`。
- 关闭开关时未再出现阶段四总结执行日志；开启开关后恢复触发，说明开关控制行为基本符合预期。
- 第一轮真机问题已定位：`阶段四总结完成` 已出现，但两轮实际结果均为 `extractedCount=0`，问题不在触发层，而在偏好总结输出格式或解析层。
- 2026-05-13 阶段四提取层修复已完成：
- `PreferenceSummaryPromptBuilder` 已改为更严格的“只输出 JSON 数组、不要解释和 Markdown 代码块”提示词，并补充示例输出。
- `PreferenceSummaryParser` 已增强为可解析带前后说明、Markdown 代码块包裹的 JSON，并兼容中文字段名与类别别名。
- `ChatViewModel` 已补充阶段四原始输出预览日志，便于后续真机直接判断是模型输出问题还是 parser 问题。
- 本轮定向验证结果：
- `:app:testDebugUnitTest --tests "com.companion.chat.data.preferences.PreferenceSummaryPromptBuilderTest" --tests "com.companion.chat.data.preferences.PreferenceSummaryParserTest"` 通过。
- `:app:assembleDebug` 通过。
- 当前下一步：
- 重新安装新包并推送模型后，需再做一轮阶段四真机复测，重点看 `阶段四原始输出` 与 `extractedCount` 是否大于 0。
- 2026-05-13 阶段四提取层修复复测通过：
- 已重新执行“卸载旧 app -> 安装新 app -> 推送模型 -> 重启应用”流程。
- 真机日志确认模型文件最终大小正确，第二次启动后 `engine.initialize` 返回 `Ready`。
- 本轮人工复测输入“以后请尽量简洁回答 / 我喜欢游戏和科幻 / 你可以叫我老王 / 我一般晚上聊天比较多”后，应用进入后台成功触发阶段四总结。
- `viewmodel_log.txt` 已记录阶段四原始输出预览，输出为合法 JSON 数组，包含 `style`、`interest`、`name`、`habit` 4 类偏好。
- 本轮关键结果：`阶段四总结完成: reason=应用进入后台, extractedCount=4`，说明“总结 prompt -> Engine-B 输出 -> parser 解析 -> 结构化提取”链路已打通。
- 当前剩余待验项：
- `PreferenceRepository` 现策略首次写入 `confidence=1`，confirmed 偏好注入要求 `confidence>=3`，因此若要继续做 prompt 注入真机验收，还需重复相同偏好至少 3 次或人工注入高置信度数据。
- 2026-05-13 已完成“模型统一抽取 memories + user_preferences”第一版开发：
- 阶段四后台链路不再只做偏好总结，已改为一次模型调用统一输出 `memories` 与 `user_preferences` 两个数组。
- 新增 `UnifiedExtractionPromptBuilder` 与 `UnifiedExtractionParser`，要求模型严格输出 JSON 对象，并同时解析两类结果。
- `memories.category` 已固定接入 `fact / preference / event / relation / time / other`。
- `user_preferences.category` 继续保留 `name / style / interest / habit / other`。
- `MemoryRepository` 已新增模型抽取写入入口与精确去重，避免阶段四重复触发时同一记忆反复插入。
- `ChatViewModel` 已改为：当“自动学习偏好”开启时，主记忆提取走后台模型统一抽取；若模型未产出记忆、失败、超时或取消，则回退到规则提取兜底。
- 当前规则提取器未删除，但已从主链路退化为兜底链路；关闭“自动学习偏好”时，发送消息仍沿用规则提取，避免已有记忆功能失效。
- 记忆 UI 与 prompt 展示已同步扩展新分类，新增 `relation / time / other`，并兼容旧数据中的 `relationship`。
- 本轮本地验证结果：
- `:app:testDebugUnitTest --tests "com.companion.chat.data.preferences.UnifiedExtractionPromptBuilderTest" --tests "com.companion.chat.data.preferences.UnifiedExtractionParserTest" --tests "com.companion.chat.data.memory.MemoryRepositoryWriteTest" --tests "com.companion.chat.data.memory.MemoryPromptBuilderTest" --tests "com.companion.chat.ui.memory.MemoryViewModelTest"` 通过。
- `:app:assembleDebug` 通过。
- 当前下一步：
- 需要重新真机安装并推送模型后，验证一次后台触发是否能同时写入 memories 和 user_preferences，并检查记忆页是否能看到新增分类数据。
- 2026-05-13 记忆页 Flow 刷新链路修复已完成：
- `MemoryDao` 新增 `observeAll(): Flow<List<Memory>>`，`MemoryRepository` 新增 `observeAllMemories()`。
- `MemoryViewModel` 已从一次性 `loadMemories()` 初始化改为启动即订阅 `memories` 表变化；页面停留期间如后台稍后写入记忆，UI 应可自动刷新。
- 本轮为适配 DAO 新接口，已补齐 `MemoryViewModelTest`、`MemoryLifecycleManagerTest`、`MemoryRepositoryPersistentTest`、`MemoryRepositoryWriteTest`、`MemoryRetrieverTest` 中假 DAO 的 `observeAll()` 实现。
- 本轮本地验证结果：
- `:app:assembleDebug` 通过。
- `:app:testDebugUnitTest --tests com.companion.chat.ui.memory.MemoryViewModelTest --tests com.companion.chat.data.memory.MemoryLifecycleManagerTest --tests com.companion.chat.data.memory.MemoryRepositoryPersistentTest --tests com.companion.chat.data.memory.MemoryRepositoryWriteTest --tests com.companion.chat.data.memory.MemoryRetrieverTest` 通过。
- 2026-05-13 已完成本轮真机重装与模型校验：
- 已执行“卸载旧 app -> 安装新 app -> 启动一次建目录 -> 推送模型 -> 强制停止并重启应用”完整流程。
- `viewmodel_log.txt` 确认第二次启动后模型文件存在且大小正确：`2588147712 bytes`。
- `viewmodel_log.txt` 确认本轮最终初始化结果为 `engine.initialize 返回, state = Ready`。
- 当前待复测项：
- 需要再次人工验证“打开记忆页后，再让应用进后台触发阶段四总结”时，记忆页是否会在页面停留期间自动出现新记忆。
- 2026-05-13 已完成“弱记忆补强”第一轮：
- `UnifiedExtractionPromptBuilder` 已强化提示词，明确要求优先提取兴趣、喜欢/不喜欢、习惯、时间规律、性格特征、自我描述、禁忌、回答偏好，并要求一句话中的多条稳定信息尽量拆开提取。
- `PreferenceMemoryDeriver` 已补强派生规则：
- `interest` 现可正确保留负向表达，如“不喜欢 / 讨厌 / 不爱”。
- `habit` 对“我一般怎样 / 我通常怎样”这类非时间表达不再简单误判为 `time`，实际时间规律仍优先归到 `time`。
- `other` 对“比较慢热 / 有点内向”这类自我特征会稳定补成用户视角记忆。
- `RuleBasedMemoryExtractor` 已补强兜底覆盖：
- 新增对 `我不喜欢...`、`我一般...`、`我通常...`、`我经常...`、`我比较...`、`我是个...的人`、`以后请...`、`希望你...` 的识别。
- 规则兜底已支持一句消息拆分多子句后提取多条记忆，不再固定 `.take(1)` 只保留一条。
- 本轮新增/更新测试覆盖：
- `UnifiedExtractionPromptBuilderTest`
- `PreferenceMemoryDeriverTest`
- `RuleBasedMemoryExtractorTest`
- 本轮本地验证结果：
- `:app:testDebugUnitTest --tests com.companion.chat.data.preferences.UnifiedExtractionPromptBuilderTest --tests com.companion.chat.data.preferences.PreferenceMemoryDeriverTest --tests com.companion.chat.data.memory.RuleBasedMemoryExtractorTest` 通过。
- `:app:assembleDebug` 通过。
- 当前下一步：
- 重新真机复测以下高价值表达是否能稳定进入记忆页：
- `我喜欢科幻和游戏`
- `我不喜欢太官方的回答`
- `我一般晚上十点后聊天`
- `我比较慢热`
- `以后请尽量直接一点，多举例`
- 2026-05-13 已完成弱记忆补强版真机重装：
- 已重新执行“卸载旧 app -> 安装新 app -> 启动一次建目录 -> 推送模型 -> 强制停止并重启应用”。
- 安装后首次启动仍先报模型不存在，推送模型并第二次启动后恢复正常，属于当前已知安装后首次读模型竞态。
- `viewmodel_log.txt` 确认第二次启动后模型文件存在且大小正确：`2588147712 bytes`。
- `viewmodel_log.txt` 确认本轮增强包最终初始化结果为 `engine.initialize 返回, state = Ready`。
- 2026-05-13 已完成阶段四收尾第一轮实现：
- 后台总结稳定性已调整为“超时/取消先自动重试一次，只有二次超时或取消后才走规则兜底”，不再首次超时就立即写入 1 条兜底记忆。
- `SecondEngineManager` 在 `ChatViewModel` 中的阶段四 summary timeout 已从默认 60 秒提升到 90 秒，以降低首轮后台总结超时概率。
- `UnifiedExtractionParser` 已增加结构纠偏：当模型把偏好信息误输出到 `memories` 区时，会自动恢复出对应的 `user_preferences`，覆盖 `style / interest / habit / other` 等场景。
- 阶段四总结完成后已新增偏好合并日志，记录 `merged` 与 `confirmed` 数量，便于后续真机确认 `user_preferences -> confirmed -> prompt 注入` 闭环。
- 本轮新增/更新测试覆盖：
- `UnifiedExtractionParserTest`
- `PreferenceRepositoryTest`
- `SecondEngineManagerTest`
- `PromptAssemblerTest`
- 本轮本地验证结果：
- `:app:testDebugUnitTest --tests com.companion.chat.data.preferences.UnifiedExtractionParserTest --tests com.companion.chat.data.preferences.PreferenceRepositoryTest --tests com.companion.chat.data.preferences.SecondEngineManagerTest --tests com.companion.chat.data.context.PromptAssemblerTest` 通过。
- `:app:assembleDebug` 通过。
- 当前下一步：
- 重装真机后复测以下阶段四收尾目标：
- 首次后台触发不再先出现“只落 1 条兜底再变多条”的明显分裂结果。
- 同一轮总结日志中 `preferenceCount` 不再稳定为 0。
- 重复表达相同偏好至少 3 次后，发送前日志可看到 `confirmed 偏好注入: count>0`，并观察回答是否体现该偏好。
- 2026-05-13 已完成阶段四收尾版真机重装：
- 已重新执行“卸载旧 app -> 安装新 app -> 启动一次建目录 -> 推送模型 -> 强制停止并重启应用”。
- 安装后首次启动仍先报模型不存在，推送模型并第二次启动后恢复正常，属于当前已知安装后首次读模型竞态。
- `viewmodel_log.txt` 确认第二次启动后模型文件存在且大小正确：`2588147712 bytes`。
- `viewmodel_log.txt` 确认本轮阶段四收尾版最终初始化结果为 `engine.initialize 返回, state = Ready`。
- 2026-05-13 新一轮真机复测结果：
- 用户最初反馈“没有任何记忆”，但继续等待后记忆最终出现，说明当前不是“写库失败”或“UI 永远不刷新”。
- 本轮 `viewmodel_log.txt` 关键证据：
- `21:31:16` 出现 `阶段四取消: reason=应用进入后台, retry=1`
- `21:31:19` 出现 `阶段四跳过: 前台仍在生成, reason=应用进入后台-重试`
- `21:32:53` 出现 `阶段四原始输出`
- `21:32:53` 出现 `阶段四偏好合并完成: merged=7, confirmed=0`
- `21:32:53` 出现 `阶段四总结完成: memoryCount=14, preferenceCount=7, extractedCount=21`
- 结论：本轮阶段四已经能同时写入 `memories` 和 `user_preferences`，但后台总结完成时间仍偏晚，导致用户会先误判为“没有任何记忆”。
- 当前剩余核心问题已从“写不进去/刷不出来”收缩为“阶段四完成时延过长，需要继续优化用户可感知时序”。
- 2026-05-13 已新增阶段五具体实施计划文档：
- `docs/plans/2026-05-13-stage5-skill-management-plan.md`
- 计划依据：
- `COMPANIONCHAT_DESIGN.md` 中“技能管理（Prompt 模板）”设计目标、内置技能、切换流程和 UI 草图
- `COMPANIONCHAT_TEST_CHECKLIST.md` 中阶段五 `5.1`、`5.2`、`5.3` 全部验收项
- 当前阶段五现状判断：
- `Skill` 实体、`SkillDao`、数据库内置四技能种子已存在
- 设置页“角色管理”入口和 `CharacterManagementScreen` 仍是占位实现
- `ChatViewModel.baseSystemPrompt` 已是技能切换可复用的主 prompt 入口
- 阶段五计划拆分为四个实施阶段：
- 技能仓库与业务规则
- 技能管理 UI 与表单/删除确认
- 技能切换到主引擎 prompt 与 Conversation 重建
- 设置页接入、编译与真机验收闭环
- 2026-05-13 阶段五需求已按新方向重设计：
- `skills` 与“角色卡”正式分离，不再复用同一页面或同一概念。
- 设置页后续将同时提供两个独立入口：`角色管理` 与 `Skills 管理`。
- `CharacterManagementScreen` 不再改造成技能页，而是重做为真实角色卡管理页，支持创建、编辑、删除、激活完整角色卡。
- 角色卡定位为日常聊天陪伴人格，skills 定位为工作任务能力；两者允许同时启用。
- 激活规则确定为：同一时间仅 1 个激活角色卡 + 1 个激活 skill。
- 内置 skill 已调整需求：移除 `通用助手`、`代码助手`、`写作助手`，仅保留 `翻译助手` 作为唯一内置项。
- 唯一内置 `翻译助手` 的核心 prompt 已确定为：翻译时考虑使用者的语境、文化背景以及母语情况。
- `ChatViewModel` 后续将继续复用 `baseSystemPrompt` 作为主入口，但内容改为“基础 prompt + 激活角色卡 prompt + 激活 skill prompt”，再继续走现有记忆、偏好、上下文链路。
- 本轮已新增设计与实施文档：
- `docs/plans/2026-05-13-stage5-role-skill-separation-design.md`
- `docs/plans/2026-05-13-stage5-role-skill-separation-plan.md`
- 当前阶段五旧计划 `docs/plans/2026-05-13-stage5-skill-management-plan.md` 已不再完全适用，后续实施应以“角色卡与 skills 分离”的新设计和新计划为准。
- 2026-05-13 阶段五“角色卡与 skills 分离”第一版代码已完成：
- 数据层：
- 新增 `RoleCard` 实体与 `RoleCardDao`。
- 新增 `RoleCardRepository`、`RoleCardPromptBuilder`、`SkillRepository`。
- `CompanionDatabase` 已升级到 `version = 2`，并新增 `1 -> 2` migration：
- 创建 `role_cards` 表。
- 清理旧内置 skills，仅保留唯一内置 `翻译助手`。
- 将 `翻译助手` prompt 固定为“翻译时考虑使用者的语境、文化背景以及母语情况”的版本。
- UI 与导航：
- `CharacterManagementScreen` 已从占位页替换为真实角色卡管理页，支持创建、编辑、删除、激活。
- 新增 `SkillsManagementScreen`，用于管理唯一内置翻译助手和用户自定义 skills。
- 设置页已拆分出两个独立入口：`角色管理` 与 `Skills 管理`。
- `MainActivity` 与 `SettingsRoutes` 已接入新页面路由与激活回调。
- 聊天主链路：
- `ChatViewModel` 已接入 `RoleCardRepository`、`SkillRepository` 与 `RoleCardPromptBuilder`。
- `baseSystemPrompt` 现改为“默认基础 prompt + 激活角色卡 prompt + 激活 skill prompt”组合结果。
- 新增 `activateRoleCard()` 与 `activateSkill()`，切换后会尝试复用现有上下文链路重建 `Conversation`，并保留当前会话消息。
- 本轮新增测试：
- `SkillRepositoryTest`
- `RoleCardRepositoryTest`
- `RoleCardPromptBuilderTest`
- `RoleManagementViewModelTest`
- `SkillsManagementViewModelTest`
- 本轮本地验证结果：
- `:app:testDebugUnitTest --tests "com.companion.chat.data.skill.*" --tests "com.companion.chat.data.role.*" --tests "com.companion.chat.ui.settings.*"` 通过。
- `:app:testDebugUnitTest` 全量通过。
- `:app:assembleDebug` 通过。
- 本轮真机部署已按固定流程完成：
- 设备 `aa972376` 在线。
- 已卸载旧包 `com.companion.chat`。
- 已将新 `app-debug.apk` 推送到 `/data/local/tmp/companionchat-stage5.apk` 并通过 `pm install -r -t --user 0` 安装成功。
- 已启动一次应用创建目录。
- 已将模型 `D:\Desktop\phone\models\gemma-4-E2B-it.litertlm` 重新推送到 `/sdcard/Android/data/com.companion.chat/files/models/gemma-4-E2B-it.litertlm`。
- 当前下一步：
- 进入人工真机功能复测，重点验证：
- 设置页两个入口是否都可进入。
- 角色卡创建/编辑/删除/激活是否正常。
- `Skills 管理` 中是否只剩唯一内置 `翻译助手`。
- 激活角色卡与 skill 后，回答是否同时体现人格与任务能力。
- 2026-05-14 阶段六“语音模型、语音克隆与图片生成”第一版代码已完成：
- 工程边界：
- 本阶段只修改根目录当前有效源码 `app/`，未使用旧 `CompanionChat/app/` 作为实现目标。
- 新增阶段六设计与实施记录：
- `docs/plans/2026-05-14-stage6-voice-image-generation-design.md`
- `docs/plans/2026-05-14-stage6-voice-image-generation-plan.md`
- 数据层：
- `CompanionDatabase` 已升级到 `version = 3`，新增 `2 -> 3` migration。
- `RoleCard` 已增加 `avatarImageUri`、`galleryImageUris`、`imageStylePrompt`、`voiceProfileUri`、`voiceMode`、`voiceDisplayName`。
- `RoleCardRepository` 创建和更新接口已支持图片与语音字段。
- Prompt：
- `ChatViewModel.DEFAULT_BASE_SYSTEM_PROMPT` 已调整为更贴近 Anime Companion 的本地私密、长期陪伴、自然亲密、中文优先、少说教风格。
- `RoleCardPromptBuilder` 已把 `imageStylePrompt` 纳入角色设定摘要，同时保持语音 URI 不进入 LLM prompt。
- 语音：
- `VoiceInputEngine` 已新增 `warmUp()` 和 `WarmedUp` 状态。
- `AndroidVoiceInputEngine` 已支持进入对话页后预热语音组件，但不自动录音。
- `VoiceOutputEngine` 已新增 `VoiceOutputConfig` 与 `VoiceOutputMode`。
- 新增 `RoleAwareVoiceOutputEngine`，播放回复时读取激活角色卡语音配置；真实克隆后端未接入时自动回退 Android TTS。
- 图片生成：
- 新增 `ImageGenerationEngine`、`ImageGenerationConfig`、`ImageGenerationState`、`ImageGenerationPurpose`、`ImageGenerationConfigRepository`、`HttpImageGenerationEngine`。
- HTTP 图片生成配置支持 Base URL、API Key、model、request template、response image field path、timeout。
- 生成结果支持 URL、data URL、base64，并保存到应用私有目录 `files/generated_images/...`。
- UI：
- `ModelConfigScreen` 已增加图片生成 HTTP 配置区。
- `SettingsScreen` 已增加“图片生成”入口说明。
- `VoiceSettingsScreen` 已从占位页升级为语音预热、语音输出模式和 TTS 回退说明页。
- `RoleCardEditorDialog` 已增加图片 URI、图片风格提示词、语音参考音频 URI、语音模式、语音显示名称字段。
- `CharacterManagementScreen` 已展示角色图片和语音配置摘要。
- 本轮新增/更新测试：
- `RoleCardRepositoryTest`
- `RoleCardPromptBuilderTest`
- 本轮本地验证结果：
- `./gradlew :app:testDebugUnitTest` 通过。
- `./gradlew :app:assembleDebug` 通过。
- 验证准备：
- 已为当前根工程新增 `local.properties`，指向 `/home/yrd/Android/Sdk`。
- 已给根目录 `gradlew` 增加执行权限。
- 当前已知限制：
- 本阶段未接入 sherpa-onnx/Whisper 等端侧 ASR 模型。
- 本阶段未接入真实语音克隆后端，`CLONE` 配置当前仍回退系统 TTS。
- 图片生成依赖用户配置联网 HTTP 服务，尚未做真机联网生成端到端复测。
- 2026-05-14 阶段六无线真机调试记录：
- 已通过无线调试配对并连接设备 `192.168.1.24:33817`。
- 已将当前 `app-debug.apk` 推送到 `/data/local/tmp/companionchat-stage6.apk` 并通过 `pm install -r -t` 安装成功。
- 应用可启动，`pidof com.companion.chat` 有进程，未在本轮 logcat 中发现 `AndroidRuntime` / `FATAL EXCEPTION`。
- `app_init_log.txt` 显示应用初始化、旧 JSON 迁移跳过、记忆生命周期维护均完成。
- 当前设备模型目录只有旧 `.gguf`：`Gemma-4-E2B-Uncensored-HauhauCS-Aggressive-Q4_K_P.gguf`。
- 当前阶段六包默认寻找 `/storage/emulated/0/Android/data/com.companion.chat/files/models/gemma-4-E2B-it.litertlm`，设备上不存在，因此 `engine.initialize` 返回 `Error(message=模型文件不存在或为空...)`。
- 已确认阶段六新增 UI 可进入：
- 设置页显示“图片生成”入口。
- 模型配置页显示“图片生成 HTTP 配置”、`Base URL`、`API Key`、`Model`、`Request Template`、`Response Image Field Path`、`Timeout Millis`。
- 语音设置页显示“预热但不自动录音”“系统 TTS / 角色克隆配置”“克隆后端未接入时回退系统 TTS”等说明。
- 角色卡编辑弹窗显示“头像图片 URI”“图库图片 URI”“图片风格提示词”“语音参考音频 URI”“语音模式（SYSTEM_TTS / CLONE）”“语音显示名称”。
- 当前真机剩余限制：
- 因缺少 `.litertlm` 模型文件，本轮未验证真实聊天生成。
- 未配置联网图片生成 HTTP 服务，本轮未做图片生成端到端请求。
- 2026-05-14 阶段六无线真机继续调试：
- 已定位 Edge Gallery 下载的 Gemma4 E2B 模型：
- `/sdcard/Android/data/com.google.ai.edge.gallery/files/Gemma_4_E2B_it/20260325/gemma4_2b_v09_obfus_fix_all_modalities_thinking.litertlm`
- 已复制到 CompanionChat 默认模型路径：
- `/sdcard/Android/data/com.companion.chat/files/models/gemma-4-E2B-it.litertlm`
- 重启应用后 `viewmodel_log.txt` 确认：
- 模型文件存在。
- 文件大小 `2538766336 bytes`。
- `engine.initialize 返回, state = Ready`。
- 真机聊天验证：
- 发送 `hello` 后模型正常返回中文回复，页面回到“已就绪”。
- 通过数据库插入阶段六测试角色卡 `Mika`，包含头像图片 URI、图库 2 张、`CLONE` 语音模式、语音显示名称。
- 角色管理页已显示：
- `图片：头像已配置，图库 2 张`
- `语音：Mika voice`
- 激活角色卡后发送 `who_are_you`，模型回复“我是 Mika”，说明角色 prompt 链路生效。
- 输入法调试：
- 用户要求不要微信输入法、不要百度输入法。
- 已找到 vivo 系统安全键盘组件 `com.vivo.secime.service/.SecIME`。
- 普通 `ime enable/set` 会提示 `Unknown input method`，但通过 secure settings 写入后，默认输入法可指向该组件。
- 使用安全键盘默认状态输入 `test_secure_ime` 后，聊天发送与模型回复均正常。
- 本轮发现的待查项：
- 点击聊天页麦克风按钮后，本轮未观察到 `VoiceInputEngine` / `SpeechRecognizer` logcat 记录，也未看到“正在听...”状态变化；设备上 `RECORD_AUDIO` 权限已授予。后续需要继续定位是点击坐标/Compose 命中问题，还是语音入口回调未触发。
- 图片生成 HTTP 端到端仍待配置真实服务后复测。
