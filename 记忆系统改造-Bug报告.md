# 记忆系统改造 — Bug & 问题报告

> 生成日期：2026-06-25
> 覆盖范围：改造新增/修改的全部 36 个文件

---

## P0 — 功能损坏

### BUG-001：T1BatchProcessor 是空壳，T+1 批量处理完全失效

**文件：** `T1BatchProcessor.kt:21-36`

```kotlin
suspend fun processT1Batch(): T1BatchResult {
    val result = T1BatchResult()
    // 1. 收集 contradictions 链接
    val contradictions = memoryGraphRepository.getNeighbors(0) // ← 写死 ID=0，无意义
    // result.contradictionCount = 0  // ← val 无法赋值，被注释
    // 2. 清理弱记忆
    val cleaned = memoryRepository.cleanupWeakMemories(0.05)
    // result.cleanedCount = cleaned  // ← val 无法赋值，被注释
    // 3. 获取元记忆（仅注释，无代码）
    return result  // ← 返回全 0 空结果
}
```

**根本原因**：`T1BatchResult` 的 4 个字段全部声明为 `val`（不可变），导致所有赋值操作被注释。`getNeighbors(0)` 写入 0 作为记忆 ID 没有意义（auto-generate 的 id 从 1 开始）。

**影响**：每日 T+1 批处理完全空转，Agent Experience 永远不会生成，Meta-Memory 永远不会提炼。

**修复**：`T1BatchResult` 的字段改为 `var`，并实现真实的 contradictions 遍历逻辑。

---

### BUG-002：`applyEnrichedLinks()` 是空方法，链接 enrichment 未实现

**文件：** `UnifiedExtractionParser.kt:292-297`

```kotlin
/** 将 links 中的 toEntityIdx 丰富为 entityName。 */
fun applyEnrichedLinks(): UnifiedExtractionResultFull {
    return this  // ← 什么都不做
}
```

**影响**：`parseFull()` 末尾调用了此方法，但 enrichment 逻辑从未实现。下游消费者拿到的 `links` 只包含 `fromMemoryIdx`/`toEntityIdx` 整数索引，没有实际的实体名称，导致链接不可读。

---

### BUG-003：PPR 检索不执行实体匹配，种子获取不完整

**文件：** `PprRetriever.kt:117-120`

```kotlin
private fun extractKeywords(userMessage: String): List<String> {
    val normalized = userMessage.lowercase()
        .replace(PUNCTUATION_REGEX, " ").replace(WHITESPACE_REGEX, " ").trim()
    return normalized.split(WHITESPACE_REGEX)
        .filter { it.length >= 2 && it !in STOP_WORDS }.distinct()
}
```

**方案要求**：Phase 1 应从用户消息中提取实体名 + 匹配 `memory_entities` 表
**实际实现**：仅做纯正则分词，无实体匹配

**影响**：PPR 种子的质量取决于分词与 FTS 的配合。未做实体匹配意味着：
- 种子可能遗漏关键实体（如"张三"被分词为"张"和"三"两个单字，filter 后丢弃）
- 无法利用实体链接进行桥接发现
- 多种子桥接的效果大打折扣

**修复**：在 `extractKeywords` 后增加 `memoryEntityDao.findAll()` 的实体名称模糊匹配。

---

## P1 — 逻辑缺陷

### BUG-004：`updateMemoryIndexIfNeeded` 是空函数且浪费 I/O

**文件：** `CompanionRuntime.kt:149-157`

```kotlin
private suspend fun updateMemoryIndexIfNeeded(repository: MemoryRepository, roleCardId: Long?) {
    val allMemories = if (roleCardId != null) {
        repository.getAllMemories().filter { it.roleCardId == roleCardId || it.roleCardId == null }
    } else {
        repository.getAllMemories()  // ← 全量查询但结果丢弃
    }
    // updateIndex removed - use FTS/PPR retrieval instead  // ← 纯注释
}
```

**问题**：
1. 函数体全量查询记忆后不做事，浪费数据库 I/O
2. 实际上已无人调用此函数（`buildMemoryContext` 中调用已被移除），是死代码

**影响**：无运行时影响（因为已无人调用），但代码残留造成维护困惑。

**修复**：直接删除此函数。

---

### BUG-005：衰减 SQL 不更新 `lastAccessedAt`，影响下次衰减判断

