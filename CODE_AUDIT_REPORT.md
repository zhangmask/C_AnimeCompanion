# Anime Companion 代码审计报告

> 审计时间：2026-06-21  
> 审计范围：`CompanionChat/app/src/main/java/` 全部 Kotlin 源码  
> 审计目标：逻辑 Bug、伪装/占位实现、README 与代码不符

---

## 一、逻辑 Bug

### 1.1 🔴 四阶段偏好学习实际只有一阶段运行（阶段互斥）

**位置**：`ChatViewModel.kt:650`

```kotlin
if (!contextConfigRepository.getAutoPreferenceLearningEnabled()) {
    storeRuleBasedMemoriesForMessage(userMessage)  // 阶段一
}
generateResponse(userMessage.content.trim())
// 阶段四在 finishStreaming() 中通过 schedulePreferenceSummaryAfterDelay() 调度
```

**问题**：当自动偏好学习开启（默认开启）时，阶段一（规则即时提取）**完全不执行**；关闭时，阶段四（LLM 异步提取）**完全不执行**。README 声称的"四阶段偏好学习：规则即时提取 → LLM 后台异步提取 → 置信度合并 → 已确认偏好注入对话"暗示四个阶段**依次全部运行**，但实际阶段一和阶段四是**互斥**的。

**影响**：开启默认设置时，用户说"我叫小明"不会立即被规则提取记住，必须等阶段四后台 LLM 总结才可能提取——延迟 3 分钟以上，且 LLM 提取可能失败。

**建议**：阶段一应始终执行（即时、零成本），阶段四作为补充在后台异步运行。

---

### 1.2 🔴 RuleBasedMemoryExtractor：splitToClauses 切分后正则全句匹配失效

**位置**：`RuleBasedMemoryExtractor.kt:5-13`

```kotlin
override fun extract(userMessage: String, sessionId: String): List<ExtractedMemory> {
    return splitToClauses(normalizedMessage)   // 先按逗号切分
        .flatMap { clause -> extractFromClause(clause) }  // 再对每个子句做正则
}
```

**问题**：`splitToClauses` 按标点切分后，每个子句传给 `extractFromClause`，但内部正则全部使用 `^...$` 全句匹配。例如：

- 用户输入 `"其实我也挺喜欢打篮球的"` → 切分后 `"其实我挺喜欢打篮球的"` → `normalizeClause` 只去掉"也"前缀 → 得到 `"其实我挺喜欢打篮球的"` → **无法匹配任何正则** ❌
- 大量自然中文表达因为前缀/后缀修饰词未被归一化而无法提取

---

### 1.3 🔴 MemoryRetriever：每次检索无条件递增所有返回记忆的引用计数

**位置**：`MemoryRetriever.kt:40`

```kotlin
results.forEach { memoryDao.incrementReference(it.id) }
```

**问题**：每次 `retrieveRelevantMemories` 被调用，返回的**所有**记忆的 `referenceCount` 都 +1，无论相关性高低。导致"马太效应"——热门记忆越来越热，新记忆永远排不上。没有衰减机制，没有上限，没有基于相关性的加权。

---

### 1.4 🟡 MemoryRetriever：FTS 查询构造脆弱 + 全量内存兜底

**位置**：`MemoryRetriever.kt:17-31`

```kotlin
val fallbackMatches = memoryDao.getAll().filter { memory ->   // ← 全量加载！
    keywords.any { keyword -> content.contains(keyword) }
}
```

**问题**：
1. `escapeSqlLiteral` 只转义单引号，FTS4 有自己的查询语法（`*`, `+`, `-`, `AND`, `OR`, `NOT`），中文关键词可能触发异常
2. 兜底逻辑 `memoryDao.getAll()` 每次查询都**全量加载所有记忆到内存**，O(n) 复杂度
3. FTS 结果和兜底结果直接合并去重，没有分数融合

---

### 1.5 🟡 CompanionRuntime.runTurn：每轮都重建 Conversation

