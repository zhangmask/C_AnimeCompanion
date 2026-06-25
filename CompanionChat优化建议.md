# CompanionChat — 应用优化建议报告（含修改方案）

> 生成日期：2026-06-25
> 覆盖范围：全部模块

---

## 🔴 P0 — 架构/稳定性问题

### OPT-001：`ChatViewModel` 过大（1825 行），违反单一职责

**文件**：`ui/chat/ChatViewModel.kt`

**问题**：ChatViewModel 承担了过多职责：对话管理、推理调度、记忆调用、偏好学习、语音 I/O、前台 Service、文件处理。

**修改方案**（分 3 阶段）：

**阶段 1：抽出语音控制（1h）**
```kotlin
// 新建 ui/voice/VoiceViewModel.kt
class VoiceViewModel(...) {
    // 从 ChatViewModel 搬移：语音输入/输出状态、TTS控制、VAD控制
    fun startVoiceInput() { ... }
    fun stopVoiceInput() { ... }
    fun speak(text: String) { ... }
}
```

`ChatViewModel` 改引用 `VoiceViewModel`：
```kotlin
class ChatViewModel(
    private val voiceViewModel: VoiceViewModel,
    ...
)
```

**阶段 2：抽出记忆/偏好（2h）** — 已有独立的 `MemoryViewModel.kt`，但 `ChatViewModel` 中还直接操作 `MemoryRepository` 和 `PreferenceLearningCoordinator`。改为注入 `MemoryViewModel` 代理调用。

**阶段 3：UseCase 模式（3h）** — 引入 `chat/SendMessageUseCase.kt`、`chat/StreamResponseUseCase.kt` 等 UseCase 类。

---

### OPT-002：多处 `runCatching` 静默吞没异常

**文件与修改方案**：

**① `CompanionChatApplication.kt:35`**
```kotlin
// 改前
}.onFailure {
    logToFile("ensureInitialized 失败: ${it.javaClass.simpleName}: ${it.message}")
}
// 改后
}.onFailure {
    logToFile("ensureInitialized 失败: ${it.javaClass.simpleName}: ${it.message}")
    android.util.Log.e("CompanionChat", "启动初始化失败", it)
}
```

**② `MemoryLifecycleManager.kt:49`**
```kotlin
// 改前
runCatching { runDailyDecay() }
// 改后
try {
    runDailyDecay()
} catch (e: Exception) {
    android.util.Log.e("MemoryLifecycle", "每日衰减执行失败", e)
}
```

**③ `MemoryViewModel.kt:58,80-81,105-106`**
```kotlin
// 改前
} catch (_: Exception) {}
// 改后
} catch (e: Exception) {
    android.util.Log.e("MemoryViewModel", "操作失败", e)
}
```

**④ `ChatViewModel.kt` 中所有 `runCatching`**
搜索 `runCatching` → 在不影响UI体验的位置加 `Log.e` 输出。

---

### OPT-003：`AppContainer` 中 `secondEngineManager` 被注释掉，DI 断裂

**文件**：`AppContainer.kt`

**修改方案**：在 `AppContainer` 中补全缺失的 `lazy` 初始化链：

```kotlin
// AppContainer.kt — 新增
val secondEngineManager: SecondEngineManager by lazy {
    SecondEngineManager(application, modelConfigRepository)
}
val memoryExtractLoop: MemoryExtractLoop by lazy {
    MemoryExtractLoop(
        memoryRepository = memoryRepository,
        memoryGraphRepository = memoryGraphRepository,
        promptBuilder = unifiedExtractionPromptBuilder,
        parser = unifiedExtractionParser
    )
}
val t1BatchProcessor: T1BatchProcessor by lazy {
    T1BatchProcessor(memoryRepository, memoryGraphRepository, secondEngineManager)
}
```

然后在 `CompanionChatApplication.kt` 中使用容器内的 `t1BatchProcessor`：
```kotlin
// 改前
t1BatchProcessor = null
// 改后
t1BatchProcessor = appContainer.t1BatchProcessor
```

`ChatViewModel.kt` 中改为引用容器实例：
```kotlin
// 改前
memoryExtractLoop = MemoryExtractLoop(memoryRepository, ...)
// 改后
memoryExtractLoop = container.memoryExtractLoop
```

---

