# CompanionChat 优化实施计划

> 对应：`CompanionChat优化建议.md`（完整版，含详细修改方案）
> 优先级：🔴 P0 → 🟡 P1 → 🟢 P2

---

## Phase 1 — 稳定性修复（P0）

### 1.1 静默吞异常修复

**对应 OPT-002**，修改方案详见 `CompanionChat优化建议.md`

| 文件 | 改前 | 改后 | 工作量 |
|------|------|------|--------|
| `CompanionChatApplication.kt:35` | `onFailure{logToFile(...)}` | 追加 `Log.e(...)` + 完整 stack trace | 小（1 行） |
| `MemoryLifecycleManager.kt:49` | `runCatching { runDailyDecay() }` | `try { ... } catch(e) { Log.e(...) }` | 小（3 行） |
| `MemoryViewModel.kt:58,80-81,105-106` | `catch(_: Exception) {}` | `catch(e: Exception) { Log.e(...) }` | 小（每处 1 行） |
| `ChatViewModel.kt` 多处 `runCatching` | 静默 | 追加 `onFailure{Log.e(...)}` | 中（5-8 处） |

**总工作量**：~1h，4 文件

### 1.2 节流时间回退

**对应 OPT-006**

```kotlin
// PreferenceLearningCoordinator.kt:257-258
const val STAGE4_IDLE_DELAY_MILLIS = 30 * 1000L    // 15s → 30s
const val STAGE4_THROTTLE_MILLIS = 2 * 60 * 1000L   // 15s → 2min
```

**总工作量**：5min，1 文件，2 行

### 1.3 提取 `MemoryConfig.kt`

**对应 OPT-007**，修改方案详见 `CompanionChat优化建议.md`

**新建** `data/memory/MemoryConfig.kt`（包含 13 个常量）

**迁移对照**：

| 源文件 | 迁移内容 | 迁移方式 |
|--------|---------|---------|
| `MemoryDao.kt` SQL 内衰减率 | 0.70/0.80/0.90 | SQL 中保持硬编码（不可引用 Kotlin const），Kotlin 调用方改为 `MemoryConfig.XXX` |
| `MemoryRepository.kt:47` | `strength = 0.6` | → `MemoryConfig.INITIAL_STRENGTH` |
| `MemoryRepository.kt:110` | `threshold = 0.85f` | → `MemoryConfig.SEMANTIC_DEDUP_THRESHOLD` |
| `PprRetriever.kt:142-146` | 0.85/0.3/0.05 | → `MemoryConfig.PPR_*` |
| `MemoryPromptBuilder.kt:46` | `tokenBudget = 1200` | → `MemoryConfig.DEFAULT_TOKEN_BUDGET` |
| `MemoryDecayManager.kt:52` | `DAY_MILLIS` | **保持不动**（纯时间常量） |

**总工作量**：1h，1 新建 + 4 修改

---

## Phase 2 — 代码清理（P2）

### 2.1 清理临时脚本（OPT-009）

```bash
# 删除开发临时文件（25+ 个）
del _fix_*.py _run_build.py _check_errors.py fix_*.py build_check.bat _add_import.py _fix_and_build.py
del "CompanionChat\app\src\main\java\com\companion\chat\locale\_edit_strings.py"
```

**保留**：所有 `.md` 方案文档和 `其他githubdemo` 研究目录。

**总工作量**：5min

### 2.2 清理冗余字段（OPT-010）

```kotlin
// ExtractedMemory.kt — 删除 linkToEntityIndex
data class ExtractedMemory(
    val content: String,
    val category: String,
    val source: String,
    val entityName: String? = null
    // linkToEntityIndex 已移除 — 通过 ExtractedLink 传递
)
```

**检查** `MemoryExtractLoop.kt` 中是否还有对 `linkToEntityIndex` 的引用。
**总工作量**：5min，1 文件

### 2.3 清理冗余注释（OPT-011 + OPT-012）

