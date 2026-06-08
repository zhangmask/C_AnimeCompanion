# CompanionChat 核心机制细化设计

> 本文档定义 CompanionChat 要实现的 4 个核心机制的详细设计方案。
> 基于当前项目现状（LiteRT-LM 单模型推理、Compose UI、无 Room 数据库）。

---

## 现状问题清单

在设计之前，先列出当前代码中的关键问题：

| 问题 | 位置 | 影响 |
|------|------|------|
| 会话用 JSON 文件存储 | `ChatViewModel.kt:518` saveSessions() | 无法搜索、全量读写、并发不安全 |
| 消息只发最后一条 | `LiteRTLMInferenceEngine.kt:279` conv.sendMessageAsync(lastUserMessage.content) | 依赖 Conversation 内部历史，无法主动控制上下文 |
| 无上下文窗口控制 | 全局 | 长对话会超出模型上下文限制导致截断或崩溃 |
| 记忆页是空壳 | `MemoryScreen.kt` | "即将上线"占位符 |
| 设置页是空壳 | `SettingsScreen.kt` | 所有 onClick 为空 |

---

## 一、自进化闭环：用户偏好自动总结

### 1.1 设计目标

模型在对话过程中自动提取用户偏好（称呼、语言风格、兴趣领域等），存入记忆系统，下次对话时注入 system prompt，使回答更贴合用户。

### 1.2 架构设计

```
┌─────────────────────────────────────────────────────┐
│                    主推理引擎                          │
│           Engine-A (Gemma-4, 正常对话)                │
│                      ▲                               │
│                      │ 偏好注入                       │
│              System Prompt 构建器                     │
│                      ▲                               │
│                      │ 读取                          │
│              用户偏好表 (Room)                         │
│                      ▲                               │
│                      │ 写入                          │
│           Engine-B (Gemma-4, 后台总结)                │
│                      ▲                               │
│                      │ 最近 N 轮对话                  │
│              对话结束时触发                             │
└─────────────────────────────────────────────────────┘
```

### 1.3 双引擎策略

使用两个 Engine 实例，**严格隔离**：

| 引擎 | 职责 | 生命周期 | 优先级 |
|------|------|----------|--------|
| Engine-A | 用户对话（前台） | 应用启动时加载，常驻 | 高：用户正在等待 |
| Engine-B | 偏好总结（后台） | 对话结束后按需创建 | 低：可延迟、可跳过 |

**互斥规则（铁律）：**
- Engine-B 只在 Engine-A 空闲（`InferenceState.Ready`）时启动
- Engine-B 运行期间如果用户发消息，**立即取消 Engine-B**，让路给 Engine-A
- Engine-B 失败不影响主对话，静默跳过
- Engine-B 推理超时限制：60 秒硬中断

### 1.4 触发时机

不是每轮对话都触发总结，而是**对话暂停时**触发：

```
触发条件（满足任一）：
  - 用户 3 分钟内没有新消息（对话自然停顿）
  - 用户切换到其他会话
  - 用户切到后台 / 锁屏

不触发条件：
  - 对话正在进行（Engine-A 处于 Generating 状态）
  - Engine-B 上次运行距今 < 5 分钟（防频繁触发）
  - 本次对话只有 1-2 轮（信息量太少，不值得总结）
```

### 1.5 总结 Prompt 模板

```
请从以下对话中提取用户偏好，以 JSON 数组格式输出。
每条偏好包含 category（类别）和 content（内容）。

类别包括：
- name: 用户称呼/名字
- style: 回答风格偏好（简洁/详细/幽默等）
- interest: 兴趣领域
- habit: 使用习惯
- other: 其他值得记住的信息

如果没有值得记录的偏好，输出空数组 []

对话内容：
{最近 5 轮对话，格式：[用户]: xxx\n[助手]: xxx}
```

### 1.6 偏好存储与合并

总结结果写入 Room 的 `UserPreference` 表：

```kotlin
@Entity(tableName = "user_preferences")
data class UserPreference(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,        // name / style / interest / habit / other
    val content: String,         // 偏好内容
    val confidence: Int = 1,     // 确认次数（重复提取到同一偏好则 +1）
    val createdAt: Long,
    val updatedAt: Long
)
```