**位置**：`CompanionRuntime.kt:265-287`

**问题**：虽然 `rebuildConversationWithContext` 内部有 `shouldCompress` 检查会提前返回 `skipped()`，但每次仍会构建 `stableMessages`、检查 `shouldInjectContext`。如果有记忆上下文（几乎总是有），就会调用 `buildContext` → `engine.rebuildConversation` → `engine.replayMessages`。**几乎每一轮对话都会销毁并重建底层 Conversation 对象**。

---

### 1.6 🟡 LiteRTLMInferenceEngine.sendMessageStream：忽略消息列表，只取最后一条用户消息

**位置**：`LiteRTLMInferenceEngine.kt:389-394`

**问题**：方法签名接收 `messages: List<ChatMessage>`，但实际只取最后一条用户消息发送。历史消息依赖 Conversation 内部状态。如果 Conversation 状态与 UI 的 messages 列表不同步（例如重建失败后降级），会导致上下文丢失。

---

### 1.7 🟡 RuleBasedSummaryGenerator：每条消息截断到 48 字符，信息损失严重

**位置**：`RuleBasedSummaryGenerator.kt:49` — `MAX_MESSAGE_CHARS = 48`

**问题**：48 个字符对中文约 1-2 句话。角色扮演、情感倾诉等长消息场景，48 字符截断丢失大量上下文，压缩摘要质量极低。

---

### 1.8 🟡 RoleAwareVoiceOutputEngine.waitForPlaybackComplete：忙等待轮询

**位置**：`RoleAwareVoiceOutputEngine.kt:181-187`

```kotlin
while (localAudioPlaybackEngine?.state?.value is VoiceOutputState.Speaking) {
    kotlinx.coroutines.delay(100)  // 100ms 轮询
}
```

**问题**：100ms 轮询导致语音段落间明显停顿；Error 状态会跳过后续段落；应改用 Flow collectLatest。

---

### 1.9 🟡 MemoryRepository.storeExtractedMemories：去重只检查精确匹配

**位置**：`MemoryRepository.kt:140`

**问题**：只检查 `category + content` 精确匹配。规则提取器和 LLM 提取器可能产生语义相同但文字略有不同的记忆（如"用户喜欢篮球" vs "用户喜欢打篮球"），存为两条独立记忆造成冗余。

---

## 二、伪装实现 / 占位实现

### 2.1 🔴 RuleBasedSummaryGenerator 不是真正的"摘要"

**位置**：`RuleBasedSummaryGenerator.kt`

**现状**：所谓"摘要"只是把每条消息截断到 48 字符后用分号连接：
```
用户：今天天气不错；助手：是啊阳光很好；用户：我喜欢这样的天气
```

**README 声称**："更早的历史会被压缩重建，只保留当前真正需要的部分"

**实际**：没有任何"压缩"或"重建"逻辑，只是粗暴截断拼接。`LlmSummaryGenerator` 存在且是真正的 LLM 摘要，但默认使用 `RuleBasedSummaryGenerator`。

---

### 2.2 🔴 OnnxEmbeddingEngine：模型文件可能不存在，静默失效

**位置**：`OnnxEmbeddingEngine.kt:162-163`

```kotlin
const val DEFAULT_MODEL_PATH = "embedding/model.onnx"
const val DEFAULT_VOCAB_PATH = "embedding/vocab.txt"
```

**现状**：引擎尝试从 `assets` 目录加载嵌入模型和词表，但项目中没有证据表明这些文件存在。初始化失败时 `isInitialized = false`，所有向量检索静默返回空结果。用户不会收到任何错误提示，记忆检索功能形同虚设。

---

### 2.3 🔴 VectorRetriever：纯内存索引，进程死亡即丢失

**位置**：`VectorRetriever.kt:14` — `private val embeddingIndex = mutableMapOf<Long, FloatArray>()`

**现状**：嵌入向量索引完全存储在内存中，没有持久化。App 进程被系统杀死后所有嵌入向量丢失，下次启动必须重新计算。

---

