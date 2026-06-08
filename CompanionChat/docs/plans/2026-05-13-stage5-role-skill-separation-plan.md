# Stage5 Role And Skill Separation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将阶段五重构为“角色卡管理 + Skills 管理”双系统，支持单角色激活、单 skill 激活、两者同时参与聊天 prompt，并保持阶段二到阶段四链路稳定。

**Architecture:** 保留现有 `Skill`/`SkillDao`/`skills` 表，收缩为“唯一内置翻译助手 + 用户自定义 skills”；新增独立 `RoleCard` 数据模型、DAO、Repository 和管理页面。`ChatViewModel` 继续复用 `baseSystemPrompt` 作为入口，通过“基础 prompt + 激活角色卡 prompt + 激活 skill prompt”生成新的基础提示词，再走现有上下文、记忆、偏好拼装链路。

**Tech Stack:** Kotlin、Jetpack Compose、Room、KSP、Android ViewModel、JUnit4

---

### Task 1: 建立角色卡数据层

**Files:**
- Create: `app/src/main/java/com/companion/chat/data/local/entity/RoleCard.kt`
- Create: `app/src/main/java/com/companion/chat/data/local/dao/RoleCardDao.kt`
- Modify: `app/src/main/java/com/companion/chat/data/local/CompanionDatabase.kt`
- Test: `app/src/test/java/com/companion/chat/data/role/RoleCardDaoContractTest.kt`

**Step 1: 写失败测试**

```kotlin
@Test
fun getActive_returns_only_one_active_role_card() = runTest {
    val dao = FakeRoleCardDao()
    dao.insert(roleCard(id = 1, isActive = true))
    dao.insert(roleCard(id = 2, isActive = false))

    val active = dao.getActive()

    assertEquals(1L, active?.id)
}
```

**Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.role.RoleCardDaoContractTest"`

Expected: FAIL，提示 `RoleCard` 或 `RoleCardDao` 不存在。

**Step 3: 写最小实现**

- 新增 `RoleCard` 实体，字段包含：
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
- 新增 `RoleCardDao`，至少提供：
  - `insert()`
  - `update()`
  - `delete()`
  - `getAll()`
  - `getActive()`
  - `deactivateAll()`
  - `activate(id)`
- 在 `CompanionDatabase` 中注册 `RoleCard` 和 `roleCardDao()`

**Step 4: 再跑测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.role.RoleCardDaoContractTest"`

Expected: PASS

**Step 5: 编译数据库相关代码**

Run: `.\gradlew.bat :app:assembleDebug`

Expected: PASS，Room/KSP 代码生成正常。

### Task 2: 收敛 skills 数据层并只保留内置翻译助手

**Files:**
- Modify: `app/src/main/java/com/companion/chat/data/local/CompanionDatabase.kt`
- Create: `app/src/main/java/com/companion/chat/data/skill/SkillRepository.kt`
- Test: `app/src/test/java/com/companion/chat/data/skill/SkillRepositoryTest.kt`

**Step 1: 写失败测试**

```kotlin
@Test
fun deleteSkill_rejects_built_in_translation_skill() = runTest {
    val dao = FakeSkillDao(
        skills = mutableListOf(
            builtInSkill(id = 1, name = "翻译助手", isBuiltIn = true)
        )
    )
    val repository = SkillRepository(dao)

    assertFailsWith<IllegalStateException> {
        repository.deleteSkill(1L)
    }
}
```

**Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.skill.SkillRepositoryTest"`

Expected: FAIL，提示 `SkillRepository` 不存在或行为不符合预期。

**Step 3: 写最小实现**

- 新增 `SkillRepository`
- 实现：
  - `getAllSkills()`
  - `getActiveSkill()`
  - `createSkill()`
  - `updateSkill()`
  - `deleteSkill()`
  - `activateSkill()`
- 业务规则：
  - 名称和 `systemPrompt` 去首尾空白
  - 名称和 `systemPrompt` 不能为空
  - 内置 skill 不可删除
  - 激活时先 `deactivateAll()` 再 `activate()`