**合并策略：**
- 新偏好与已有偏好做**去重比较**（同一 category + 相似 content）
- 重复的偏好 confidence +1，更新 updatedAt
- confidence ≥ 3 的偏好视为"确认偏好"，注入 system prompt
- confidence = 1 的偏好暂不注入（可能是噪声）

### 1.7 System Prompt 注入格式

在 Engine-A 的 system prompt 末尾追加：

```
关于当前用户的已知信息（请自然地融入对话，不要刻意提及你知道这些）：
- 用户喜欢简洁的回答风格
- 用户对编程和音乐感兴趣
- 用户偏好用中文交流
```

### 1.8 风险与降级

| 风险 | 降级方案 |
|------|----------|
| 双引擎内存不足（OOM） | Engine-B 创建失败时，降级为**规则提取**（见下文） |
| Engine-B 总结质量差 | 结果仅 confidence=1，不注入 prompt，积累后人工确认 |
| 用户不希望被"记住" | 设置页提供"关闭自动总结"开关 |

**规则提取降级方案（不使用模型）：**
- 用户说"记住我叫XXX" → 正则匹配，直接存 name 偏好
- 用户说"回答简洁点" → 关键词匹配，存 style 偏好
- 用户说"别再提XXX了" → 关键词匹配，存 habit 偏好
- 这个降级方案始终生效，作为模型总结的**补充**

---

## 二、上下文管理设计

### 2.1 设计目标

控制发送给模型的上下文长度，防止长对话超出模型窗口限制。

### 2.2 现状分析

当前 `LiteRTLMInferenceEngine` 使用 `Conversation` 对象的 `sendMessageAsync()`，对话历史由 LiteRT-LM 内部管理。但我们需要**主动控制**上下文内容。

**核心改动：** 当对话轮数超过阈值时，创建新的 Conversation 对象，注入压缩后的上下文。

### 2.3 上下文窗口策略

```
┌──────────────────────────────────────────┐
│            发送给模型的上下文               │
│                                          │
│  ┌──────────────────────────────────┐    │
│  │ System Prompt                    │    │
│  │ + 用户偏好注入                    │    │
│  │ + 历史摘要（如果有）              │    │
│  └──────────────────────────────────┘    │
│  ┌──────────────────────────────────┐    │
│  │ 最近 N 轮完整对话                 │    │
│  │ （用户消息 + 助手回复，逐条保留）  │    │
│  └──────────────────────────────────┘    │
│  ┌──────────────────────────────────┐    │
│  │ 当前用户消息                      │    │
│  └──────────────────────────────────┘    │
└──────────────────────────────────────────┘

N = 保留轮数（默认 10 轮，可在设置中调整）
```

### 2.4 压缩触发与执行

```
触发条件：
  当前会话消息数 > (N * 2 + 10)
  （N*2 是保留的最近消息，+10 是缓冲区防止频繁压缩）

执行流程：
  1. 提取需要丢弃的中间消息（第 3 条到第 倒数N*2 条）
  2. 用 Engine-B 对这些消息做摘要（如果 Engine-B 不可用则跳过摘要）
  3. 创建新的 Conversation 对象
  4. 注入 system prompt + 摘要 + 最近 N 轮消息
  5. 旧 Conversation 释放
```

### 2.5 历史摘要 Prompt 模板

```
请将以下对话历史压缩为一段简洁的摘要，保留关键信息和上下文。
摘要不超过 200 字。

对话历史：
{被丢弃的消息，格式：[用户]: xxx\n[助手]: xxx}
```

### 2.6 上下文管理器接口

```kotlin
data class ContextWindow(
    val systemPrompt: String,           // 基础 system prompt
    val userPreferences: String,        // 注入的用户偏好
    val historySummary: String,         // 历史摘要（可能为空）
    val recentMessages: List<ChatMessage>,  // 最近 N 轮
    val currentMessage: ChatMessage     // 当前用户消息
)

interface ContextManager {
    // 判断是否需要压缩
    fun shouldCompress(messages: List<ChatMessage>): Boolean

    // 构建上下文窗口
    suspend fun buildContext(
        messages: List<ChatMessage>,
        systemPrompt: String,
        userPreferences: String
    ): ContextWindow

    // 压缩历史消息为摘要
    suspend fun compressHistory(messages: List<ChatMessage>): String
}
```

