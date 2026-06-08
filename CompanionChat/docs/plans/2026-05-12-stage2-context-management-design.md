# CompanionChat 阶段二：上下文管理设计

> 日期：2026-05-12
> 依据：`COMPANIONCHAT_DESIGN.md`、`COMPANIONCHAT_TEST_CHECKLIST.md`
> 目标：让长对话在不超出模型上下文限制的前提下继续进行，具备滑动窗口、历史摘要、Conversation 重建与降级保护能力。

## 1. 背景与问题

当前 `LiteRTLMInferenceEngine` 通过 LiteRT-LM 的 `Conversation` 对象维持内部历史，但实际发送时只传最后一条用户消息：

- 现有 `sendMessageStream()` 只取 `messages.lastOrNull { it.role == USER }`
- 历史上下文完全依赖 `Conversation` 内部状态
- 应用层无法主动控制“保留多少轮”“哪些内容被裁剪”“摘要何时注入”

这会带来三个直接问题：

- 长对话无法受控，最终可能超出模型窗口
- 切换 system prompt、技能、记忆注入后，历史上下文无法精确重组
- 后续阶段的记忆注入、偏好注入、技能切换都缺少统一上下文入口

因此阶段二的本质不是“做一个摘要函数”，而是把上下文控制权从 LiteRT-LM 内部拿回到应用层。

## 2. 设计目标

阶段二需要达成以下结果：

- 让应用能够判断当前消息列表是否需要压缩
- 在发送前构建统一的上下文窗口
- 在需要时重建 `Conversation`
- 当回放能力不可靠时，允许降级为“摘要注入 + 最近窗口保留”
- 保证压缩过程中不崩溃、不 ANR、不会把 UI 卡死
- 为后续阶段的偏好、记忆、技能切换预留 prompt 拼装入口

## 3. 范围与非目标

### 3.1 本阶段范围

- `ContextManager` 核心逻辑
- `ContextWindow` 数据模型
- 历史压缩触发规则
- Conversation 重建流程
- 回放主方案与降级方案
- 设置中的“保留轮数 N”配置项
- 本地日志与验证支撑

### 3.2 非目标

- 不在本阶段引入真正的记忆检索逻辑
- 不在本阶段实现用户偏好自动总结
- 不在本阶段实现第二引擎的完整生命周期管理
- 不在本阶段完成技能切换业务

这些能力只在接口层预留扩展点，不在阶段二落地完整业务。

## 4. 关键决策

### 4.1 `ContextManager` 放在 `ViewModel` 之外

最终决定：`ContextManager` 独立于 `ChatViewModel` 与 `LiteRTLMInferenceEngine`，作为应用层协调组件存在。

原因：

- `ViewModel` 负责 UI 状态，不适合承载复杂上下文裁剪和重建策略
- 引擎层应专注“初始化/发送/取消/释放”，不应直接负责策略判断
- 独立组件更适合单元测试，后续也更容易接入记忆、偏好、技能 prompt 构建器

职责边界如下：

- `ChatViewModel`：在发送前调用 `ContextManager`
- `ContextManager`：计算窗口、决定是否压缩、生成摘要、输出重建所需数据
- `LiteRTLMInferenceEngine`：执行“创建/重建/发送/释放”

### 4.2 主方案必须包含“消息回放”

最终决定：阶段二文档把“最近 N 轮消息回放到新 Conversation”定义为主方案。

原因：

- 验收清单 `2.2.3` 明确要求“最近 N 轮消息正确回放到新 Conversation”
- 只做摘要注入会明显损失最近轮次细节，不足以作为主方案
- 后续技能切换、记忆注入也都依赖可控的 Conversation 重建能力

同时明确写入：

- 回放不是无条件成功
- 只要 LiteRT-LM API 在实际运行中不稳定、语义不一致、耗时异常，就立即走降级方案

### 4.3 `N` 作为阶段二硬要求

最终决定：`N` 必须从阶段二开始就是配置项，默认值 `10`，并预留设置页可调入口。

原因：

- 验收清单 `2.1.6` 与 `2.4.4` 已把“可配置 N”列为阶段二要求
- 如果本阶段仍把 `N` 写死，后面会重复改 `ContextManager`、`ViewModel` 和设置页

本阶段先落“配置能力 + 默认值 + 设置入口联动”，不要求做复杂设置体系。

## 5. 两套方案

## 5.1 主方案：滑动窗口 + 历史摘要 + Conversation 重建 + 最近消息回放

这是阶段二的标准方案。

### 执行流程

1. 用户点击发送
2. `ChatViewModel` 收集当前会话全部消息
3. `ContextManager.shouldCompress()` 判断是否触发压缩
4. 若无需压缩，直接沿用当前 `Conversation`
5. 若需要压缩：
   - 提取最近 `N` 轮完整消息
   - 提取被丢弃的更早消息
   - 调用摘要生成器压缩历史
   - 生成新的完整 system prompt
   - 释放旧 `Conversation`
   - 用新 prompt 创建新 `Conversation`
   - 将最近 `N` 轮消息回放到新 `Conversation`
6. 最后发送当前用户消息

