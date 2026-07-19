# 自定义 API 推理后端 + TTS 自动朗读修复 + 缓存启用

## Context

用户报告三个问题：

1. **自动朗读不生效** — 无论选系统 TTS 还是 MOSS，发消息后 AI 回复不自动播放
2. **MOSS 语音缓存被禁用** — 之前调试时注释掉了缓存写入逻辑，现在需要重新启用（按 messageId 持久化 WAV 到数据库）
3. **本地推理太慢** — 用户希望支持自定义远程 API（OpenAI 兼容）作为替代推理后端，在设置里配置后可切换

***

## 任务一：修复自动朗读（已加诊断日志，待验证）

**状态**: 已在 [ChatViewModel.kt](file:///c:/Users/72952/OneDrive/Desktop/ui/CompanionChat/app/src/main/java/com/companion/chat/ui/chat/ChatViewModel.kt#L1008) 的 `startAutoTts()` 和 `finishAutoTts()` 加了 `logToFile` 诊断日志，APK 已安装。

**验证步骤**:

1. 确认语音设置里"自动朗读"开关为开启
2. 发一条消息让 AI 回复
3. 拉 `tts_run.log` 查看 `startAutoTts: autoPlayTts=?` 和 `finishAutoTts:` 日志
4. 根据日志判断是设置读取问题、触发时机问题、还是 `voiceOutputEngine.speak()` 内部问题

***

## 任务二：重新启用 MOSS TTS 缓存（已改代码，待编译）

**状态**: 已修改 [RoleAwareVoiceOutputEngine.kt](file:///c:/Users/72952/OneDrive/Desktop/ui/CompanionChat/app/src/main/java/com/companion/chat/engine/RoleAwareVoiceOutputEngine.kt#L116) 的 `speakWithCache()`。

**改动要点**:

1. 先查 `getCachedAudioUri(messageId)` → 命中且文件存在则直接播放（跳过合成）
2. 未命中 → 调 `synthesizeAndPlay()` 合成播放
3. 合成成功 → `saveCachedAudioUri(messageId, mergedUri)` 写入数据库 MessageEntity.audioUri

**已有依赖**: `getCachedAudioUri`/`saveCachedAudioUri` lambda 在 [AppContainer.kt:92-93](file:///c:/Users/72952/OneDrive/Desktop/ui/CompanionChat/app/src/main/java/com/companion/chat/AppContainer.kt#L92) 已注入，`mergeOrPickAudioUri`/`cachedAudioFileExists`/`waitForPlaybackComplete` 辅助函数已存在。

***

## 任务三：自定义 API 推理后端（核心新功能）

### 3.1 数据层

**新建** **`data/local/entity/CustomApiConfig.kt`**:

```kotlin
@Entity(tableName = "custom_api_configs")
data class CustomApiConfig(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val apiKey: String,
    val baseUrl: String,          // 完整 URL，如 https://api.deepseek.com
    val model: String,            // 如 deepseek-chat
    val apiFormat: String,        // "OPENAI" | "ANTHROPIC"
    val customParams: String = "{}", // JSON 文本，记事本编辑的额外参数
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

**新建** **`data/local/dao/CustomApiConfigDao.kt`**: `getAll(): List`, `getById(id)`, `getActive()`, `insert(config): Long`, `update(config)`, `delete(config)`, `deactivateAll()`, `activate(id)`

**新建** **`data/engine/CustomApiConfigRepository.kt`**: 封装 DAO，提供 `getAll()`, `getActive()`, `upsert(config)`, `delete(id)`, `activate(id)`

**修改** **`data/local/CompanionDatabase.kt`**:

* entities 数组追加 `CustomApiConfig::class`

* version 改 11

* 新增 `MIGRATION_10_11`（CREATE TABLE custom\_api\_configs）

* 新增 `abstract fun customApiConfigDao(): CustomApiConfigDao`

* `.addMigrations(..., MIGRATION_10_11)`

### 3.2 引擎接口层

**修改** **`data/engine/InferenceEngine.kt`**:

* `ModelRuntime` 枚举加 `CUSTOM_API`

* `EngineConfig` 加 `val customApiConfig: CustomApiConfig? = null`

**修改** **`data/engine/ModelConfigRepository.kt`**:

* `ModelConfig` 加 `useCustomApi: Boolean = false`、`activeCustomApiConfigId: Long = -1`

* SharedPreferences 加 key `use_custom_api`、`active_custom_api_config_id`

* `getConfig()`/`updateConfig()` 同步读写

* `toEngineConfig()` 在 `useCustomApi` 时从 `CustomApiConfigRepository` 取 active 配置填入 `customApiConfig`

* `resolveModelPath()` 的 `when` 补 `CUSTOM_API -> ""` 分支

### 3.3 引擎实现层

**新建** **`engine/CustomApiInferenceEngine.kt`**: 实现 `InferenceEngine` 接口

* `initialize(config)`: 校验 `config.customApiConfig != null`，置 `Ready`

* `sendMessageStream(messages)`: 用 `HttpURLConnection` + `callbackFlow` 在 `Dispatchers.IO` 发 POST

  * OpenAI: `${baseUrl}/v1/chat/completions`，header `Authorization: Bearer $apiKey`、`Accept: text/event-stream`

  * body: `{"model":"...","messages":[...],"stream":true, ...customParams}`

  * 逐行读 `data: `  前缀，提取 `choices[0].delta.content` → `trySend`

  * `[DONE]` 关闭流

* `rebuildConversation`/`replayMessages`: 返回 true（API 无状态）

* `release()`: 取消连接

**修改** **`engine/InferenceEngineFactory.kt`**:

```kotlin
ModelRuntime.CUSTOM_API -> CustomApiInferenceEngine(context)
```

### 3.4 ChatViewModel 适配

**修改** **`ui/chat/ChatViewModel.kt`** (`initializeEngine` \~line 1266):

* 读 `modelConfig` 后，若 `useCustomApi && activeCustomApiConfigId >= 0`，跳过本地文件存在性检查

* `factory.create(CUSTOM_API)` → `engine.initialize(config)`（config 已带 customApiConfig）

* 其余流程不变

### 3.5 UI 层

**修改** **`ui/settings/ModelConfigScreen.kt`** (\~line 178):

* 推理后端卡片最上方插入 `ModelRuntimeOptionItem("自定义 API", ..., selected = modelConfig.useCustomApi, onClick = { viewModel.setUseCustomApi(true) })`

* 原两项的 `selected` 改为 `!modelConfig.useCustomApi && modelConfig.runtime == ...`

* `useCustomApi == true` 时：下方渲染"详细设置"按钮 → `onNavigateToCustomApiList()`，隐藏本地 modelPath/参数块

* 新增参数 `onNavigateToCustomApiList: () -> Unit`

**新建** **`ui/settings/CustomApiConfigListScreen.kt`**:

* Scaffold + TopBar（返回）+ LazyColumn 列出配置（name + model + baseUrl）

* 点击配置项 → `viewModel.activate(id)` 设为活跃

* FAB"添加" → `onAdd()` 跳转编辑页（configId = -1）

* 每项带编辑/删除图标

**新建** **`ui/settings/CustomApiConfigEditScreen.kt`**:

* 表单字段：name, apiKey, baseUrl, model, apiFormat（RadioButton: OpenAI 兼容 / Anthropic）

* TopBar 右侧"更多"按钮 → 切换记事本模式：TextField 显示当前配置 JSON（含 customParams），可手动编辑

* 保存 → `viewModel.save(config)` → popBack；取消 → popBack

**新建** **`ui/settings/CustomApiConfigViewModel.kt`**:

* 持 `CustomApiConfigRepository`，`uiState` 暴露 list/activeId/editingConfig

* 方法：`loadList()`, `activate(id)`, `save(config)`, `delete(id)`, `startEdit(id?)`

### 3.6 导航与 DI

**修改** **`ui/navigation/AppNavigation.kt`** `SettingsRoutes`:

```kotlin
const val CUSTOM_API_LIST = "settings/custom-api"
const val CUSTOM_API_EDIT = "settings/custom-api/edit/{configId}"
fun customApiEdit(configId: Long) = "settings/custom-api/edit/$configId"  // -1 = 新建
```

**修改** **`MainActivity.kt`** NavHost 注册两个 composable；`ModelConfigScreen` 调用增 `onNavigateToCustomApiList`

**修改** **`AppContainer.kt`**:

* `val customApiConfigRepository by lazy { CustomApiConfigRepository(database.customApiConfigDao()) }`

* `ModelConfigRepository(application, customApiConfigRepository)` 注入

**修改** **`AppViewModelFactory.kt`**: 增 `CustomApiConfigViewModel` 分支

***

## 实现顺序

1. **任务一+二**（已完成代码改动）: 编译 → 安装 → 测试自动朗读 + 验证缓存
2. **任务三** 按依赖顺序:

   * 2a. 数据层: Entity + DAO + Repository + Migration

   * 2b. 接口层: ModelRuntime 枚举 + EngineConfig + ModelConfigRepository

   * 2c. 引擎层: CustomApiInferenceEngine + Factory

   * 2d. ChatViewModel 适配

   * 2e. UI 层: 三个 Screen + ViewModel

   * 2f. 导航 + DI 接线

***

## 验证方法

### 自动朗读

1. 语音设置 → 开启"自动朗读"
2. 发消息 → AI 回复 → 应自动播放 TTS
3. 拉 `tts_run.log` 确认 `startAutoTts: autoPlayTts=true` 和 `finishAutoTts:` 日志

### TTS 缓存

1. 点击某条消息的播放按钮 → 等待合成完成
2. 再次点击同一条消息 → 应立即播放（日志显示"缓存命中"）
3. 查数据库 `messages.audioUri` 字段非空

### 自定义 API

1. 设置 → 模型配置 → 推理后端选"自定义 API"
2. 点"详细设置" → 添加配置（填 api\_key, base\_url, model）
3. 点"更多" → 记事本编辑自定义参数 → 保存
4. 返回聊天 → 发消息 → 应通过 API 流式生成回复（非本地推理）
5. 切回本地后端 → 应恢复本地推理