### 2.7 与 LiteRT-LM 的集成

由于 `Conversation` 对象管理内部历史，压缩后需要**重建 Conversation**：

```kotlin
// 伪代码
fun rebuildConversation(contextWindow: ContextWindow) {
    // 1. 释放旧 conversation
    conversation?.close()

    // 2. 创建新 conversation，system prompt 包含摘要
    val fullSystemPrompt = buildString {
        append(contextWindow.systemPrompt)
        if (contextWindow.userPreferences.isNotBlank()) {
            append("\n\n${contextWindow.userPreferences}")
        }
        if (contextWindow.historySummary.isNotBlank()) {
            append("\n\n之前对话的摘要：${contextWindow.historySummary}")
        }
    }

    val config = ConversationConfig(
        systemInstruction = Contents.of(fullSystemPrompt),
        samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7)
    )
    conversation = engine!!.createConversation(config)

    // 3. 将最近 N 轮消息"回放"到新 conversation 中
    //    注意：回放时不等待生成完成，只是把历史喂进去
    for (msg in contextWindow.recentMessages) {
        if (msg.role == MessageRole.USER) {
            conversation!!.sendMessageAsync(msg.content) // 需要消费掉返回的 flow
        }
        // 助手消息由 Conversation 内部管理，不需要手动回放
    }
}
```

**重要注意：** 上面的"回放"方案需要验证 LiteRT-LM 的 `Conversation` 是否支持这种用法。如果不行，替代方案是：
- 只在 system prompt 中注入完整的历史摘要
- 不做 Conversation 重建
- 牺牲一些上下文精度，换取实现简单性

---

## 三、记忆系统设计（SQLite FTS5）

### 3.1 设计目标

用 SQLite FTS5 全文搜索实现记忆的存储、分层和检索。记忆分为短期记忆和长期记忆。

### 3.2 记忆分层模型

```
┌─────────────────────────────────────────────────────┐
│                    记忆分层                            │
│                                                      │
│  ┌─────────────────────────────────────────────┐    │
│  │ 短期记忆 (ShortTerm)                          │    │
│  │ - 从对话中自动提取的事实                       │    │
│  │ - 生命周期：7 天                               │    │
│  │ - 超过 7 天未被引用 → 自动降级或丢弃            │    │
│  │ - 来源：Engine-B 总结 / 规则提取               │    │
│  └─────────────────────────────────────────────┘    │
│                    │                                 │
│                    │ 升级条件：被引用 ≥ 3 次          │
│                    │         或 用户主动标记          │
│                    ▼                                 │
│  ┌─────────────────────────────────────────────┐    │
│  │ 长期记忆 (LongTerm)                           │    │
│  │ - 确认重要的事实和偏好                         │    │
│  │ - 生命周期：永久（直到用户删除）                │    │
│  │ - 来源：短期记忆升级 / 用户手动添加             │    │
│  └─────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────┘
```

### 3.3 数据库设计

```kotlin
@Entity(tableName = "memories")
data class Memory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,              // 记忆内容（纯文本）
    val category: String,             // 分类：fact / preference / event / relationship
    val layer: String,                // 层级：short_term / long_term
    val source: String,               // 来源：auto_extract / user_marked / preference_sync
    val referenceCount: Int = 0,      // 被检索引用次数
    val sessionId: String? = null,    // 来源会话 ID
    val createdAt: Long,
    val updatedAt: Long,
    val expiresAt: Long? = null       // 短期记忆的过期时间
)
```

**FTS5 虚拟表：**