**文件：** `MemoryDao.kt:46-55`

```sql
UPDATE memories SET strength = CASE ... END, 
    updatedAt = :now, lastAccessedAt = lastAccessedAt  -- ← 自身赋值，不变
WHERE id = :id AND strength > 0.05
```

**问题**：`lastAccessedAt = lastAccessedAt` 是不变的自身赋值。这意味着 `lastAccessedAt` 只在检索命中时（通过 `strengthen`）更新，但衰减发生时它不更新。下次衰减调度时，`idleDays` 基于 `lastAccessedAt` 计算，会导致：

假设一条记忆在 Day 0 创建后从未被检索：
- Day 1 衰减：idleDays=(1-0)=1 → ×0.70 ✅
- Day 2 衰减：idleDays 仍=1（因为 lastAccessedAt 没更新）→ 再次 ×0.70 ❌（应该是 ×0.80）

**影响**：连续多天未提及的记忆会重复应用第一天的衰减率（×0.70），而不是曲线上的逐步衰减。

**修复**：`applyDecayByAge` 执行后应将 `lastAccessedAt` 更新为 `now`，或由调用方在遍历循环中维护。

---

### BUG-006：`strengthen` 同时更新 `lastAccessedAt` 和 `updatedAt`，混淆最后访问时间

**文件：** `MemoryDao.kt:59-64`

```sql
UPDATE memories
SET strength = MIN(1.0, strength + :delta), 
    lastAccessedAt = :now, updatedAt = :now
WHERE id = :id
```

**问题**：`strengthen` 同时更新 `lastAccessedAt` 和 `updatedAt` 为同一时间。但语义上：
- `lastAccessedAt` = 最后被检索/提及的时间（用于计算衰退 idleDays）
- `updatedAt` = 最后编辑时间（用于排序）

当 PPR 检索命中一条记忆时，`lastAccessedAt` 更新是正确的（因为它被提及了），但 `updatedAt` 也会被更新，导致它在排序时"跳"到前面，给用户一种"这条记忆最近被编辑过"的错觉。

**影响**：记忆列表排序可能不准确，用户看到的是"被检索过的"而非"被编辑过的"记忆。

**修复**：检索强化只更新 `lastAccessedAt`，不更新 `updatedAt`。

---

### BUG-007：PPR 评分中 `base_score` 可能为负数

**文件：** `PprRetriever.kt:54`

```kotlin
val seeds = ftsResults.mapIndexed { index, memory ->
    SeedEntry(memory.id, baseScore = 1.0 - (index.toDouble() / ftsResults.size * 0.5))
}
```

**问题**：当 `ftsResults.size > 2` 时，最后一个元素的 `baseScore = 1.0 - (9/10*0.5) = 0.55`，还在正数范围。但如果 topK 很大（如 20），则最后一个 `baseScore = 1.0 - (19/20*0.5) = 0.525`。虽然理论上不会负，但写法不安全，且 score 差异不够大（排名第1 的 1.0 vs 排名最后的 0.5）。

**影响**：PPR 传播时，低排名种子的贡献仍然很大（0.5 vs 1.0 仅差 2 倍），理想应该是指数衰减。

---

## P2 — 代码质量问题

### BUG-008：MemoryExtractLoop 的 `messages` 参数未使用

**文件：** `MemoryExtractLoop.kt:28-29`

```kotlin
suspend fun execute(
    messages: List<ChatMessage>,    // ← 未使用
    llmRawOutput: String,
```

**问题**：`messages` 参数传入后从未在函数体内被使用。调用方需要传一个无意义的参数。

**影响**：接口设计冗余，调用方需构造空列表或传实际对话数据造成困惑。

**修复**：移除 `messages` 参数。

---

### BUG-009：`PprRetriever.ScoredMemory` 作用域限制导致外部无法引用

**文件：** `PprRetriever.kt:95-101`

```kotlin
data class ScoredMemory(
    val memory: Memory,
    val score: Double,
    val ftsScore: Double,
    val pprScore: Double,
    val recencyScore: Double,
    val entityBoost: Double
)
```

**问题**：`ScoredMemory` 定义在 `PprRetriever` 类内部，外部调用者想使用返回的 `ScoredMemory` 需要完整路径 `PprRetriever.ScoredMemory`。虽然能编译，但跨模块使用时不够直观。

