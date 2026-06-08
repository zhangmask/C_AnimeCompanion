# CompanionChat 阶段三：记忆系统设计

> 日期：2026-05-13
> 依据：`COMPANIONCHAT_TEST_CHECKLIST.md`、阶段二现状、当前 Room 数据结构
> 目标：为 CompanionChat 建立完整的记忆系统闭环，包含规则提取、Room 写入、FTS 检索、分层生命周期、Prompt 注入和记忆管理 UI。

## 1. 背景

当前项目已经具备阶段三的部分数据地基：

- `memories`、`user_preferences`、`skills` 表已经存在
- `memories_fts` 虚拟表和同步触发器已经创建
- `MemoryDao`、`PreferenceDao`、`SkillDao` 已有基础 CRUD 和部分查询能力
- `MemoryScreen` 仍是占位页，聊天链路中也尚未接入“记忆提取”和“记忆注入”

也就是说，阶段三不是从零开始，而是把“数据库骨架”升级为“真实可运行的记忆系统”。

## 2. 设计目标

阶段三要达成以下结果：

- 用户消息可被规则提取器识别为事实或偏好类记忆
- 提取结果自动写入 `memories` 表，形成短期记忆
- 当前用户消息可触发 FTS 检索，返回相关记忆
- 相关记忆能注入到发送前 prompt 中
- 应用启动时自动做短期记忆清理和长期提升
- `MemoryScreen` 能完成基础管理：展示、筛选、手动增删改、手动提升

## 3. 范围与非目标

### 3.1 本阶段范围

- 基于规则的 `MemoryExtractor`
- 记忆仓库层与 FTS 检索封装
- 记忆生命周期管理
- 记忆 prompt 注入
- `MemoryScreen` 最小可用版
- 与聊天链路的最小集成

### 3.2 非目标

- 不在本阶段引入模型总结式记忆抽取
- 不在本阶段实现偏好自动总结引擎
- 不在本阶段做多轮复杂事件压缩
- 不在本阶段引入记忆重要性学习策略
- 不在本阶段做云同步或跨设备记忆共享

这些能力属于后续阶段增强，不纳入阶段三交付范围。

## 4. 关键决策

### 4.1 阶段三先用规则提取，不用模型提取

原因：

- 规则提取更稳定、可测、低成本
- 阶段四已经规划了模型驱动的偏好总结能力
- 当前目标是先打通“提取 -> 写入 -> 检索 -> 注入 -> 管理”的闭环

结论：

- 阶段三的自动写入以规则提取为主
- 模型总结不提前并入阶段三

### 4.2 记忆写入走仓库层，不直接散落在 ViewModel

原因：

- `ChatViewModel` 已经承担上下文和推理编排，不适合再承载复杂记忆策略
- 提取、去重、写入、检索、提升都需要集中逻辑
- 仓库层便于单元测试和后续扩展

结论：

- 新增 `MemoryRepository`
- `ChatViewModel` 只负责在发送链路中调用仓库接口

### 4.3 记忆注入只注入“相关记忆”，不注入全部记忆

原因：

- 全量注入会快速放大 prompt
- 阶段三已经有阶段二的上下文窗口约束，必须避免 prompt 膨胀
- FTS 检索的意义就在于做“按当前问题取相关记忆”

结论：

- 每次最多注入 5 条记忆
- 由 `MemoryPromptBuilder` 统一格式化

## 5. 总体架构

```text
ChatViewModel
  -> MemoryRepository
      -> MemoryExtractor
      -> MemoryRetriever
      -> MemoryLifecycleManager
      -> MemoryPromptBuilder
      -> MemoryDao
  -> ContextManager
  -> PromptAssembler
  -> LiteRTLMInferenceEngine
```

职责边界如下：

- `ChatViewModel`
  - 发送前请求相关记忆
  - 发送后触发自动提取写入

- `MemoryRepository`
  - 统一封装写入、检索、去重、提升、清理

- `MemoryExtractor`
  - 负责规则提取

- `MemoryRetriever`
  - 负责将用户消息转成 FTS 查询并取回结果

- `MemoryPromptBuilder`
  - 负责将相关记忆转成 prompt 片段

- `MemoryLifecycleManager`
  - 负责应用启动时清理过期记忆和提升长期记忆

- `MemoryScreen`
  - 负责记忆可视化和手动管理

## 6. 数据模型约定

当前 `Memory` 实体如下：

```kotlin
data class Memory(
    val id: Long = 0,
    val content: String,
    val category: String,
    val layer: String,
    val source: String,
    val referenceCount: Int = 0,
    val sessionId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val expiresAt: Long? = null
)
```

阶段三采用以下字段约定：

- `category`
  - `fact`
  - `preference`
  - `event`
  - `relationship`

- `layer`
  - `short_term`
  - `long_term`

- `source`
  - `rule_extractor`
  - `manual`

## 7. 规则提取器设计

### 7.1 输入

- 当前用户消息
- 当前会话 `sessionId`

### 7.2 输出

- `ExtractedMemory` 列表，每条包含：
  - `content`
  - `category`
  - `layer`
  - `source`
  - `expiresAt`

### 7.3 首批规则范围

阶段三第一版覆盖以下模式：