- 将 `CompanionDatabase` 内置 seeds 改为只保留 `翻译助手`
- 将 `翻译助手` 的 `systemPrompt` 固定为强调“考虑使用者的语境、文化以及母语情况”的版本

**Step 4: 再跑测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.skill.SkillRepositoryTest"`

Expected: PASS

**Step 5: 补充数据库种子验证**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.local.*"`

Expected: PASS，或至少编译通过并能验证唯一内置 skill 逻辑。

### Task 3: 建立角色卡业务层与 Prompt 生成

**Files:**
- Create: `app/src/main/java/com/companion/chat/data/role/RoleCardRepository.kt`
- Create: `app/src/main/java/com/companion/chat/data/role/RoleCardPromptBuilder.kt`
- Test: `app/src/test/java/com/companion/chat/data/role/RoleCardRepositoryTest.kt`
- Test: `app/src/test/java/com/companion/chat/data/role/RoleCardPromptBuilderTest.kt`

**Step 1: 写失败测试**

```kotlin
@Test
fun buildPrompt_includes_persona_style_rules_and_taboos() {
    val prompt = RoleCardPromptBuilder().build(
        roleCard(
            name = "小夏",
            persona = "温柔陪伴型伙伴",
            speakingStyle = "自然、轻松、不过分正式",
            rules = "优先共情，再给建议",
            taboos = "不要说教"
        )
    )

    assertTrue(prompt.contains("温柔陪伴型伙伴"))
    assertTrue(prompt.contains("自然、轻松、不过分正式"))
    assertTrue(prompt.contains("优先共情，再给建议"))
    assertTrue(prompt.contains("不要说教"))
}
```

**Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.role.RoleCardRepositoryTest" --tests "com.companion.chat.data.role.RoleCardPromptBuilderTest"`

Expected: FAIL

**Step 3: 写最小实现**

- `RoleCardRepository` 实现：
  - `getAllRoleCards()`
  - `getActiveRoleCard()`
  - `createRoleCard()`
  - `updateRoleCard()`
  - `deleteRoleCard()`
  - `activateRoleCard()`
- 业务规则：
  - `name`、`persona` 必填
  - 单角色唯一激活
  - 内置角色若未来存在，不可删除
- `RoleCardPromptBuilder` 统一把角色卡字段拼成稳定 prompt 文本

**Step 4: 再跑测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.role.RoleCardRepositoryTest" --tests "com.companion.chat.data.role.RoleCardPromptBuilderTest"`

Expected: PASS

### Task 4: 分离设置页入口并落地两个管理页面

**Files:**
- Modify: `app/src/main/java/com/companion/chat/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/companion/chat/ui/navigation/AppNavigation.kt`
- Modify: `app/src/main/java/com/companion/chat/MainActivity.kt`
- Modify: `app/src/main/java/com/companion/chat/ui/settings/CharacterManagementScreen.kt`
- Create: `app/src/main/java/com/companion/chat/ui/settings/SkillsManagementScreen.kt`
- Create: `app/src/main/java/com/companion/chat/ui/settings/RoleManagementViewModel.kt`
- Create: `app/src/main/java/com/companion/chat/ui/settings/SkillsManagementViewModel.kt`
- Create: `app/src/main/java/com/companion/chat/ui/settings/RoleCardEditorDialog.kt`
- Create: `app/src/main/java/com/companion/chat/ui/settings/SkillEditorDialog.kt`
- Test: `app/src/test/java/com/companion/chat/ui/settings/RoleManagementViewModelTest.kt`
- Test: `app/src/test/java/com/companion/chat/ui/settings/SkillsManagementViewModelTest.kt`

**Step 1: 写失败测试**

```kotlin
@Test
fun settings_contains_both_character_and_skills_entries() {
    val items = buildSettingsEntries()

    assertTrue(items.any { it.title == "角色管理" })
    assertTrue(items.any { it.title == "Skills 管理" })
}
```

**Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.ui.settings.RoleManagementViewModelTest" --tests "com.companion.chat.ui.settings.SkillsManagementViewModelTest"`

Expected: FAIL

**Step 3: 写最小实现**

- 设置页新增 `Skills 管理` 入口
- `CharacterManagementScreen` 改为真实角色卡管理页
- 新增独立 `SkillsManagementScreen`
- 两个页面都支持：
  - 当前激活区
  - 用户列表区
  - 添加/编辑
  - 删除确认
  - 空状态
- `SkillsManagementScreen` 额外展示唯一内置 `翻译助手`

**Step 4: 再跑测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.ui.settings.RoleManagementViewModelTest" --tests "com.companion.chat.ui.settings.SkillsManagementViewModelTest"`

Expected: PASS

### Task 5: 接入 ChatViewModel 的角色+skill 双 Prompt 组合

**Files:**
- Modify: `app/src/main/java/com/companion/chat/ui/chat/ChatViewModel.kt`
- Test: `app/src/test/java/com/companion/chat/ui/chat/ChatViewModelRoleSkillSwitchTest.kt`

**Step 1: 写失败测试**

```kotlin
@Test
fun rebuildBasePrompt_combines_default_role_and_skill_prompt() = runTest {
    val viewModel = buildChatViewModel(
        activeRolePrompt = "你是一个温柔陪伴型角色。",
        activeSkillPrompt = "你是一个专业的翻译助手。"
    )

    val prompt = viewModel.debugBaseSystemPrompt()

    assertTrue(prompt.contains("温柔陪伴型角色"))
    assertTrue(prompt.contains("专业的翻译助手"))
}
```

**Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.ui.chat.ChatViewModelRoleSkillSwitchTest"`

Expected: FAIL

**Step 3: 写最小实现**

- 为 `ChatViewModel` 注入：
  - `RoleCardRepository`
  - `SkillRepository`
  - `RoleCardPromptBuilder`
- 新增内部方法：
  - `refreshBaseSystemPrompt()`
  - `rebuildConversationForPromptChange()`
- 新增公开动作：
  - `activateRoleCard(roleId: Long)`
  - `activateSkill(skillId: Long)`
- 组合规则：
  - 默认基础 prompt
  - 激活角色卡 prompt
  - 激活 skill prompt
- 然后继续复用现有 `ContextManager`、`PromptAssembler`、记忆与偏好链路

**Step 4: 再跑测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.ui.chat.ChatViewModelRoleSkillSwitchTest"`

Expected: PASS

### Task 6: 端到端回归与文档收口

**Files:**
- Modify: `jindu.md`
- Verify: `docs/plans/2026-05-13-stage5-role-skill-separation-design.md`
- Verify: `docs/plans/2026-05-13-stage5-role-skill-separation-plan.md`

**Step 1: 运行定向测试**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.skill.*" --tests "com.companion.chat.data.role.*" --tests "com.companion.chat.ui.settings.*" --tests "com.companion.chat.ui.chat.ChatViewModelRoleSkillSwitchTest"`

Expected: PASS

**Step 2: 运行总编译**

Run: `.\gradlew.bat :app:assembleDebug`

Expected: PASS

**Step 3: 手工验收**

- 验证设置页出现“角色管理”和“Skills 管理”两个入口
- 验证角色卡可创建、编辑、删除、激活
- 验证 `翻译助手` 为唯一内置 skill
- 验证可新增自定义 skill
- 验证角色卡与 skill 可同时启用
- 验证切换任一项后当前会话不断开、回复风格发生变化

**Step 4: 更新进度记录**

- 将本轮设计、计划、唯一内置翻译助手、角色卡与 skills 分离方案追加写入 `jindu.md`

**Step 5: 提交**

```bash
git add docs/plans/2026-05-13-stage5-role-skill-separation-design.md docs/plans/2026-05-13-stage5-role-skill-separation-plan.md jindu.md
git commit -m "docs: add stage5 role and skill separation design"
```