```sql
CREATE VIRTUAL TABLE memories_fts USING fts5(
    content,
    category,
    content='memories',
    content_rowid='id'
);

-- 触发器：插入时同步 FTS
CREATE TRIGGER memories_ai AFTER INSERT ON memories BEGIN
    INSERT INTO memories_fts(rowid, content, category)
    VALUES (new.id, new.content, new.category);
END;

-- 触发器：删除时同步 FTS
CREATE TRIGGER memories_ad AFTER DELETE ON memories BEGIN
    INSERT INTO memories_fts(memories_fts, rowid, content, category)
    VALUES ('delete', old.id, old.content, old.category);
END;

-- 触发器：更新时同步 FTS
CREATE TRIGGER memories_au AFTER UPDATE ON memories BEGIN
    INSERT INTO memories_fts(memories_fts, rowid, content, category)
    VALUES ('delete', old.id, old.content, old.category);
    INSERT INTO memories_fts(rowid, content, category)
    VALUES (new.id, new.content, new.category);
END;
```

### 3.4 记忆写入

记忆有三个写入来源：

| 来源 | 触发时机 | 层级 | 说明 |
|------|----------|------|------|
| Engine-B 自动总结 | 对话暂停时 | short_term | 自进化闭环的输出 |
| 规则提取 | 每轮对话后 | short_term | 关键词匹配用户明确指令 |
| 用户手动添加 | 记忆页操作 | long_term | 用户在记忆管理页手动输入 |

**规则提取关键词表：**

```kotlin
object MemoryRuleExtractor {
    private val rules = listOf(
        ExtractionRule(
            pattern = Regex("(?:记住|记下|别忘了|我叫|我的名字是)(.+)"),
            category = "fact",
            extractGroup = 1
        ),
        ExtractionRule(
            pattern = Regex("(?:我喜欢|我对.+感兴趣|我爱好)(.+)"),
            category = "preference",
            extractGroup = 1
        ),
        ExtractionRule(
            pattern = Regex("(?:我(?:在|住在|来自))(.+)"),
            category = "fact",
            extractGroup = 1
        ),
        ExtractionRule(
            pattern = Regex("(?:不要|别|禁止|不要再说)(.+)"),
            category = "preference",
            extractGroup = 1
        )
    )
}
```

### 3.5 记忆检索

当用户发送消息时，用 FTS5 搜索相关记忆注入上下文：

```kotlin
// 检索流程
suspend fun retrieveRelevantMemories(userMessage: String): List<Memory> {
    // 1. 提取用户消息的关键词（简单分词：按标点和空格拆分）
    val keywords = userMessage.split(Regex("[\\s，。！？、；：""''（）,.!?;:\"'()]+"))
        .filter { it.length >= 2 }  // 过滤太短的词
        .take(5)                     // 最多 5 个关键词

    if (keywords.isEmpty()) return emptyList()

    // 2. FTS5 查询
    val query = keywords.joinToString(" OR ")
    val results = memoryDao.searchByFTS(query, limit = 5)

    // 3. 优先返回长期记忆，短期记忆按引用次数排序
    return results.sortedWith(
        compareByDescending<Memory> { it.layer == "long_term" }
            .thenByDescending { it.referenceCount }
    )

    // 4. 引用次数 +1（标记为"被使用过"）
    results.forEach { memoryDao.incrementReference(it.id) }
}
```

### 3.6 记忆生命周期管理

```
定时清理（应用启动时执行一次）：
  - 短期记忆 expiresAt < 当前时间 → 删除
  - 短期记忆 referenceCount ≥ 3 → 升级为长期记忆
  - 长期记忆不自动删除

用户操作：
  - 在记忆管理页查看所有记忆
  - 手动添加/编辑/删除
  - 手动将短期记忆提升为长期记忆
  - 按分类筛选
```

### 3.7 记忆注入 System Prompt 格式

```
从记忆中检索到的相关信息（这些是过去对话中积累的知识）：
- [事实] 用户是一名 Android 开发者
- [偏好] 用户喜欢简洁的回答
- [事件] 用户上周提到要准备技术分享

请自然地利用这些信息，但不要刻意说"我记得你说过..."。
```

### 3.8 Room DAO 接口

