# Stage6 Voice And Image Generation Implementation Plan

**Goal:** 在当前根目录 Android 工程 `app/` 中增量接入语音预热、角色语音配置、语音克隆回退架构、联网图片生成 HTTP 配置、角色图片素材字段，并保持阶段二到阶段五链路稳定。

**Architecture:** Room 从 v2 升级到 v3 扩展 `RoleCard`；语音输出经 `RoleAwareVoiceOutputEngine` 读取激活角色卡配置并回退系统 TTS；图片生成通过 `ImageGenerationEngine` 抽象与 `HttpImageGenerationEngine` 实现，设置页保存通用 HTTP 配置；角色编辑弹窗管理图片和语音字段。

**Tech Stack:** Kotlin、Jetpack Compose、Material3、Room、Coroutines、Android SpeechRecognizer、Android TextToSpeech、HttpURLConnection、JUnit4

## Task 1: 扩展角色卡 schema

- 修改 `RoleCard`，增加头像图片、图库、图片风格、语音参考音频、语音模式、语音显示名称字段。
- 修改 `CompanionDatabase` 到 `version = 3`，新增 `MIGRATION_2_3`。
- 复用 `Converters` 保存 `galleryImageUris`。
- 更新 `RoleCardRepository` 创建与更新参数，保留旧调用默认值。

## Task 2: 更新角色 prompt

- 修改 `RoleCardPromptBuilder`，将 `imageStylePrompt` 纳入角色设定摘要。
- 保持 URI 类字段不进入 prompt。
- 更新 `RoleCardPromptBuilderTest` 覆盖图片风格进入、语音 URI 不进入。

## Task 3: 接入语音预热与角色语音回退

- 修改 `VoiceInputEngine` 增加 `warmUp()` 和 `WarmedUp` 事件。
- 修改 `AndroidVoiceInputEngine`，预热时准备 `SpeechRecognizer`，不启动录音。
- 修改 `VoiceOutputEngine`，增加 `VoiceOutputConfig` 与 `VoiceOutputMode`。
- 新增 `RoleAwareVoiceOutputEngine`，读取激活角色卡语音配置，克隆后端不可用时回退 Android TTS。
- 修改 `ChatViewModel`，初始化时预热语音输入，播放回复时走角色感知语音输出。

## Task 4: 接入联网图片生成

- 新增 `ImageGenerationConfig`、`ImageGenerationState`、`ImageGenerationPurpose`。
- 新增 `ImageGenerationConfigRepository`，通过 SharedPreferences 保存 HTTP 配置。
- 新增 `ImageGenerationEngine` 与 `HttpImageGenerationEngine`。
- 支持模板渲染、Authorization Bearer API Key、响应字段路径读取、URL/base64 保存到应用私有目录。
- 在 `ChatUiState` 暴露图片生成状态和错误信息。

## Task 5: 更新设置与角色 UI

- `ModelConfigScreen` 增加图片生成 HTTP 配置区。
- `SettingsScreen` 在模型区域增加“图片生成”入口说明。
- `VoiceSettingsScreen` 从占位页升级为可读配置说明页，说明预热、不自动录音、系统 TTS 回退。
- `RoleCardEditorDialog` 增加图片和语音字段。
- `CharacterManagementScreen` 在角色卡列表展示图片/语音配置摘要。

## Task 6: 验证

- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:assembleDebug`

## Implementation Result

已完成全部任务。本阶段验证结果：

- `./gradlew :app:testDebugUnitTest` 通过。
- `./gradlew :app:assembleDebug` 通过。

已知限制：

- `CLONE` 已接入 `moss-tts-nano` 本地模型包校验与回退框架；当前本地包是 OpenMOSS browser ONNX 拆分格式，真实自回归 runner 完成前回退系统 TTS。
- 图片生成依赖用户配置 HTTP 服务；未配置 Base URL 时返回明确错误。
- 本地 DreamLite 已通过 `third_party/DreamLite` submodule 和模型包检查器接入，官方移动端权重/包可用前只返回明确“模型尚未准备”错误，不做真实出图。
- 未做真机联网图片生成端到端验证。
