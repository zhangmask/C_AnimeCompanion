# CompanionChat 阶段四：自进化闭环设计

> 日期：2026-05-13
> 依据：`COMPANIONCHAT_DESIGN.md`、`COMPANIONCHAT_TEST_CHECKLIST.md`、当前阶段三已落地代码
> 目标：为 CompanionChat 建立“后台偏好总结 -> Room 合并存储 -> Prompt 注入 -> 设置可控”的阶段四闭环。

## 1. 背景

当前项目已经完成阶段三的核心闭环：

- Room 数据库已包含 `user_preferences` 表与 `PreferenceDao`
- 主聊天链路已经有 `PromptAssembler`、`ContextManager`、`ChatViewModel` 和阶段三记忆注入
- 设置页已有“记忆管理”“上下文窗口大小”等入口，说明设置页结构已经可承载阶段四开关
- `LiteRTLMInferenceEngine` 已支持独立初始化、会话重建、取消和释放，为第二引擎实例化提供了代码基础

因此阶段四不是从零开始建数据层，而是在现有聊天主链路旁边补一条低优先级后台学习链路。

## 2. 设计目标

阶段四交付后，需要满足以下结果：

- 当用户对话暂停时，可触发后台 Engine-B 对最近 5 轮对话做偏好总结
- 总结结果能被解析为结构化偏好并写入 `user_preferences`
- 相同类别且内容相同或等价的偏好能够合并，重复出现时提升 `confidence`
- 只有 `confidence >= 3` 的偏好会进入主对话 prompt
- Engine-B 故障、超时、OOM 或被用户消息抢占时，不影响前台对话
- 用户可在设置页显式关闭“自动学习偏好”，关闭后阶段四链路完全不触发

## 3. 范围与非目标

### 3.1 本阶段范围

- 第二引擎管理器
- 阶段四触发调度
- 偏好总结 prompt 模板
- JSON 解析与 `UserPreference` 合并写入
- 已确认偏好 prompt 注入
- 设置页“自动学习偏好”开关与持久化
- 最小真机联调与日志验收

### 3.2 非目标

- 不在本阶段引入新的模型文件或多模型切换
- 不在本阶段做复杂语义相似度去重，只先实现“规范化后精确匹配”
- 不在本阶段新增偏好管理独立 UI 页面
- 不在本阶段调整阶段三记忆表结构
- 不在本阶段做云同步、账号体系或跨设备共享

## 4. 关键决策

### 4.1 Engine-B 复用现有 `LiteRTLMInferenceEngine`

原因：

- 当前主引擎能力已经封装了 `initialize`、`cancel`、`release`
- 复用同一引擎实现可避免额外维护两套 LiteRT-LM 适配层
- 第二引擎的核心差异是“实例隔离”和“调度优先级”，不是推理接口本身

结论：

- 新增 `SecondEngineManager`，内部按需创建独立 `LiteRTLMInferenceEngine`
- Engine-B 与 Engine-A 使用同一路径模型，但绝不共享 `Conversation`

### 4.2 触发调度先放在 `ChatViewModel`

原因：

- 当前聊天会话切换、发送、前后台事件入口都已汇聚在 UI 编排层附近
- 阶段四需要感知“当前会话是否变化”“最近一次用户消息时间”“当前是否正在生成”
- 先放在 `ChatViewModel` 能最小改动打通闭环，后续再考虑拆分调度器

结论：

- `ChatViewModel` 负责在发送完成、切换会话、进入后台时发起阶段四触发检查
- 具体执行仍委托给 `SecondEngineManager` 和 `PreferenceRepository`

### 4.3 总结输入严格限制为最近 5 轮

原因：

- 设计文档和验收清单已给出明确边界
- 减少 Engine-B token 成本和后台执行时长
- 避免把阶段二/阶段三上下文压缩问题重新引入阶段四

结论：

- 总结输入固定取最近 10 条消息（5 轮用户+助手）
- 少于 3 条消息或不足 2 轮时直接跳过

### 4.4 去重先做“规范化精确匹配”

原因：

- 阶段四首版目标是稳定和可测，不是追求最强语义合并
- `PreferenceDao` 已有 `findExactMatch(category, content)`，适合最小演进
- 可以先通过大小写、空白、标点规范化覆盖大量重复输入

结论：

- 新增偏好规范化逻辑
- 合并键为 `category + normalizedContent`
- 未来若质量不足，再追加相似度匹配

### 4.5 规则提取降级必须一直保留

原因：

- 设计文档明确规则提取不是 Engine-B 的替代，而是补充
- 这能保证 OOM、超时、关闭自动学习时，显式用户指令仍可被写入偏好或记忆

结论：

- 阶段四实现不移除现有规则记忆提取
- 仅新增“偏好自动学习”链路，不改阶段三记忆链路的职责归属

## 5. 总体架构

```text
ChatViewModel
  -> 阶段四触发检查
      -> SecondEngineManager
          -> LiteRTLMInferenceEngine (Engine-B)
      -> PreferenceSummaryPromptBuilder
      -> PreferenceSummaryParser
      -> PreferenceRepository
          -> PreferenceDao
      -> PromptAssembler
  -> 现有 Engine-A 对话链路
```