### 设计意图

- 让“近因信息”尽量以原始消息保留
- 让“远因信息”以摘要保留
- 避免窗口无限增长

## 5.2 降级方案：滑动窗口 + 摘要注入，不做消息回放

这是主方案的保护兜底，不是并列主线。

### 触发条件

只要满足任一条件，立即启用降级：

- LiteRT-LM 的回放语义与预期不一致
- 回放过程中产生异常
- 回放耗时过长，影响交互
- 当前引擎状态不适合做重建

### 降级做法

- 不逐条回放最近 N 轮历史
- 只把“历史摘要 + 最近若干原文片段”拼进新的 system prompt
- 然后直接发送当前用户消息

### 代价

- 近几轮细节保真度下降
- 模型可能不能精确引用最近一两轮的原话

### 收益

- 实现更稳
- 更容易避免重建期崩溃或卡死
- 在设备资源紧张时更可控

## 6. 总体架构

```text
ChatViewModel
  -> PromptAssembler
  -> ContextManager
      -> SummaryGenerator
  -> LiteRTLMInferenceEngine
      -> rebuildConversation()
      -> sendMessageStream()
```

新增组件职责如下：

- `PromptAssembler`
  - 负责组装基础 prompt、偏好占位、摘要段落
  - 本阶段偏好和记忆先传空字符串，但接口必须保留

- `ContextManager`
  - 判断是否压缩
  - 裁剪窗口
  - 组织 `ContextWindow`
  - 调用摘要生成器

- `SummaryGenerator`
  - 提供对被裁剪历史的摘要能力
  - 本阶段允许有两个实现：
    - `NoOpSummaryGenerator`：直接返回空字符串
    - `EngineBackedSummaryGenerator`：使用独立引擎/占位引擎生成摘要

- `LiteRTLMInferenceEngine`
  - 增加会话重建能力
  - 增加历史回放能力
  - 保持发送和取消逻辑可重用

## 7. 数据模型

### 7.1 `ContextWindow`

```kotlin
data class ContextWindow(
    val systemPrompt: String,
    val userPreferences: String,
    val historySummary: String,
    val recentMessages: List<ChatMessage>,
    val currentMessage: ChatMessage
)
```

说明：

- `systemPrompt`：基础 prompt
- `userPreferences`：阶段二先保留接口，默认可为空
- `historySummary`：被裁剪历史的压缩结果
- `recentMessages`：最近 `N` 轮完整消息
- `currentMessage`：当前待发送的用户消息

### 7.2 `ContextSettings`

```kotlin
data class ContextSettings(
    val retainedRounds: Int = 10,
    val compressionBuffer: Int = 10,
    val summaryMaxChars: Int = 200,
    val summaryTimeoutMillis: Long = 60_000L
)
```

说明：

- `retainedRounds`：保留轮数 `N`
- `compressionBuffer`：缓冲区，避免过于频繁压缩
- `summaryMaxChars`：摘要上限
- `summaryTimeoutMillis`：摘要超时

## 8. `ContextManager` 设计

### 8.1 接口

```kotlin
interface ContextManager {
    fun shouldCompress(messages: List<ChatMessage>, settings: ContextSettings): Boolean

    suspend fun buildContext(
        messages: List<ChatMessage>,
        systemPrompt: String,
        userPreferences: String,
        settings: ContextSettings
    ): ContextWindow

    suspend fun compressHistory(
        messages: List<ChatMessage>,
        settings: ContextSettings
    ): String
}
```

### 8.2 `shouldCompress()`

判定公式：

```text
messages.size > (N * 2 + buffer)
```

默认值下等价于：

```text
messages.size > (10 * 2 + 10) = 30
```

设计说明：

- 这里按“一轮 = 用户 + 助手”近似计算
- 消息存在异常缺配对时，仍按消息总数判断，不做复杂配对校正

### 8.3 `buildContext()`

逻辑：

1. 取当前用户消息作为 `currentMessage`
2. 去掉当前用户消息后，剩余历史参与压缩计算
3. 取最近 `N * 2` 条消息作为 `recentMessages`
4. 中间被丢弃的消息传给 `compressHistory()`
5. 返回完整 `ContextWindow`

约束：

- 如果没有需要裁剪的历史，则 `historySummary = ""`
- `recentMessages` 必须严格对应最近 `N` 轮

### 8.4 `compressHistory()`

调用摘要生成器：

- 成功：返回摘要
- 失败：返回空字符串
- 超时：返回空字符串
- 不可用：返回空字符串

阶段二要求“失败可降级，不影响主链路”。

## 9. Prompt 组装策略

完整 prompt 结构：

```text
[基础 System Prompt]

[用户偏好注入，可为空]

[历史摘要段落，可为空]
```

历史摘要段落格式固定为：

```text
之前对话的摘要：
{historySummary}
```

规则：

- `userPreferences` 为空时，不拼空标题
- `historySummary` 为空时，不拼“之前对话的摘要”
- 组装逻辑必须稳定、可测试，不能散落在 `ViewModel` 和引擎中

## 10. Conversation 重建设计

### 10.1 新增引擎能力