### 2.4 🟡 MemoryLifecycleManager：只在启动时运行一次

**位置**：`MemoryLifecycleManager.kt` + `CompanionChatApplication.kt:31`

**现状**：`runStartupMaintenance()` 只在 Application 创建时调用一次。App 长时间运行期间：过期短期记忆不会被清理、新达到晋升条件的短期记忆不会被晋升、没有长期记忆的衰减/淘汰机制。

---

### 2.5 🟡 DiscoverRoleRepository：发现页数据是硬编码种子

**位置**：`DiscoverRoleRepository.kt:11` → `DiscoverRoleSeeds.roles`

**现状**：发现页的角色列表来自硬编码数据，没有网络获取、没有社区角色市场。

**README 声称**："创作者可以以 $6/月发布自己的角色卡片到社区" ——社区角色市场完全不存在。

---

### 2.6 🟡 NoOpSummaryGenerator：存在但从未使用

**位置**：`NoOpSummaryGenerator.kt` — 总是返回空字符串，等效于丢弃所有历史。代码中从未被引用，属于死代码。

---

## 三、README 与代码不符

### 3.1 🔴 "临时对话分支" / "任务打断隔离" —— 完全未实现

**README 原文**：
> 支持临时对话分支。当你需要在聊天中插入一个简短的问题（比如问路），系统会内部自动分出临时对话，不会打断主对话的上下文。

**核心技术创新**：
> 任务打断隔离：临时对话分支不打断主陪伴线索，完成后自动恢复原始上下文

**代码现状**：搜索整个代码库，不存在任何临时对话分支、对话分支、任务打断相关的实现。

---

### 3.2 🔴 "推理绑定前台服务防杀" —— 完全未实现

**README 原文**：
> 安卓深度适配：推理绑定前台服务防杀，协程调度安全挂起，arm64-v8a Native 优化，触发器同步

**代码现状**：不存在任何 `ForegroundService`。推理引擎在普通协程上下文中运行，Android 系统可以在后台时杀死进程。

---

### 3.3 🔴 "InferenceEngineFactory 根据设备能力和用户配置自动选择最优运行时" —— 无自动选择

**README 原文**：
> 双运行时推理：高端设备用 llama.cpp 追求吞吐，主流设备用 LiteRT-LM 优先流畅和散热

**代码现状**：`InferenceEngineFactory.create(runtime)` 只是简单 `when` 分支，根据传入参数创建对应引擎。**没有任何设备能力检测、性能基准测试或自动选择逻辑**。用户必须手动选择。

---

### 3.4 🔴 "语义归一化" —— 未实现

**README 原文**：
> 分层记忆系统：FTS4 全文检索 + 语义归一化 + 规则/LLM 双通道提取 + 引用次数自动提升

**代码现状**：FTS4 全文检索存在，但"语义归一化"完全不存在。没有同义词映射、没有概念归一化、没有语义消歧。

---

### 3.5 🟡 "完全离线" —— 存在云端 ASR 和 HTTP 图片生成路径

**README 原文**：
> 其他 asr、tts、生图模型等组件都在本地运行，不依赖云端服务。

**代码现状**：`CloudHttpAsrEngine` + `HttpImageGenerationEngine` 提供云端选项。虽然默认是本地，但用户配置后就会联网，与"不依赖云端服务"的绝对表述矛盾。

---

### 3.6 🟡 "本地图片生成" —— README 只提 Stable Diffusion，未提 DreamLite

**README 原文**：
> 基于 Stable Diffusion 的本地推理

**代码现状**：实际支持 `StableDiffusionNative` 和 `DreamLiteNative` 两种本地引擎，默认使用 DreamLite。README 完全未提及 DreamLite。

---

### 3.7 🟡 压缩阈值配置不合理

**位置**：`ContextSettings.kt:9-10`

```kotlin
val compressionThreshold: Int
    get() = retainedRounds * 2 + compressionBuffer  // 默认 30*2+10 = 70
```

