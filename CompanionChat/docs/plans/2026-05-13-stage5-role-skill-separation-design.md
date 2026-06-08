---
goal: CompanionChat 阶段五重设计：角色卡与 Skills 分离
version: 1.0
date_created: 2026-05-13
last_updated: 2026-05-13
owner: SOLO Code Assistant
status: Designed
tags: [design, stage5, roles, skills, prompt, compose, room]
---

# Introduction

本设计用于重定义 `CompanionChat` 的阶段五能力边界。原阶段五计划默认将设置页中的“角色管理”直接复用为技能管理页，但最新需求已明确：

- `skills` 是工作任务导向能力，不应与角色概念混合
- 角色卡用于日常聊天陪伴的人设与说话方式
- 设置页中“角色管理”和“Skills 管理”必须分离为两个独立入口
- 角色卡需要补齐创建、编辑、删除、激活能力
- 内置 skill 仅保留“翻译助手”一项

因此，阶段五需要从“单一 skill 管理功能”调整为“两套可同时启用的 prompt 系统”：

- **角色卡系统**：负责人格、人设、语气、背景设定
- **Skills 系统**：负责工作能力模板，如翻译、写作、代码等任务型能力

## 1. Requirements & Constraints

- **REQ-001**: 设置页必须同时提供“角色管理”和“Skills 管理”两个独立入口。
- **REQ-002**: `CharacterManagementScreen` 不再承载技能管理，而是重做为真正的角色卡管理页面。
- **REQ-003**: `Skills` 管理页面需要支持用户新增、编辑、删除、激活自己的 skills。
- **REQ-004**: 内置 skill 仅保留“翻译助手”一个，其余内置项 `通用助手`、`代码助手`、`写作助手` 移除。
- **REQ-005**: “翻译助手”内置 system prompt 需直接内置为强调“考虑使用者的语境、文化以及母语情况”的版本。
- **REQ-006**: 角色卡支持完整角色信息，不做简化版角色卡。
- **REQ-007**: 角色卡与 skill 可同时启用，不互斥。
- **REQ-008**: 角色卡同一时间只能激活一个。
- **REQ-009**: skill 同一时间只能激活一个。
- **REQ-010**: 最终聊天 prompt 必须同时支持基础 prompt、激活角色卡 prompt、激活 skill prompt、记忆/偏好/上下文的组合。
- **REQ-011**: 技能切换或角色切换后，若引擎已初始化，则需重建 `Conversation`，同时不丢失当前会话历史。
- **REQ-012**: 业务规则必须落在 Repository 或等价业务层，不允许仅依赖 UI 层保证内置项不可删、唯一激活等规则。
- **REQ-013**: 所有新增注释、文案、文档保持中文。

- **CON-001**: 当前 `ChatViewModel.baseSystemPrompt` 已是主 prompt 入口，设计优先复用，不额外并行引入第三套顶层 prompt 状态。
- **CON-002**: 当前阶段二、三、四链路已接入上下文压缩、记忆注入、偏好注入，阶段五重构不得破坏这些能力。
- **CON-003**: 当前 `skills` 表、`SkillDao`、数据库预置能力已存在，应尽量复用。
- **CON-004**: 原 `CharacterManagementScreen` 当前为占位页，可直接替换为真实角色管理页。
- **CON-005**: 当前数据库版本为 `1`，若新增角色卡表则必须评估 schema 升级或开发期重建策略。

## 2. High-Level Design

### 2.1 新的产品结构

设置页调整为两个独立入口：

- `角色管理`：进入角色卡列表页
- `Skills 管理`：进入 skill 列表页

两个页面职责完全分离：

- **角色卡页面** 管理“我想让 AI 以什么身份和人格陪伴我”
- **Skills 页面** 管理“我想让 AI 具备什么任务处理方式”

### 2.2 激活模型

系统允许同时存在：

- `1` 个激活角色卡
- `1` 个激活 skill

不允许：

- 多角色同时激活
- 多个 skills 同时激活

### 2.3 Prompt 组合顺序

最终进入现有上下文构建链路前，基础 prompt 组合顺序定义为：

1. 基础系统 prompt
2. 当前激活角色卡 prompt
3. 当前激活 skill prompt
4. 后续再由现有链路追加记忆、偏好、上下文摘要、最近消息

这样可以确保：

- 角色卡定义“你是谁、怎么说话、拥有什么人格”
- skill 定义“当前如何完成具体任务”
- 记忆和偏好继续作为个性化补充，而不是替代角色或任务指令

## 3. Role Card System Design

### 3.1 角色卡定位

角色卡不是简单昵称皮肤，而是一套完整人格设定。首版字段按“完整角色卡”设计，至少包含：