```kotlin
@Dao
interface MemoryDao {
    @Insert
    suspend fun insert(memory: Memory): Long

    @Update
    suspend fun update(memory: Memory)

    @Delete
    suspend fun delete(memory: Memory)

    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    suspend fun getAll(): List<Memory>

    @Query("SELECT * FROM memories WHERE layer = :layer ORDER BY updatedAt DESC")
    suspend fun getByLayer(layer: String): List<Memory>

    @Query("SELECT * FROM memories WHERE category = :category ORDER BY updatedAt DESC")
    suspend fun getByCategory(category: String): List<Memory>

    // FTS5 搜索
    @Query("""
        SELECT m.* FROM memories m
        INNER JOIN memories_fts fts ON m.id = fts.rowid
        WHERE memories_fts MATCH :query
        ORDER BY m.layer DESC, m.referenceCount DESC
        LIMIT :limit
    """)
    suspend fun searchByFTS(query: String, limit: Int = 5): List<Memory>

    @Query("UPDATE memories SET referenceCount = referenceCount + 1 WHERE id = :id")
    suspend fun incrementReference(id: Long)

    @Query("UPDATE memories SET layer = 'long_term' WHERE id = :id")
    suspend fun promoteToLongTerm(id: Long)

    @Query("DELETE FROM memories WHERE layer = 'short_term' AND expiresAt < :now")
    suspend fun cleanupExpiredShortTerm(now: Long)

    @Query("SELECT * FROM memories WHERE layer = 'short_term' AND referenceCount >= 3")
    suspend fun getPromotableShortTerm(): List<Memory>
}
```

---

## 四、技能管理（Prompt 模板）

### 4.1 设计目标

管理预设的 prompt 模板（角色/场景），用户可以快速切换不同的对话模式。

### 4.2 数据模型

```kotlin
@Entity(tableName = "skills")
data class Skill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                 // 技能名称："翻译助手"、"代码审查"
    val description: String,          // 简短描述
    val systemPrompt: String,         // 完整的 system prompt
    val icon: String = "default",     // 图标标识
    val isBuiltIn: Boolean = false,   // 是否内置（内置的不可删除）
    val isActive: Boolean = false,    // 是否当前激活
    val usageCount: Int = 0,          // 使用次数
    val createdAt: Long,
    val updatedAt: Long
)
```

### 4.3 内置技能（出厂自带）

| 名称 | System Prompt 核心 |
|------|-------------------|
| 通用助手 | "你是一个友善的 AI 助手，请用中文回答用户的问题。" |
| 翻译助手 | "你是一个专业的翻译助手。用户会给你需要翻译的内容，请准确翻译并保持原意。" |
| 代码助手 | "你是一个编程助手，擅长代码审查和问题排查。回答时给出清晰的代码示例。" |
| 写作助手 | "你是一个写作助手，帮助用户润色文字、生成创意内容。" |

### 4.4 技能切换流程

```
用户在技能列表页选择技能
  → 更新当前会话的 system prompt
  → 如果引擎已初始化，重建 Conversation（新 system prompt）
  → 标记该技能 isActive = true，其他技能 isActive = false
  → usageCount + 1
```

### 4.5 Room DAO 接口

```kotlin
@Dao
interface SkillDao {
    @Insert
    suspend fun insert(skill: Skill): Long

    @Update
    suspend fun update(skill: Skill)

    @Delete
    suspend fun delete(skill: Skill)

    @Query("SELECT * FROM skills ORDER BY isBuiltIn DESC, usageCount DESC")
    suspend fun getAll(): List<Skill>

    @Query("SELECT * FROM skills WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): Skill?

    @Query("UPDATE skills SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE skills SET isActive = 1, usageCount = usageCount + 1, updatedAt = :now WHERE id = :id")
    suspend fun activate(id: Long, now: Long = System.currentTimeMillis())
}
```

### 4.6 UI 设计

技能管理页（复用 Settings 中已有的"角色管理"入口）：

```
┌─────────────────────────────────┐
│ 技能管理                   [+添加] │
├─────────────────────────────────┤
│ 当前激活                        │
│ ┌─────────────────────────────┐ │
│ │ 🤖 通用助手           [使用中] │ │
│ │ 你的默认 AI 伙伴              │ │
│ └─────────────────────────────┘ │
├─────────────────────────────────┤
│ 内置技能                        │
│ ┌─────────────────────────────┐ │
│ │ 🌐 翻译助手           已用12次 │ │
│ │ 📝 代码助手           已用5次  │ │
│ │ ✍️  写作助手           已用3次  │ │
│ └─────────────────────────────┘ │
├─────────────────────────────────┤
│ 我的技能                        │
│ ┌─────────────────────────────┐ │
│ │ 📋 周报生成器         已用8次  │ │
│ │    [编辑]  [删除]             │ │
│ └─────────────────────────────┘ │
└─────────────────────────────────┘
```

