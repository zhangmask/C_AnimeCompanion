---
goal: CompanionChat 阶段五技能管理具体实施计划
version: 1.0
date_created: 2026-05-13
last_updated: 2026-05-13
owner: SOLO Code Assistant
status: Planned
tags: [feature, stage5, skills, prompt, compose, room]
---

# Introduction

![Status: Planned](https://img.shields.io/badge/status-Planned-blue)

本计划用于完成 `CompanionChat` 的阶段五“技能管理”功能。目标是基于现有 `skills` 表、`SkillDao`、数据库内置种子数据和设置页“角色管理”入口，补齐完整的 Prompt 模板管理闭环：技能列表、添加、编辑、删除、激活、使用次数累加、会话不中断的 system prompt 切换，以及对应的数据层、UI 层和集成验证。

## 1. Requirements & Constraints

- **REQ-001**: 对齐 `d:\Desktop\phone\COMPANIONCHAT_DESIGN.md` 中“技能管理（Prompt 模板）”设计目标、内置技能定义、切换流程和 UI 分区设计。
- **REQ-002**: 对齐 `d:\Desktop\phone\COMPANIONCHAT_TEST_CHECKLIST.md` 中阶段五 `5.1`、`5.2`、`5.3` 全部验收项。
- **REQ-003**: 保留当前 `Room` 中的 `skills` 表、`Skill` 实体、`SkillDao` 和数据库 `onCreate` 内置技能初始化，不重复造轮子。
- **REQ-004**: 阶段五完成后，设置页“角色管理”入口必须进入真实技能管理页，不允许继续停留在“即将上线”占位页。
- **REQ-005**: 技能切换必须影响主对话引擎的 `baseSystemPrompt`，并触发 `Conversation` 重建。
- **REQ-006**: 技能切换后当前会话历史不能丢失，后续继续对话必须可用。
- **REQ-007**: 内置技能不可删除，必须在业务层明确拦截，不依赖 UI 单层保护。
- **REQ-008**: 自定义技能必须支持新增、编辑、删除、激活和使用次数统计。
- **REQ-009**: 所有新增注释、提示文案、计划内容保持中文。
- **CON-001**: 当前主分支已完成阶段四，阶段五计划不得破坏现有 `ChatViewModel` 的记忆、偏好、上下文压缩和设置项链路。
- **CON-002**: 当前导航已使用 `SettingsRoutes.CHARACTER`，计划优先复用该路由，避免扩大导航改动范围。
- **CON-003**: 当前 `ChatViewModel` 使用 `baseSystemPrompt` 作为基础提示词来源，技能切换应复用这一主入口，而不是并行引入第二套 prompt 状态。
- **CON-004**: 当前数据库版本仍为 `1`，阶段五默认不做 schema 变更；仅在确认确有必要时再单独评估 migration。
- **PAT-001**: 数据访问通过 Repository 包装 `SkillDao`，UI 不直接操作 DAO。
- **PAT-002**: 采用 `ViewModel + UiState + Compose` 的现有项目模式，不在 Composable 中直接执行业务写库逻辑。
- **PAT-003**: 优先最小改动接入现有 `CharacterManagementScreen.kt` 占位文件，必要时拆辅助组件文件。
- **GUD-001**: 所有阶段五任务必须具备可执行验证方式，优先单元测试，其次编译验证，再做真机/手测。

## 2. Implementation Steps

### Implementation Phase 1

- **GOAL-001**: 固化技能数据层与业务边界，形成可复用的技能仓库接口。

| Task | Description | Completed | Date |
|------|-------------|-----------|------|
| TASK-001 | 新建 `app/src/main/java/com/companion/chat/data/skill/SkillRepository.kt`，封装 `getAllSkills()`、`getActiveSkill()`、`createSkill()`、`updateSkill()`、`deleteSkill()`、`activateSkill()`，统一调用 `SkillDao`。 |  |  |
| TASK-002 | 在 `SkillRepository` 中实现业务约束：删除内置技能时直接抛出业务异常或返回明确失败结果；激活技能时固定执行 `deactivateAll()` 后再 `activate(id)`。 |  |  |
| TASK-003 | 在 `SkillRepository` 中实现基础输入规范化：`name`、`description`、`systemPrompt` 去首尾空白，拒绝空名称和空 system prompt。 |  |  |
| TASK-004 | 新建 `app/src/test/java/com/companion/chat/data/skill/SkillRepositoryTest.kt`，覆盖内置技能不可删除、自定义技能 CRUD、激活唯一性、`usageCount + 1`。 |  |  |
| TASK-005 | 复查 `app/src/main/java/com/companion/chat/data/local/CompanionDatabase.kt` 中四条内置技能内容，与设计文档逐条比对；若文案不一致，仅在该文件修正种子内容。 |  |  |

### Implementation Phase 2

- **GOAL-002**: 用真实技能管理 UI 替换占位页，并补齐列表分区、增删改交互和空状态。

| Task | Description | Completed | Date |
|------|-------------|-----------|------|
| TASK-006 | 新建 `app/src/main/java/com/companion/chat/ui/settings/SkillManagementViewModel.kt`，持有 `SkillManagementUiState`，通过 `SkillRepository` 加载并区分“当前激活 / 内置技能 / 我的技能”。 |  |  |
| TASK-007 | 修改 `app/src/main/java/com/companion/chat/ui/settings/CharacterManagementScreen.kt`，将当前占位页替换为真实技能管理页 UI，标题改为“技能管理”，保留返回逻辑。 |  |  |
| TASK-008 | 在 `CharacterManagementScreen.kt` 中实现三段列表：当前激活、内置技能、我的技能；每项显示图标、名称、描述、使用次数，“使用中”标签和内置/自定义操作差异。 |  |  |
| TASK-009 | 新建 `app/src/main/java/com/companion/chat/ui/settings/SkillEditorDialog.kt` 或同文件内局部组件，实现添加/编辑弹窗表单：名称、描述、system prompt。 |  |  |
| TASK-010 | 在 UI 中实现删除二次确认弹窗，仅对自定义技能显示“编辑/删除”操作，内置技能不显示删除按钮。 |  |  |
| TASK-011 | 实现自定义技能空状态文案：“还没有自定义技能”，并保留“+添加”入口。 |  |  |
| TASK-012 | 新建 `app/src/test/java/com/companion/chat/ui/settings/SkillManagementViewModelTest.kt`，覆盖列表分区、添加、编辑、删除、激活和空状态驱动数据。 |  |  |

### Implementation Phase 3

- **GOAL-003**: 打通技能切换与主对话引擎的 prompt/Conversation 重建闭环。

| Task | Description | Completed | Date |
|------|-------------|-----------|------|
| TASK-013 | 修改 `app/src/main/java/com/companion/chat/ui/chat/ChatViewModel.kt`，新增 `SkillRepository` 依赖，并在初始化后读取当前激活技能，将其 `systemPrompt` 写入 `baseSystemPrompt`。 |  |  |
| TASK-014 | 在 `ChatViewModel.kt` 新增 `activateSkill(skillId: Long)`，内部执行 Repository 激活、更新 `baseSystemPrompt`、重建当前 `Conversation`，并保留现有会话消息状态。 |  |  |
| TASK-015 | 设计 `activateSkill(skillId: Long)` 的重建步骤：先取当前会话最近消息和当前 `baseSystemPrompt`，再复用现有上下文构建链路生成新 prompt，最后调用 `inferenceEngine.rebuildConversation(...)`。 |  |  |
| TASK-016 | 若当前引擎尚未初始化完成，则仅更新 `baseSystemPrompt` 和激活状态，不强行重建；待后续 `initializeEngine()` 时自动使用新技能 prompt。 |  |  |
| TASK-017 | 在 `CharacterManagementScreen.kt` 中为点击技能项接入 `onActivateSkill(skillId)` 回调，成功后立即刷新“使用中”标记和次数。 |  |  |
| TASK-018 | 新建 `app/src/test/java/com/companion/chat/ui/chat/ChatViewModelSkillSwitchTest.kt`，覆盖激活技能后 `baseSystemPrompt` 变更、`Conversation` 重建被调用、旧会话消息不丢失。 |  |  |

### Implementation Phase 4

- **GOAL-004**: 完成设置页接入、回归验证和阶段五验收闭环。

| Task | Description | Completed | Date |
|------|-------------|-----------|------|
| TASK-019 | 修改 `app/src/main/java/com/companion/chat/ui/settings/SettingsScreen.kt`，将“角色管理”副标题从“选择或创建 AI 伴侣角色”调整为与技能管理一致的说明，避免文案与实际功能不符。 |  |  |
| TASK-020 | 修改 `app/src/main/java/com/companion/chat/MainActivity.kt`，将 `SettingsRoutes.CHARACTER` 对应页面接入新的真实技能管理屏，并把激活回调传递到共享 `ChatViewModel`。 |  |  |
| TASK-021 | 复查 `app/src/main/java/com/companion/chat/ui/navigation/AppNavigation.kt` 与 `SettingsRoutes`，维持现有 `CHARACTER` 路由不变，不新增无必要路由。 |  |  |
| TASK-022 | 补充或更新 `app/src/test/java/com/companion/chat/ui/settings/SettingsScreenTest.kt`（如项目中尚无则新建），验证设置页点击“角色管理”后进入技能管理页。 |  |  |
| TASK-023 | 运行定向测试：技能仓库、技能 ViewModel、技能切换、设置页接入；再运行 `:app:assembleDebug`。 |  |  |
| TASK-024 | 真机/手工验证阶段五 `5.1.1-5.3.5`：四条内置技能、CRUD、切换生效、切换后会话不断、模型回答体现技能 prompt。 |  |  |
| TASK-025 | 完成阶段五实施后，将结果追加写入 `jindu.md`，记录实现范围、验证命令、真机结果和未解决项。 |  |  |

## 3. Alternatives

- **ALT-001**: 保留 `CharacterManagementScreen` 作为“角色管理”概念页，另外新增独立 `SkillManagementScreen` 和新路由。未选用原因：导航和文案改动面更大，当前设置入口本质上已承担阶段五入口职责。
- **ALT-002**: 直接在 `Composable` 中调用 `SkillDao` 完成 CRUD。未选用原因：违反现有项目 `ViewModel/Repository` 分层，会让删除内置技能等业务规则分散到 UI。
- **ALT-003**: 技能切换时只更新数据库 `isActive`，不立即重建 `Conversation`。未选用原因：会导致 prompt 生效滞后，不满足清单 `5.3.4`。
- **ALT-004**: 阶段五顺便重命名设置页“角色管理”为“技能管理”。暂不作为首要任务：如果只改展示文案即可满足一致性，不需要扩大路由和导航语义改动。

## 4. Dependencies

- **DEP-001**: `app/src/main/java/com/companion/chat/data/local/entity/Skill.kt`
- **DEP-002**: `app/src/main/java/com/companion/chat/data/local/dao/SkillDao.kt`
- **DEP-003**: `app/src/main/java/com/companion/chat/data/local/CompanionDatabase.kt`
- **DEP-004**: `app/src/main/java/com/companion/chat/ui/chat/ChatViewModel.kt`
- **DEP-005**: `app/src/main/java/com/companion/chat/engine/LiteRTLMInferenceEngine.kt`
- **DEP-006**: `app/src/main/java/com/companion/chat/ui/settings/SettingsScreen.kt`
- **DEP-007**: `app/src/main/java/com/companion/chat/MainActivity.kt`

## 5. Files

- **FILE-001**: `app/src/main/java/com/companion/chat/data/local/entity/Skill.kt`，复查实体字段是否仍满足阶段五。
- **FILE-002**: `app/src/main/java/com/companion/chat/data/local/dao/SkillDao.kt`，复查 DAO 能否直接支撑仓库。
- **FILE-003**: `app/src/main/java/com/companion/chat/data/local/CompanionDatabase.kt`，校验四条内置技能种子与默认激活逻辑。
- **FILE-004**: `app/src/main/java/com/companion/chat/data/skill/SkillRepository.kt`，新建技能业务仓库。
- **FILE-005**: `app/src/main/java/com/companion/chat/ui/settings/SkillManagementViewModel.kt`，新建技能管理状态层。
- **FILE-006**: `app/src/main/java/com/companion/chat/ui/settings/CharacterManagementScreen.kt`，替换占位页为真实技能管理 UI。
- **FILE-007**: `app/src/main/java/com/companion/chat/ui/settings/SkillEditorDialog.kt`，新建添加/编辑弹窗组件。
- **FILE-008**: `app/src/main/java/com/companion/chat/ui/settings/SettingsScreen.kt`，调整入口文案与导航意图。
- **FILE-009**: `app/src/main/java/com/companion/chat/MainActivity.kt`，接入技能页和 `ChatViewModel` 激活回调。
- **FILE-010**: `app/src/main/java/com/companion/chat/ui/chat/ChatViewModel.kt`，接入 active skill 读取和切换后 prompt 重建。
- **FILE-011**: `app/src/test/java/com/companion/chat/data/skill/SkillRepositoryTest.kt`，新增数据层测试。
- **FILE-012**: `app/src/test/java/com/companion/chat/ui/settings/SkillManagementViewModelTest.kt`，新增 UI 状态测试。
- **FILE-013**: `app/src/test/java/com/companion/chat/ui/chat/ChatViewModelSkillSwitchTest.kt`，新增技能切换集成测试。
- **FILE-014**: `app/src/test/java/com/companion/chat/ui/settings/SettingsScreenTest.kt`，新增或补充导航测试。
- **FILE-015**: `jindu.md`，记录阶段五计划与后续实施结论。

## 6. Testing

- **TEST-001**: `SkillRepositoryTest` 验证 `5.1.2`、`5.1.4`、`5.1.5`、`5.1.6`。
- **TEST-002**: 数据检查或单测验证 `CompanionDatabase` 内置四技能内容，对应 `5.1.1`、`5.1.3`。
- **TEST-003**: `SkillManagementViewModelTest` 验证三区列表分组、空状态、自定义技能显隐逻辑，对应 `5.2.2`、`5.2.5`、`5.2.6`、`5.2.11`。
- **TEST-004**: Compose/UI 测试验证添加、编辑、删除、确认弹窗，对应 `5.2.8`、`5.2.9`、`5.2.10`。
- **TEST-005**: `ChatViewModelSkillSwitchTest` 验证激活技能后 `baseSystemPrompt` 更新、`Conversation` 重建和历史不丢失，对应 `5.3.3`、`5.3.4`。
- **TEST-006**: 手工对话验证“翻译助手 / 代码助手 / 自定义技能 prompt 生效”，对应 `5.3.1`、`5.3.2`、`5.3.5`。
- **TEST-007**: 运行 `.\gradlew.bat :app:assembleDebug`，确保阶段五接入不破坏现有项目。
- **TEST-008**: 阶段六联动复查 `6.2.4`、`6.3.3`、`6.4.2`，确保模型未加载和会话操作下技能管理仍稳定。

## 7. Risks & Assumptions

- **RISK-001**: 当前 `CharacterManagementScreen` 是占位实现，直接替换时可能引入较大 UI 改动面。
- **RISK-002**: 技能切换和阶段三/四的 prompt 拼装共用 `baseSystemPrompt`，若重建顺序不对，可能覆盖记忆与偏好注入结果。
- **RISK-003**: 若切换技能时正处于生成中，直接重建 `Conversation` 可能与现有生成状态冲突，需要在实现时增加状态保护。
- **RISK-004**: 如果当前项目没有现成 Compose 导航/UI 测试基座，设置页与技能页测试可能需要先补最小测试脚手架。
- **ASSUMPTION-001**: 当前 `skills` 表和四条内置技能已随现有数据库正常存在，可作为阶段五开发基础。
- **ASSUMPTION-002**: 当前 `ChatViewModel.baseSystemPrompt` 仍是主 prompt 真正来源，阶段五无需重构为多 ViewModel prompt 系统。
- **ASSUMPTION-003**: 阶段五默认不新增数据库字段，因此不引入新 migration。

## 8. Related Specifications / Further Reading

- `d:\Desktop\phone\COMPANIONCHAT_DESIGN.md`
- `d:\Desktop\phone\COMPANIONCHAT_TEST_CHECKLIST.md`
- `d:\Desktop\phone\CompanionChat\docs\plans\2026-05-13-stage4-self-evolution-plan.md`