- `记住我叫X` -> `fact`：`用户叫X`
- `我叫X` -> `fact`：`用户叫X`
- `我喜欢X` -> `preference`：`用户喜欢X`
- `我住在X` -> `fact`：`用户住在X`
- `不要再说X了` -> `preference`：`不要再说X`

### 7.4 默认写入策略

- 自动提取写入 `short_term`
- `expiresAt = now + 7天`
- `referenceCount = 0`
- `source = rule_extractor`

## 8. FTS 检索设计

### 8.1 查询入口

新增 `retrieveRelevantMemories(userMessage: String): List<Memory>`

### 8.2 查询流程

1. 对用户消息做最小关键词清洗
2. 过滤长度过短或无效输入
3. 构造 FTS 查询
4. 调用 `MemoryDao.searchByFTS`
5. 按以下优先级排序：
   - 长期记忆优先
   - 再按 `referenceCount` 降序
   - 再按 `updatedAt` 降序
6. 最多返回 5 条
7. 对命中结果执行 `incrementReference`

### 8.3 检索输入限制

- 空消息不检索
- 单字符或纯符号消息不检索
- 最多取当前消息中的前若干关键词，不做复杂 NLP

## 9. 生命周期管理设计

### 9.1 启动时清理

应用启动时执行：

- `cleanupExpiredShortTerm(now)`

### 9.2 启动时提升

应用启动时执行：

- `getPromotableShortTerm()`
- 将 `referenceCount >= 3` 的短期记忆提升为长期记忆

### 9.3 手动写入规则

- 用户在 `MemoryScreen` 手动新增的记忆直接记为 `long_term`
- `expiresAt = null`
- `source = manual`

## 10. Prompt 注入设计

### 10.1 新增组件

新增 `MemoryPromptBuilder`

### 10.2 注入格式

相关记忆存在时，固定注入段落：

```text
从记忆中检索到的与当前对话相关的信息：
- [事实] 用户叫小明
- [偏好] 用户喜欢简洁回答
```

### 10.3 规则

- 无相关记忆时不拼空段落
- 只注入检索命中的相关记忆
- 该段落作为 `userPreferences` 旁边的单独扩展段落接入现有 prompt 组装器

## 11. 聊天链路接入

### 11.1 发送前

在 `ChatViewModel` 发送前流程中增加：

- 基于当前用户消息检索相关记忆
- 用 `MemoryPromptBuilder` 构造记忆 prompt
- 将记忆 prompt 接入阶段二已有的 prompt 组装链路

### 11.2 发送后

在用户消息成功进入会话后：

- 用 `MemoryExtractor` 对该用户消息做规则提取
- 将提取结果写入数据库

### 11.3 启动时

应用启动时执行：

- 短期记忆过期清理
- 可提升短期记忆提升为长期记忆

## 12. MemoryScreen 设计

### 12.1 第一版能力

- 列出全部记忆
- 按分类筛选
- 显示层级与分类标签
- 手动新增记忆
- 编辑现有记忆
- 删除记忆
- 将短期记忆手动提升为长期记忆

### 12.2 第一版不做

- 批量操作
- 高级搜索
- 拖拽排序
- 多选编辑

### 12.3 空状态

当没有记忆时显示：

`还没有记忆，对话中说“记住...”会自动保存`

## 13. 测试与验收映射

### 13.1 后端

- 规则提取命中和误伤控制
- 自动写入 `short_term`
- `expiresAt = now + 7天`
- FTS 插入/删除/更新联动
- 检索结果排序与上限
- 命中后引用次数递增
- 启动时清理和提升逻辑
- 记忆 prompt 拼接正确

### 13.2 前端

- 记忆列表展示正确
- 分类筛选正确
- 手动增删改正确
- 短期/长期视觉区分清晰
- 设置页可跳转

### 13.3 集成

- “记住我叫小明”后能写入记忆
- 问“我叫什么”时模型能利用注入记忆
- 删除会话不影响记忆表

## 14. 推荐实现顺序

阶段三按以下顺序推进：

1. `MemoryExtractor`
2. `MemoryRepository`
3. `MemoryRetriever`
4. `MemoryPromptBuilder`
5. 聊天链路接入
6. 生命周期管理
7. `MemoryViewModel`
8. `MemoryScreen`
9. 真机集成验证

## 15. 风险与缓解

### 风险 1：规则误提取

缓解：

- 第一版规则范围从小开始
- 单测覆盖“应命中”和“不应命中”

### 风险 2：FTS 查询不稳定

缓解：

- 继续沿用当前已验证可用的 FTS4 + 触发器方案
- 检索层统一封装查询构造，不让 UI 直接拼 SQL

### 风险 3：记忆注入导致 prompt 膨胀

缓解：

- 最多注入 5 条
- 内容按简洁格式输出
- 与阶段二上下文压缩逻辑协同工作

### 风险 4：UI 一开始做太重

缓解：

- MemoryScreen 第一版只做最小可用管理页
- 高级功能全部延后

## 16. 结论

阶段三采用“**规则提取 + Room 写入 + FTS 检索 + Prompt 注入 + 基础管理 UI**”的完整方案。

它与现有数据地基保持一致，并且按“后端主链路先闭环，UI 随后收口”的顺序推进，能在风险可控的前提下尽快让 CompanionChat 具备真正可用的记忆能力。