- 名称
- 简介
- 头像或图标标识
- 核心人设
- 说话风格
- 背景设定/世界观
- 行为规则
- 禁止项
- 开场白
- 示例对话
- 是否内置
- 是否当前激活
- 创建时间
- 更新时间

### 3.2 角色卡 Prompt 生成策略

角色卡不直接在 `ChatViewModel` 中逐字段拼接，而是在角色仓库或专门的 Prompt Builder 中，统一将角色字段转换成一段稳定文本，例如：

- 你的身份是谁
- 你与用户的关系是什么
- 说话时应保持什么语气
- 遵守哪些行为规则
- 避免哪些行为
- 首次或日常交互的风格示例

这样做的好处是：

- UI 字段可以继续扩展而不影响主流程
- `ChatViewModel` 只消费最终 prompt，不关心角色卡细节

### 3.3 角色卡管理行为

- 支持新增角色卡
- 支持编辑角色卡
- 支持删除自定义角色卡
- 支持激活角色卡
- 激活新角色卡时自动取消此前激活项
- 激活后若引擎可用，则立即触发当前会话的 prompt 重建

## 4. Skills System Redesign

### 4.1 Skills 页面独立

新增独立 `SkillsManagementScreen`，不再复用 `CharacterManagementScreen`。页面职责包括：

- 展示当前激活 skill
- 展示唯一内置 skill
- 展示用户自定义 skills
- 支持新增、编辑、删除、激活

### 4.2 内置 skill 重定义

数据库初始化只保留以下唯一内置项：

- `翻译助手`

建议内置 prompt 文案为：

`你是一个专业的翻译助手。请根据使用者的语境、文化背景以及母语情况，给出准确、自然、符合目标表达习惯的翻译结果；在保持原意的前提下，优先保证易懂、得体和语用自然。`

### 4.3 Skills 业务规则

- 内置 skill 不可删除
- 自定义 skill 可新增、编辑、删除
- 激活 skill 时自动取消其他 skill 激活状态
- 激活 skill 时 `usageCount + 1`
- skill 切换成功后若引擎已初始化，则立即更新主 prompt 并重建会话

## 5. Data Model Strategy

### 5.1 Skills 侧

继续沿用当前：

- `Skill` 实体
- `SkillDao`
- `skills` 表

但需修改数据库初始化种子，仅保留一个内置 skill。

### 5.2 角色卡侧

新增独立角色卡数据表，建议实体命名为 `RoleCard`，与 `Skill` 平行，而不是复用 `skills` 表。建议字段：

- `id`
- `name`
- `description`
- `avatar`
- `persona`
- `speakingStyle`
- `background`
- `rules`
- `taboos`
- `openingMessage`
- `exampleDialogue`
- `isBuiltIn`
- `isActive`
- `createdAt`
- `updatedAt`

### 5.3 Repository 分层

新增两层业务仓库：

- `RoleCardRepository`
- `SkillRepository`

职责包括：

- 输入去空白与校验
- 内置项不可删除保护
- 唯一激活规则
- 供 ViewModel 获取“当前激活项”和“完整列表”

## 6. UI Structure

### 6.1 设置页

设置页中的“角色”分组改为：

- `角色管理`：副标题改为“创建和切换陪伴角色卡”
- `Skills 管理`：副标题改为“管理工作能力模板和自定义 skills”

### 6.2 角色管理页

角色管理页建议结构：

- 顶部标题“角色管理”
- 右上角“+添加”
- 当前激活角色
- 我的角色卡列表
- 空状态提示
- 添加/编辑角色卡弹窗或独立编辑页
- 删除二次确认弹窗

### 6.3 Skills 管理页

Skills 页建议结构：

- 顶部标题“Skills 管理”
- 右上角“+添加”
- 当前激活
- 内置 skill
- 我的 skills
- 空状态提示
- 添加/编辑弹窗
- 删除二次确认弹窗

## 7. ChatViewModel Integration

### 7.1 Prompt 状态来源

当前 `ChatViewModel` 的 `baseSystemPrompt` 保留为真正的基础入口，但其值不再只是一个固定字符串，而是由以下内容动态组装：

- 默认基础 prompt
- 激活角色卡 prompt
- 激活 skill prompt

### 7.2 初始化行为

`ChatViewModel` 初始化后需额外执行：

- 读取当前激活角色卡
- 读取当前激活 skill
- 组装新的基础 prompt
- 在引擎初始化时直接使用该 prompt

### 7.3 切换行为

新增两个独立动作：

- `activateRoleCard(roleId: Long)`
- `activateSkill(skillId: Long)`

这两个动作内部都遵循：