## 🟡 P1 — 代码质量问题

### OPT-004：`Strings.kt` 过大（1338 行），翻译耦合

**文件**：`locale/Strings.kt`

**修改方案**：

**步骤 1**：新建 `locale/strings_zh.json`
```json
{
  "memory_retrieved_title": "从记忆中检索到的与当前对话相关的信息：",
  "memory_user_note": "以下内容均为用户本人的记忆，不代表助手自身。",
  ...
}
```

**步骤 2**：新建 `locale/strings_en.json`（同上结构，英文内容）

**步骤 3**：修改 `locale/Strings.kt` 的 `Strings.get()` 方法
```kotlin
object Strings {
    private val cache = mutableMapOf<AppLanguage, Map<StringsKey, String>>()
    
    fun get(lang: AppLanguage, key: StringsKey): String {
        val map = cache.getOrPut(lang) { loadJson(lang) }
        return map[key] ?: key.name
    }
    
    private fun loadJson(lang: AppLanguage): Map<StringsKey, String> {
        val fileName = when (lang) {
            AppLanguage.ZH -> "strings_zh.json"
            AppLanguage.EN -> "strings_en.json"
        }
        val json = readAsset(fileName)
        return parseJson(json)
    }
}
```

**影响**：删除 `Strings.kt` 中约 1200 行 inline map，保留 `StringsKey` 枚举（~100行）。

---

### OPT-005：`SecondEngineManager` 状态机不清晰

**文件**：`data/preferences/SecondEngineManager.kt`

**修改方案**：引入 Kotlin 密封类做状态建模

```kotlin
// SecondEngineManager.kt — 新增状态类
sealed class EngineState {
    object Idle : EngineState()
    data class Running(val job: Job) : EngineState()
    object Cancelled : EngineState()
}
```

将旧的状态标记（`isRunning`, `currentJob`）替换为 `var state: EngineState`

```kotlin
class SecondEngineManager(...) {
    @Volatile
    private var state: EngineState = EngineState.Idle
    
    suspend fun runSummaryIfAllowed(config: EngineConfig, prompt: String): SummaryRunResult {
        if (state is EngineState.Running) return SummaryRunResult.SkippedAlreadyRunning
        if (state is EngineState.Cancelled) return SummaryRunResult.Cancelled
        
        val job = coroutineScope.launch {
            // ... 原有的 LLM 调用逻辑
        }
        state = EngineState.Running(job)
        // ...
    }
    
    fun cancelRunningSummary() {
        (state as? EngineState.Running)?.job?.cancel()
        state = EngineState.Cancelled
    }
}
```

---

### OPT-006：`PreferenceLearningCoordinator` 节流时间改为 15s 过于激进

**文件**：`companion/PreferenceLearningCoordinator.kt:257-258`

**修改方案**：回调节流时间

```kotlin
// 改前
const val STAGE4_IDLE_DELAY_MILLIS = 15 * 1000L
const val STAGE4_THROTTLE_MILLIS = 15 * 1000L

// 改后
const val STAGE4_IDLE_DELAY_MILLIS = 30 * 1000L     // 30s
const val STAGE4_THROTTLE_MILLIS = 2 * 60 * 1000L    // 2min
```

**说明**：
- `IDLE_DELAY`：用户停止说话 30s 后才触发 LLM 提取
- `THROTTLE_MILLIS`：同一 session 两次 LLM 提取至少间隔 2min

---

### OPT-007：多个 `const val` 硬编码分散在业务类中

**文件**：涉及 5 个文件

**修改方案**：新建 `data/memory/MemoryConfig.kt`

```kotlin
object MemoryConfig {
    // 衰减曲线
    const val INITIAL_STRENGTH = 0.6
    const val DECAY_DAY1 = 0.70
    const val DECAY_DAY2 = 0.80
    const val DECAY_DAY3 = 0.90
    const val DECAY_DAY4_PLUS = 0.90
    const val CLEANUP_THRESHOLD = 0.05
    
    // 强化参数
    const val STRENGTHEN_FTS_HIT = 0.05
    const val STRENGTHEN_LLM_CONFIRM = 0.15
    
    // PPR 检索
    const val PPR_DAMPING_FACTOR = 0.85
    const val PPR_MIN_LINK_WEIGHT = 0.3
    
    // 语义去重
    const val SEMANTIC_DEDUP_THRESHOLD = 0.85f
    
    // Token 预算
    const val DEFAULT_TOKEN_BUDGET = 1200
}
```