**问题**：需要 70 条消息才触发压缩，压缩后保留最近 60 条（30轮×2），只有 10 条会被压缩成摘要。压缩几乎不生效，且摘要信息量极低。

---

### 3.8 🟡 "引用次数自动提升" —— 实现存在但行为有缺陷

晋升条件 `referenceCount >= 3` 过低，几乎所有被检索 3 次的短期记忆都会晋升为长期记忆，无衰减无上限。

---

## 四、其他值得关注的问题

### 4.1 🟡 LlmSummaryGenerator 用主引擎做摘要，可能污染对话上下文

**位置**：`LlmSummaryGenerator.kt:33` — 直接调用主推理引擎的 `sendMessageStream`，会在当前 Conversation 上追加一条摘要请求消息。而 `SecondEngineManager`（阶段四使用）会创建独立引擎实例。两者路径不一致，LlmSummaryGenerator 的使用场景不明确。

---

### 4.2 🟡 ChatViewModel 日志写入诊断状态，无上限增长

**位置**：`ChatViewModel.kt:232`

```kotlin
_uiState.update { it.copy(diagnosticLog = it.diagnosticLog + line + "\n") }
```

**问题**：`diagnosticLog` 字符串无限追加到 UI 状态中，长时间使用后可能占用大量内存，影响 Compose 重组性能。

---

### 4.3 🟡 UserPreference 缺少 roleCardId 字段

**位置**：`UserPreference.kt`

**问题**：`Memory` 有 `roleCardId` 字段支持角色隔离，但 `UserPreference` 没有。所有角色的偏好共享，无法实现"不同角色记住用户不同偏好"的能力。

---

### 4.4 🟡 CompanionDatabase FTS 表只在 onCreate 创建

**位置**：`CompanionDatabase.kt:159`

**问题**：`createMemoryFtsTables` 只在 `onCreate` 回调中执行。对于从旧版本升级的用户（通过 Migration），FTS 表和触发器不会被创建，导致 FTS 检索完全失效。需要在 Migration 中也创建 FTS 表。

---

## 五、问题汇总

| 级别 | 类型 | 数量 | 关键问题 |
|------|------|------|----------|
| 🔴 严重 | 逻辑 Bug | 3 | 阶段互斥、正则失效、引用计数马太效应 |
| 🔴 严重 | 伪装实现 | 3 | 假摘要、嵌入静默失效、向量索引不持久 |
| 🔴 严重 | README 不符 | 4 | 临时对话未实现、前台服务未实现、无自动选择、无语义归一化 |
| 🟡 中等 | 逻辑 Bug | 6 | FTS脆弱、每轮重建、消息截断、忙等待、精确去重、忽略消息列表 |
| 🟡 中等 | 伪装实现 | 3 | 生命周期只跑一次、硬编码发现页、死代码 |
| 🟡 中等 | README 不符 | 4 | 非完全离线、未提DreamLite、压缩阈值、引用计数缺陷 |
| 🟡 中等 | 其他 | 4 | 摘要污染上下文、日志无上限、偏好无角色隔离、FTS升级缺失 |

**总计 23 个问题，其中 10 个严重级别。**

---

## 六、优先修复建议

1. **修复阶段互斥**：让 `storeRuleBasedMemoriesForMessage` 始终执行，不受 `autoPreferenceLearningEnabled` 开关影响
2. **实现临时对话分支**：或从 README 中移除该声明
3. **添加 ForegroundService**：或从 README 中移除"推理绑定前台服务防杀"声明
4. **修复 MemoryRetriever 引用计数**：只对相关性高于阈值的记忆递增，添加衰减机制
5. **修复 FTS Migration**：在 MIGRATION_1_2/2_3/3_4 中创建 FTS 表和触发器
6. **验证嵌入模型文件存在**：不存在时在 UI 显示警告
7. **降低压缩阈值或调整 retainedRounds 默认值**：让压缩机制实际生效
8. **从 README 中移除未实现功能声明**：语义归一化、自动运行时选择、社区角色市场