**影响**：使用不便，非结构性问题。

---

### BUG-010：FTS4 关键词注入风险

**文件：** `PprRetriever.kt:119`, `MemoryRetriever.kt:54`

```kotlin
private fun escapeFtsTerm(term: String): String = term.replace("\"", "")
```

**问题**：FTS4 转义只去除了双引号，但没有处理 FTS4 的特殊语法字符如 `*`, `^`, `NEAR`, `NOT`, `OR`, `AND`。如果用户输入包含 `NOT` 或 `OR` 等 FTS4 运算符，会改变查询语义。

**示例**：用户输入"我喜欢 NOT 讨厌苹果" → FTS 表达式变成 `"我喜欢" "NOT" "讨厌" "苹果"`，FTS4 会将 NOT 解释为逻辑非运算符。

**影响**：极端情况下 FTS 查询结果可能完全偏离预期。

**修复**：对关键词进行 FTS4 安全转义，或使用 `MATCH '...'` 的精确短语模式。

---

### BUG-011：`MemoryDecayManager` 和 `MemoryRepository` 存在重复的衰减逻辑

**文件：** `MemoryDecayManager.kt:23-34` vs `MemoryRepository.kt:70-82`

两个类实现了几乎相同的 `applyDailyDecay()` 方法——都遍历 `getActiveMemories(0.05)` 然后按 `idleDays` 调用 `memoryDao.applyDecayByAge`。这造成：
1. 如果两个类都被调用，衰减会执行两次
2. 调用方不清楚该用哪一个

**修复**：统一为一个入口，建议保留 `MemoryDecayManager` 并从 `MemoryRepository` 中移除重复的实现。

---

### BUG-012：Token 估算不准确（中文文本）

**文件：** `MemoryPromptBuilder.kt:144-146`

```kotlin
private fun estimateTokens(text: String): Int {
    return (text.length / 4) + 1
}
```

**问题**：中文文本每个字约 1-2 tokens（具体取决于模型），英文每 4 字符约 1 token。`length / 4` 对中文文本严重低估（实际应为 `/2`），导致 token 预算控制失效。

**影响**：`L1 概览` 的实际注入量可能超出 `tokenBudget` 的 2 倍以上，导致 prompt 超长。

**修复**：使用 `text.toByteArray(Charsets.UTF_8).size / 4` 或对不同语言分别估算。

---

## 汇总

| ID | 严重度 | 类型 | 文件 | 简述 |
|----|--------|------|------|------|
| BUG-001 | 🔴 P0 | 功能失效 | T1BatchProcessor.kt | T+1 空壳，全 `val` 导致赋值被注释 |
| BUG-002 | 🔴 P0 | 功能缺失 | UnifiedExtractionParser.kt | `applyEnrichedLinks()` 空实现 |
| BUG-003 | 🟡 P1 | 功能不全 | PprRetriever.kt | 缺少实体匹配，种子质量低 |
| BUG-004 | 🟡 P1 | 死代码 | CompanionRuntime.kt | `updateMemoryIndexIfNeeded` 空函数 |
| BUG-005 | 🟡 P1 | 逻辑错误 | MemoryDao.kt | 衰减不更新 `lastAccessedAt`，曲线失效 |
| BUG-006 | 🟡 P1 | 语义混淆 | MemoryDao.kt | `strengthen` 误更新 `updatedAt` |
| BUG-007 | 🟢 P2 | 代码风格 | PprRetriever.kt | `baseScore` 计算不安全 |
| BUG-008 | 🟢 P2 | 接口冗余 | MemoryExtractLoop.kt | `messages` 参数未使用 |
| BUG-009 | 🟢 P2 | 使用不便 | PprRetriever.kt | `ScoredMemory` 内部类暴露 |
| BUG-010 | 🟢 P2 | 安全隐患 | PprRetriever.kt, MemoryRetriever.kt | FTS4 关键词注入风险 |
| BUG-011 | 🟢 P2 | 逻辑重复 | MemoryDecayManager + MemoryRepository | 两个类重复的衰减逻辑 |
| BUG-012 | 🟢 P2 | 估算偏差 | MemoryPromptBuilder.kt | Token 预算对中文低估 2 倍 |