职责边界如下：

- `ChatViewModel`
  - 记录最近用户消息时间
  - 在发送完成、会话切换、进入后台时触发阶段四检查
  - 在用户再次发送消息时抢占取消 Engine-B

- `SecondEngineManager`
  - 创建、初始化、执行、取消、释放 Engine-B
  - 负责互斥、超时和失败降级日志

- `PreferenceSummaryPromptBuilder`
  - 生成阶段四总结 prompt
  - 将最近 5 轮对话格式化为稳定输入

- `PreferenceSummaryParser`
  - 解析 JSON 数组
  - 将模型输出变成结构化偏好项

- `PreferenceRepository`
  - 负责合并、插入、读取 confirmed 偏好
  - 封装规范化、去重和 `confidence` 递增

- `PromptAssembler`
  - 在存在 confirmed 偏好时追加“关于当前用户的已知信息”段

## 6. 数据与状态设计

### 6.1 复用现有 `UserPreference`

当前实体已满足阶段四最小需求：

- `category`
- `content`
- `confidence`
- `createdAt`
- `updatedAt`

本阶段不改表结构，只补业务层合并策略。

### 6.2 新增设置项

在 `ContextConfigRepository` 或配套设置仓库中新增布尔配置：

- `autoPreferenceLearningEnabled: Boolean = true`

要求：

- 默认开启
- 设置页可修改
- 重启后保持

## 7. 关键流程

### 7.1 发送后自然停顿触发

1. 用户完成一轮对话
2. `ChatViewModel` 记录最近活动时间
3. 满足“3 分钟无新消息”或其他触发条件
4. 检查自动学习开关、消息轮数、最近 5 分钟是否已总结、Engine-A 是否空闲
5. 满足条件后启动 Engine-B

### 7.2 Engine-B 总结流程

1. 取最近 5 轮消息
2. 由 `PreferenceSummaryPromptBuilder` 构造 prompt
3. `SecondEngineManager` 初始化独立引擎并发送总结请求
4. 限制 60 秒超时
5. 返回 JSON 字符串后交给 `PreferenceSummaryParser`
6. 解析成功则进入 `PreferenceRepository.mergePreferences`
7. 完成后关闭 Engine-B 会话并释放资源

### 7.3 抢占取消

1. Engine-B 正在总结
2. 用户再次发送消息
3. `ChatViewModel` 先调用 `SecondEngineManager.cancelRunningSummary()`
4. 随后继续正常前台对话
5. 取消只影响后台任务，不影响 Engine-A

### 7.4 Prompt 注入

1. `ChatViewModel` 在发送前读取 `PreferenceRepository.getConfirmedPreferences()`
2. `PromptAssembler` 在存在 confirmed 偏好时追加固定标题和列表项
3. 无 confirmed 偏好时不拼空段落

## 8. 对验收清单的落地映射

### 8.1 对应 4.1 Engine-B 管理器

- 新增 `SecondEngineManager`
- 为 Engine-B 加入独立初始化、取消、释放、超时保护
- 单测覆盖互斥和抢占

### 8.2 对应 4.2 触发时机

- 在 `ChatViewModel` 明确三类触发入口：
  - 3 分钟无新消息
  - 切换会话
  - 应用进后台
- 同时实现 5 分钟节流和最少轮数限制

### 8.3 对应 4.3 偏好解析与存储

- 新增 prompt builder、parser、repository
- repository 封装“规范化 + 查重 + confidence+1”

### 8.4 对应 4.4 偏好注入

- 在 `PromptAssembler` 增加 confirmed 偏好段
- 补全字符串格式测试

### 8.5 对应 4.5 规则提取降级

- 保持阶段三规则链路不变
- Engine-B 跳过或失败时不影响规则提取继续工作

### 8.6 对应 4.6 设置页开关

- 设置页新增布尔开关
- 设置仓库持久化
- 关闭后阻止所有阶段四触发

## 9. 风险与应对

| 风险 | 影响 | 应对 |
|------|------|------|
| 第二引擎初始化过慢 | 后台任务堆积、影响体验 | 加 60 秒超时，且仅在 Engine-A 空闲时运行 |
| OOM 或模型初始化失败 | 阶段四不可用 | 静默跳过，仅记日志，主链路不受影响 |
| 模型输出非 JSON | 偏好无法入库 | parser 容错返回空列表，不抛到 UI |
| 重复总结过于频繁 | 无意义写库 | 5 分钟节流 + 少于 2 轮不触发 |
| 低质量偏好污染 prompt | 回答跑偏 | 仅注入 `confidence >= 3` |

## 10. 验证策略

先做后端单测，再做主链路编译，最后做真机验证：

- `SecondEngineManager` 单测：互斥、超时、取消、释放
- `PreferenceSummaryParser` 单测：合法 JSON、空数组、乱码
- `PreferenceRepository` 单测：新增、合并、confirmed 查询
- `PromptAssembler` 单测：偏好段注入和空段落抑制
- `SettingsScreen` / 设置仓库单测：开关持久化
- 真机验收：
  - 手动构造 confirmed 偏好看 prompt 注入
  - 关闭开关确认不触发
  - 重新开启后查看日志触发总结