---

## 五、依赖与技术栈变更

### 5.1 需要新增的依赖

```kotlin
// build.gradle.kts 新增
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
kapt("androidx.room:room-compiler:2.6.1")  // 或 KSP
```

### 5.2 数据库结构总览

```
CompanionDatabase (Room)
├── conversations  → 会话表（替代 conversations.json）
├── messages       → 消息表（替代嵌套在会话 JSON 中）
├── memories       → 记忆表 + memories_fts（FTS5 虚拟表）
├── user_preferences → 用户偏好表（自进化闭环输出）
└── skills         → 技能表（Prompt 模板管理）
```

### 5.3 迁移策略

从当前 JSON 文件迁移到 Room：
1. 首次启动时检测 `conversations.json` 是否存在
2. 存在则读取并导入到 Room 数据库
3. 导入成功后重命名为 `conversations.json.bak`
4. 后续只使用 Room

---

## 六、实现优先级与阶段规划

### 阶段一：基础设施（先做）

| 任务 | 说明 | 工作量 |
|------|------|--------|
| 引入 Room | 添加依赖、创建 Database、DAO | 小 |
| 会话存储迁移 | conversations.json → Room | 中 |
| 消息存储迁移 | 嵌套 JSON → Room messages 表 | 中 |

### 阶段二：上下文管理（核心体验）

| 任务 | 说明 | 工作量 |
|------|------|--------|
| ContextManager 实现 | 滑动窗口 + 压缩触发 | 中 |
| Conversation 重建 | 压缩后重建 LiteRT-LM 对象 | 大（需验证 API） |
| System Prompt 构建器 | 组装基础 prompt + 偏好 + 记忆 | 小 |

### 阶段三：记忆系统（核心功能）

| 任务 | 说明 | 工作量 |
|------|------|--------|
| Memory 表 + FTS5 | 建表、触发器、DAO | 中 |
| 规则提取器 | 关键词匹配自动提取记忆 | 小 |
| 记忆检索注入 | FTS5 搜索 + System Prompt 注入 | 中 |
| MemoryScreen 完善 | 记忆管理 UI（列表、添加、删除） | 中 |

### 阶段四：自进化闭环（锦上添花）

| 任务 | 说明 | 工作量 |
|------|------|--------|
| 第二引擎管理器 | Engine-B 的创建、销毁、互斥控制 | 中 |
| 偏好总结 + 合并 | Prompt 模板 + 结果解析 + 去重 | 中 |
| 偏好注入 | UserPreference → System Prompt | 小 |

### 阶段五：技能管理（最后做）

| 任务 | 说明 | 工作量 |
|------|------|--------|
| Skill 表 + DAO | 建表、内置数据初始化 | 小 |
| 技能管理 UI | 列表页、添加/编辑页 | 中 |
| 技能切换逻辑 | 重建 Conversation + 切换 prompt | 小 |
| 接入设置页 | 角色管理入口 → 技能管理页 | 小 |

---

## 七、关键风险点

| 风险 | 影响 | 缓解方案 |
|------|------|----------|
| 双 Engine 内存不足 | OOM 崩溃 | Engine-B 创建前检查可用内存，不足则跳过 |
| Conversation 重建后丢失历史 | 上下文断裂 | 先验证 LiteRT-LM API 是否支持，不支持则降级为 prompt 注入 |
| FTS5 中文分词效果差 | 记忆检索不准 | 使用简单分词（按标点拆分），效果不够则引入轻量分词库 |
| 偏好总结质量不稳定 | 注入垃圾信息 | 只注入 confidence ≥ 3 的偏好，低置信度的需人工确认 |
| Room 迁移丢失数据 | 历史对话丢失 | 保留 JSON 备份文件，提供重新导入入口 |
