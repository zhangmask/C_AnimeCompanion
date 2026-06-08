---
goal: CompanionChat 阶段六：语音预热、角色语音配置与联网图片生成
version: 1.0
date_created: 2026-05-14
last_updated: 2026-05-14
owner: SOLO Code Assistant
status: Implemented
tags: [design, stage6, voice, image-generation, role-card, room, compose]
---

# Introduction

阶段六基于当前根目录 Android 工程 `app/` 实施，不再参考旧 `CompanionChat/app/`。本阶段只做增量能力接入，保留阶段二到阶段五已经完成的上下文压缩、Room 会话、记忆系统、偏好学习、角色卡与 Skills 分离链路。

UI 继续沿用现有 Compose + Material3 设置页、列表页和弹窗风格，不引入新的视觉体系。

## 1. Requirements & Constraints

- 语音识别进入对话页后只预热组件，不自动录音。
- 点击麦克风后才请求权限并开始听写。
- 语音输出需要支持角色级配置，但克隆后端缺失时必须回退 Android TTS。
- 角色卡扩展图片与语音字段，旧数据经 Room v2 -> v3 migration 保留。
- 图片生成首版采用通用 HTTP 配置，生成结果保存到应用私有目录。
- 图片生成失败不得阻断聊天主链路。
- 语音参考音频 URI 不进入 LLM prompt。
- 默认基础 prompt 更贴合本地私密、长期陪伴、自然亲密、中文优先、少说教的 Anime Companion 定位。

## 2. Data Model

`RoleCard` 在 v3 增加：

- `avatarImageUri`
- `galleryImageUris`
- `imageStylePrompt`
- `voiceProfileUri`
- `voiceMode`
- `voiceDisplayName`

`galleryImageUris` 复用现有 Room `Converters` 的 `List<String>` JSON 转换。`voiceMode` 以字符串落库，对应运行时 `VoiceOutputMode`。

## 3. Voice Design

`VoiceInputEngine` 增加 `warmUp()` 与 `VoiceInputEvent.WarmedUp`。`AndroidVoiceInputEngine.warmUp()` 只创建并准备 `SpeechRecognizer`，不调用 `startListening()`。

`VoiceOutputEngine.speak()` 增加 `VoiceOutputConfig` 参数。`RoleAwareVoiceOutputEngine` 读取当前激活角色卡的语音配置；当 `CLONE` 模式存在但没有克隆后端时，仍转给系统 TTS，实现可用优先的降级路径。

## 4. Image Generation Design

新增通用接口：

- `ImageGenerationEngine`
- `ImageGenerationConfig`
- `ImageGenerationState`
- `HttpImageGenerationEngine`

HTTP 配置包含：

- Base URL
- API Key
- model
- request template
- response image field path
- timeout

模板支持 `{{model}}` 与 `{{prompt}}`。响应字段路径支持类似 `data.0.url`、`data.0.b64_json`。结果支持 URL 下载、data URL 和裸 base64，最终保存到 `files/generated_images/...`。

## 5. Prompt Design

基础 prompt 组合顺序保持为：

1. 默认基础 prompt
2. 激活角色卡 prompt
3. 激活 skill prompt
4. confirmed 偏好
5. 常驻长期记忆
6. 动态记忆
7. 摘要和最近对话

角色卡的 `imageStylePrompt` 可作为视觉风格参考进入角色设定摘要；语音 URI、头像 URI、图库 URI 不进入 prompt。

## 6. Known Limits

- 本阶段已接入本地 SenseVoice ASR，仍不接入 Whisper。
- `moss-tts-nano` 已完成真实 OpenMOSS browser ONNX 模型包校验与系统 TTS 回退；真实合成仍需要实现 prefill/decode/local decoder/audio tokenizer decode 的自回归 runner。
- 本地 DreamLite 已完成 submodule 与模型包检查器；官方移动端权重/包可用前不承诺真实出图。
- 角色市场、购买、账号体系、公开发布均不在本阶段范围内。
