# Memory User Perspective Fix Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 修复记忆注入后的主语归属歧义，确保模型将记忆默认理解为用户信息而不是助手自身信息。

**Architecture:** 保持现有数据库、检索和上下文链路不变，只在 prompt 组装层补充两级约束。`PromptAssembler` 负责插入统一的记忆解释规则，`MemoryPromptBuilder` 负责在记忆段内补充“这些内容属于用户”的局部说明。

**Tech Stack:** Kotlin, Android, Jetpack Compose, LiteRT-LM, JUnit

---

### Task 1: 为记忆视角约束补 failing tests

**Files:**
- Modify: `app/src/test/java/com/companion/chat/data/memory/MemoryPromptBuilderTest.kt`
- Modify: `app/src/test/java/com/companion/chat/data/context/PromptAssemblerTest.kt`

**Step 1: 补 MemoryPromptBuilder 断言**

- 测试动态记忆段包含“以下内容均为用户本人的记忆，不代表助手自身。”
- 测试长期记忆段同样包含这行局部说明

**Step 2: 补 PromptAssembler 断言**

- 测试存在任一记忆段时插入“记忆解释规则”
- 测试该规则位于用户偏好之后、记忆段之前
- 测试无记忆段时不插入该规则

**Step 3: 运行定向测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.memory.MemoryPromptBuilderTest" --tests "com.companion.chat.data.context.PromptAssemblerTest"`
Expected: FAIL

### Task 2: 实现 prompt 层最小修复

**Files:**
- Modify: `app/src/main/java/com/companion/chat/data/memory/MemoryPromptBuilder.kt`
- Modify: `app/src/main/java/com/companion/chat/data/context/PromptAssembler.kt`

**Step 1: 修改 MemoryPromptBuilder**

- 为记忆段增加局部说明
- 保持原有标题、列表顺序和分类映射不变

**Step 2: 修改 PromptAssembler**

- 当存在长期记忆段或动态记忆段时，插入统一“记忆解释规则”
- 保持无记忆场景输出不变
- 保持整体顺序：基础 prompt -> 用户偏好 -> 记忆解释规则 -> 长期记忆段 -> 动态记忆段 -> 摘要/最近片段

**Step 3: 运行定向测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.memory.MemoryPromptBuilderTest" --tests "com.companion.chat.data.context.PromptAssemblerTest"`
Expected: PASS

### Task 3: 做最小回归并记录进度

**Files:**
- Update: `jindu.md`

**Step 1: 运行 prompt 相关测试**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.memory.MemoryPromptBuilderTest" --tests "com.companion.chat.data.context.PromptAssemblerTest" --tests "com.companion.chat.data.context.DefaultContextManagerTest"`
Expected: PASS

**Step 2: 运行全量单元测试**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: PASS

**Step 3: 运行编译**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: PASS

**Step 4: 更新 `jindu.md`**

- 记录问题现象、根因和本次修复方式
- 记录已通过的测试与当前真机结论