1. 更新数据库激活状态
2. 重新读取当前激活角色与 skill
3. 重新组装 `baseSystemPrompt`
4. 若当前引擎未初始化，则仅更新状态，等待后续初始化生效
5. 若当前引擎已初始化且未处于不安全状态，则重建当前 `Conversation`
6. 会话消息列表保持不变，只刷新 prompt 与会话对象

### 7.4 与阶段二/三/四兼容

重建 `Conversation` 时必须继续复用现有链路：

- `ContextManager`
- `PromptAssembler`
- 记忆注入
- 偏好注入
- 摘要与最近消息回放

避免出现阶段五绕过原链路、导致上下文压缩和记忆失效的问题。

## 8. Error Handling

- 角色卡名称为空：阻止保存并提示
- 角色核心设定为空：阻止保存并提示
- skill 名称为空：阻止保存并提示
- skill prompt 为空：阻止保存并提示
- 删除内置 skill：业务层拒绝
- 删除内置角色卡：若后续存在内置角色卡，同样在业务层拒绝
- 切换角色或 skill 时若正在生成：需做状态保护，必要时拒绝切换或先终止当前生成
- 引擎未初始化：只更新数据库和本地状态，不强行重建

## 9. Testing Strategy

### 9.1 数据层

- `SkillRepositoryTest`
  - 仅保留一个内置项场景
  - 自定义 skill CRUD
  - 激活唯一性
  - `usageCount + 1`
  - 内置项不可删除

- `RoleCardRepositoryTest`
  - 角色卡新增、编辑、删除
  - 单角色唯一激活
  - 字段校验

### 9.2 ViewModel 层

- `RoleManagementViewModelTest`
  - 列表加载
  - 添加/编辑/删除
  - 激活状态切换

- `SkillManagementViewModelTest`
  - 当前激活 / 内置 / 我的 skills 分区
  - 添加/编辑/删除
  - 空状态驱动

- `ChatViewModelRoleSkillSwitchTest`
  - 激活角色卡后 `baseSystemPrompt` 更新
  - 激活 skill 后 `baseSystemPrompt` 更新
  - 两者同时启用时最终 prompt 同时包含两部分
  - 切换后触发 `Conversation` 重建
  - 历史消息不丢失

### 9.3 UI / 集成验证

- 设置页能分别进入角色管理和 Skills 管理
- 角色卡创建后可激活并影响对话风格
- 翻译助手作为唯一内置 skill 可直接激活并生效
- 自定义 skill 创建后可激活并影响回复任务方式
- 角色卡和 skill 同时启用时，对话同时体现人格与任务能力

## 10. Risks & Decisions

### 10.1 已确认决策

- `skills` 与角色卡分离
- 内置 skill 仅保留翻译助手
- 角色卡采用完整字段设计
- 角色卡与 skill 可同时启用
- 角色卡单激活
- skill 单激活

### 10.2 主要风险

- 角色卡新增表意味着数据库 schema 变化，需要补充 migration 或在开发阶段清库重建
- prompt 组合顺序若处理不当，可能影响已有记忆和偏好注入效果
- 切换角色或 skill 时若引擎正在生成，需要避免状态竞争
- 完整角色卡字段较多，首版 UI 若全部塞进一个对话框会偏重，可能需要折叠分区或独立编辑页

## 11. Recommended Implementation Order

1. 新增角色卡实体、DAO、Repository
2. 调整数据库初始化，仅保留翻译助手内置 skill
3. 设置页分离入口
4. 实现独立 `SkillsManagementScreen`
5. 将 `CharacterManagementScreen` 重做为真实角色卡管理页
6. 在 `ChatViewModel` 中接入“激活角色卡 + 激活 skill”的 prompt 组合
7. 补充测试并验证不破坏阶段二、三、四

## 12. Out of Scope

以下内容不作为本次阶段五重设计的首批实现范围：

- 多角色混合激活
- 每会话绑定不同角色卡
- 角色卡市场、导入导出
- skill 多选叠加
- 复杂模板变量系统

## 13. Related Files

- `app/src/main/java/com/companion/chat/ui/settings/CharacterManagementScreen.kt`
- `app/src/main/java/com/companion/chat/ui/settings/SettingsScreen.kt`
- `app/src/main/java/com/companion/chat/ui/chat/ChatViewModel.kt`
- `app/src/main/java/com/companion/chat/data/local/CompanionDatabase.kt`
- `app/src/main/java/com/companion/chat/data/local/entity/Skill.kt`
- `app/src/main/java/com/companion/chat/data/local/dao/SkillDao.kt`
- `docs/plans/2026-05-13-stage5-skill-management-plan.md`