`LiteRTLMInferenceEngine` 需要新增以下能力：

- 读取当前引擎配置
- 使用新的 system prompt 重建 `Conversation`
- 回放指定历史消息
- 在重建期间保证只有一个有效会话实例

### 10.2 重建流程

```text
1. 锁定重建流程
2. 关闭旧 conversation
3. 生成 fullSystemPrompt
4. createConversation(fullSystemPrompt)
5. 回放 recentMessages
6. 解锁
7. 发送当前用户消息
```

### 10.3 回放规则

文档采用以下规则：

- 优先回放最近 `N` 轮完整消息
- 若 LiteRT-LM 对助手消息无法自然接受，则退化为只回放用户消息，并在摘要中补足助手上下文
- 回放必须在后台协程中执行
- 回放过程不向 UI 暴露中间生成文本

### 10.4 降级触发

一旦回放失败，立即：

- 记录日志
- 放弃继续回放
- 创建仅含摘要的新 `Conversation`
- 直接发送当前用户消息

## 11. 摘要生成器设计

阶段二文档里定义为接口，不与阶段四强绑定。

```kotlin
interface SummaryGenerator {
    suspend fun summarize(messages: List<ChatMessage>, settings: ContextSettings): String
}
```

### 11.1 推荐实现顺序

第一步：

- 先落 `NoOpSummaryGenerator`
- 确保整个压缩与重建框架先跑通

第二步：

- 再接 `EngineBackedSummaryGenerator`
- 使用独立引擎或可替换实现生成摘要

这样可以把“框架正确性”和“摘要质量”分开验证。

### 11.2 摘要 Prompt

```text
请将以下对话历史压缩为一段简洁摘要，保留关键信息与上下文。
摘要不超过 200 字。

对话历史：
{历史消息}
```

## 12. 并发与状态保护

阶段二最容易出问题的地方不是摘要，而是“用户发消息时引擎刚好在重建”。

本设计采用以下规则：

- 重建流程加互斥锁，任一时刻只能有一个重建任务
- 若用户连续发送消息：
  - 旧的重建任务取消
  - 新请求重新计算上下文
- 重建失败不得导致当前会话丢失
- 即使压缩失败，也必须回到“可继续发送当前消息”的状态

结论：

- 阶段二允许“放弃本轮压缩”
- 不允许“应用卡死 / conversation 丢失 / 状态错乱”

## 13. 设置项设计

阶段二增加一个最小配置入口：

- 名称：`上下文窗口大小`
- 含义：保留最近 `N` 轮
- 默认：`10`
- 范围建议：`3 ~ 20`

本阶段要求：

- 设置值可持久化
- `ContextManager` 能读取该值
- 修改后阈值立即生效

本阶段不要求：

- 做复杂设置页面重构
- 做高级参数组合配置

## 14. 测试与验收映射

### 14.1 后端

- `shouldCompress()` 阈值判断
- `buildContext()` 最近窗口裁剪正确
- `buildContext()` 无摘要时返回空字符串
- `systemPrompt` 拼装正确
- 修改 `N` 后阈值变化正确

### 14.2 重建链路

- 重建后旧 `Conversation` 被释放
- 新 `Conversation` 使用新 prompt
- 回放成功时可继续引用最近上下文
- 回放失败时进入降级路径
- 重建期间并发发消息不崩溃

### 14.3 集成

- 连续发 30+ 条消息不崩溃
- 压缩后继续追问，模型能理解之前内容
- 压缩期间 UI 仍可操作
- 修改 `N` 后更早或更晚触发压缩

## 15. 实现顺序建议

阶段二建议按以下顺序落地：

1. `ContextSettings` + 配置持久化
2. `PromptAssembler`
3. `ContextManager` 基础版
4. `NoOpSummaryGenerator`
5. 引擎侧 `rebuildConversation()` 框架
6. 回放能力验证
7. 降级路径补齐
8. 设置页 `N` 调整入口
9. 真机长对话压缩验证

## 16. 风险与缓解

### 风险 1：LiteRT-LM 回放语义与预期不一致

缓解：

- 把回放定义为主方案，但保留强制降级
- 用独立日志明确记录是否走回放还是降级

### 风险 2：摘要生成耗时过长

缓解：

- 超时直接返回空摘要
- 不阻塞主发送链路

### 风险 3：重建与用户新消息竞争

缓解：

- 互斥锁 + 取消旧任务
- 保证状态恢复路径始终存在

### 风险 4：阶段二过早耦合阶段三/四

缓解：

- 只预留接口，不接真实记忆和偏好业务
- 本阶段只保证 prompt 拼装口子稳定

## 17. 结论

阶段二采用“双方案文档”：

- 主方案：滑动窗口 + 历史摘要 + Conversation 重建 + 最近消息回放
- 降级方案：滑动窗口 + 摘要注入，不做消息回放

同时明确三项固定决策：

- `ContextManager` 独立于 `ViewModel` 与引擎
- 主方案必须包含消息回放
- `N` 在阶段二就是硬要求配置项

这份设计既对齐当前清单，也给 LiteRT-LM 的 API 不确定性留出了工程可落地的缓冲区。