**迁移对照表**：

| 原位置 | 原值 | 迁移至 |
|--------|------|--------|
| `MemoryDao.kt:46-55` SQL 内 | 0.70/0.80/0.90 | `DECAY_DAY*` |
| `PprRetriever.kt:142-146` | 0.85/0.3/0.05 | `PPR_*` |
| `MemoryPromptBuilder.kt:46` | 1200 | `DEFAULT_TOKEN_BUDGET` |
| `MemoryRepository.kt:110` | 0.85f | `SEMANTIC_DEDUP_THRESHOLD` |
| `MemoryRepository.kt:47` | 0.6 | `INITIAL_STRENGTH` |

**注意**：`MemoryDao.kt` 的 SQL 中的衰减率仍保持硬编码（SQL 无法引用 Kotlin 常量），其余 Kotlin 层引用改为 `MemoryConfig.XXX`。

---

### OPT-008：`OnnxEmbeddingEngine.embed()` 可能返回 null

**文件**：`data/memory/MemoryRepository.kt:102-108`、`data/embedding/VectorRetriever.kt`

**修改方案**：

`MemoryRepository.deduplicateBySemantics` 中添加 null 检查：
```kotlin
// 改前
val embedding = embeddingEngine(content)
// 改后
val embedding = embeddingEngine(content) ?: return null  // 引擎未就绪，跳过去重
```

`VectorRetriever.kt` 中已有 null 检查，无需改动。

---

## 🟢 P2 — 代码小瑕疵

### OPT-009：项目根目录残留临时脚本文件

**修改方案**：
```bash
# 删除所有开发临时文件
del _fix_*.py _run_build.py _check_errors.py fix_*.py build_check.bat
del CompanionChat\app\src\main\java\com\companion\chat\locale\_edit_strings.py
```

**保留**：`记忆系统改造方案.md`、`记忆系统改造-代码修改清单.md`、`记忆系统改造-Bug报告.md`、`记忆系统改造-逐文件审查报告.md`、`CompanionChat优化建议.md`、`CompanionChat优化实施计划.md`

---

### OPT-010：`ExtractedMemory.linkToEntityIndex` 字段未被使用

**文件**：`data/memory/ExtractedMemory.kt`

```kotlin
// 改前
data class ExtractedMemory(
    ...
    val linkToEntityIndex: Int? = null
)
// 改后
data class ExtractedMemory(
    ...
    // linkToEntityIndex 已移除 — 链接信息通过 ExtractedLink 传递
)
```

检查 `MemoryExtractLoop.kt` 和 `MemoryGraphRepository.kt` 中是否有对 `linkToEntityIndex` 的引用。如果有，一并移除。

---

### OPT-011：`MemoryExtractLoop` 注释多余

**文件**：`data/memory/MemoryExtractLoop.kt:31-33`

```kotlin
// 改前
// 1. 直接解析 LLM 原始输出
// 2. LLM 调用：prompt 由调用方传入（解析由 parseFull 完成）
// MemoryExtractLoop 现在只负责解析和存储

// 改后
// 解析 LLM 原始输出 → 去重 → 批量写入（不负责 LLM 调用本身）
```

---

### OPT-012：`T1BatchProcessor` TODO 注释

**文件**：`data/memory/T1BatchProcessor.kt:18-21`

```kotlin
// 改前
// TODO: 后续迭代实现以下功能
// 1. 遍历 contradictions 链接，生成冲突报告
// 2. 从 Fact 记忆归纳 Agent Experience
// 3. 轻量 Meta-Memory 提炼

// 改后
// TODO(后续迭代): 遍历 contradictions → 生成冲突报告；
// 从 Fact 记忆归纳 Agent Experience；轻量 Meta-Memory 提炼
```

---

## 汇总修改量

| 优先级 | 问题数 | 预计总工时 | 文件变更数 |
|--------|--------|-----------|-----------|
| 🔴 P0 | 3 | 4h | 7 |
| 🟡 P1 | 5 | 5h | 10 |
| 🟢 P2 | 4 | 10min | 5 |
| **总计** | **12** | **~9h** | **~22** |