```kotlin
// MemoryExtractLoop.kt:31-33 — 三段注释合并为一段
// 改前：
// 1. 直接解析 LLM 原始输出
// 2. LLM 调用：prompt 由调用方传入
// MemoryExtractLoop 现在只负责解析和存储
// 改后：
// 解析 LLM 原始输出 → 去重 → 批量写入

// T1BatchProcessor.kt:18-21 — TODO 精简
// 改前：3 行 TODO
// 改后：1 行 TODO(后续迭代)
```

**总工作量**：5min，2 文件

---

## Phase 3 — 架构优化（P0 + P1）

### 3.1 DI 完善（OPT-003）

**文件**：`AppContainer.kt` + `CompanionChatApplication.kt` + `ChatViewModel.kt`

**步骤**：
1. `AppContainer.kt` 中补全 `secondEngineManager`、`memoryExtractLoop`、`t1BatchProcessor` 的 `lazy` 初始化
2. `CompanionChatApplication.kt` 使用 `appContainer.t1BatchProcessor` 替代 `null`
3. `ChatViewModel.kt` 使用 `container.memoryExtractLoop` 替代内联构造

详细代码见 `CompanionChat优化建议.md` OPT-003 节。

**总工作量**：1h，3 文件

### 3.2 `ChatViewModel` 拆分（OPT-001）

**阶段 1（小改）**：抽出 `VoiceViewModel`（1h）
**阶段 2（中改）**：抽出记忆/偏好（2h）
**阶段 3（大改）**：UseCase 模式（3h）

**建议**：仅做阶段 1，后续按需推进。

### 3.3 翻译文件拆分（OPT-004）

**文件**：`locale/Strings.kt`

**步骤**：
1. 新建 `locale/strings_zh.json`
2. 新建 `locale/strings_en.json`
3. 修改 `Strings.get()` 从 JSON 加载（保留 `StringsKey` 枚举）

详细代码见 `CompanionChat优化建议.md` OPT-004 节。

**总工作量**：3h，3 文件

### 3.4 状态机重构 + embedding null 安全（OPT-005 + OPT-008）

**OPT-005**（SecondEngineManager 状态机）：用 `sealed class EngineState` 替换旧标记变量
**OPT-008**（embedding null 安全）：`deduplicateBySemantics` 中加 `?: return null`

详情见 `CompanionChat优化建议.md` 对应章节。

**总工作量**：2h，2 文件

---

## 实施路线图

| 时间 | 内容 | 涉及文件 | 工时 |
|------|------|---------|------|
| **第1天上午** | 🔴 1.1 静默吞异常修复 | 4 文件 | 1h |
| | 🔴 1.2 节流时间回退 | 1 文件 | 5min |
| | 🟢 2.1 清理临时脚本 | 25+ 文件删 | 5min |
| **第1天下午** | 🟡 1.3 提取 MemoryConfig | 1 新建 + 4 修改 | 1h |
| | 🟢 2.2 + 2.3 清理冗余 | 3 文件 | 15min |
| **第2天** | 🔴 3.1 DI 完善 | 3 文件 | 1h |
| | 🔴 3.2 ChatViewModel 拆分阶段1 | 2 文件 | 1h |
| **第3-4天** | 🟡 3.3 翻译拆分 | 3 文件 | 3h |
| | 🟡 3.4 状态机 + null安全 | 2 文件 | 2h |

**总计**：~18 项修改，约 22 文件，~10 工时

---

## 依赖关系

```
Phase 1.1 ──┐
Phase 1.2 ──┤
Phase 1.3 ──┤── 互不依赖，可并行执行
Phase 2.x ──┘

Phase 3.1 ←── 独立（不依赖其他 Phase）
Phase 3.2 ←── 独立
Phase 3.3 ←── 独立
Phase 3.4 ←── 独立
```

**所有 Phase 互不阻塞**，可按任意顺序实施。建议第1天集中处理 P0 + P2（快速见效），第2天起处理 P1。
