# Stage3 Memory Context Patch Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为 CompanionChat 完成阶段三补丁，让全部长期记忆常驻进入上下文，并增强 FTS 检索在中文问句和关系类场景下的召回能力。

**Architecture:** 在现有阶段三实现基础上，不重做记忆系统，只新增“常驻长期记忆段”和“增强版动态检索段”。`MemoryRepository` 提供长期记忆读取与动态检索两个入口，`ChatViewModel` 发送前同时构造两段记忆 prompt，`MemoryRetriever` 改为多关键词和问句归一化查询。

**Tech Stack:** Kotlin, Android, Jetpack Compose, Coroutines, Room, SQLite FTS4, JUnit

---

### Task 1: 为常驻长期记忆补仓库接口

**Files:**
- Modify: `app/src/main/java/com/companion/chat/data/memory/MemoryRepository.kt`
- Modify: `app/src/main/java/com/companion/chat/data/local/dao/MemoryDao.kt`
- Test: `app/src/test/java/com/companion/chat/data/memory/MemoryRepositoryPersistentTest.kt`

**Step 1: 写失败测试**

- 测试 `getPersistentMemories()` 只返回 `layer = long_term` 的记忆
- 测试返回结果按 `updatedAt` 倒序

**Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.memory.MemoryRepositoryPersistentTest"`
Expected: FAIL

**Step 3: 写最小实现**

- 在 `MemoryDao` 增加长期记忆查询方法
- 在 `MemoryRepository` 增加 `getPersistentMemories()`

**Step 4: 运行测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.memory.MemoryRepositoryPersistentTest"`
Expected: PASS

### Task 2: 增强 MemoryPromptBuilder 支持常驻段和关系标签

**Files:**
- Modify: `app/src/main/java/com/companion/chat/data/memory/MemoryPromptBuilder.kt`
- Test: `app/src/test/java/com/companion/chat/data/memory/MemoryPromptBuilderTest.kt`

**Step 1: 写失败测试**

- 测试长期记忆段标题为 `长期记忆中的关键信息：`
- 测试 `relationship` 分类显示为 `关系`
- 测试常驻段和动态段都为空时返回空字符串

**Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.memory.MemoryPromptBuilderTest"`
Expected: FAIL

**Step 3: 写最小实现**

- 为 `MemoryPromptBuilder` 增加构造常驻长期记忆段的方法
- 补齐 `relationship -> 关系` 映射

**Step 4: 运行测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.memory.MemoryPromptBuilderTest"`
Expected: PASS

### Task 3: 增强 FTS 检索器

**Files:**
- Modify: `app/src/main/java/com/companion/chat/data/memory/MemoryRetriever.kt`
- Test: `app/src/test/java/com/companion/chat/data/memory/MemoryRetrieverTest.kt`

**Step 1: 写失败测试**

- 测试不再只取最长单词，而是能构造多关键词查询
- 测试会过滤弱词和纯符号
- 测试 `什么关系`、`是谁`、`喜欢什么` 等问句归一化后能命中相应记忆
- 测试查询字符串会正确拼成 FTS 可接受的表达式

**Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.memory.MemoryRetrieverTest"`
Expected: FAIL

**Step 3: 写最小实现**

- 提取多个有效关键词
- 过滤弱词
- 加入中文问句归一化
- 构造增强版 FTS 查询表达式

**Step 4: 运行测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.memory.MemoryRetrieverTest"`
Expected: PASS

### Task 4: 接入“常驻长期记忆 + 动态检索记忆”双段注入

**Files:**
- Modify: `app/src/main/java/com/companion/chat/ui/chat/ChatViewModel.kt`
- Modify: `app/src/main/java/com/companion/chat/data/context/PromptAssembler.kt`
- Modify: `app/src/main/java/com/companion/chat/data/context/ContextWindow.kt`
- Modify: `app/src/main/java/com/companion/chat/data/context/DefaultContextManager.kt`
- Modify: `app/src/main/java/com/companion/chat/data/memory/MemoryRepository.kt`

**Step 1: 写行为清单**

- 发送前总是尝试读取全部长期记忆
- 发送前仍执行动态相关记忆检索
- 两段记忆分别构造后接入 prompt 组装链路

**Step 2: 写最小实现**

- 在 `ChatViewModel` 发送前读取长期记忆
- 构造常驻长期记忆段和动态检索段
- 合并注入到阶段二上下文管理链路

**Step 3: 补日志**

- 增加 `常驻长期记忆注入: count=N`
- 增加 `动态记忆检索成功/为空`

**Step 4: 编译验证**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: PASS

### Task 5: 为常驻长期记忆注入补单测

**Files:**
- Test: `app/src/test/java/com/companion/chat/data/context/PromptAssemblerTest.kt`
- Test: `app/src/test/java/com/companion/chat/data/memory/MemoryRepositoryPersistentTest.kt`

**Step 1: 写失败测试**

- 测试长期记忆段会排在动态记忆段前面
- 测试无动态记忆时长期记忆段仍可单独存在

**Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.context.PromptAssemblerTest" --tests "com.companion.chat.data.memory.MemoryRepositoryPersistentTest"`
Expected: FAIL

**Step 3: 写最小实现**

- 调整测试数据与断言
- 只补为当前补丁需要的最小逻辑

**Step 4: 运行测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.context.PromptAssemblerTest" --tests "com.companion.chat.data.memory.MemoryRepositoryPersistentTest"`
Expected: PASS

### Task 6: 跑补丁回归测试

**Files:**
- Check: `app/src/test/java/com/companion/chat/data/memory/`
- Check: `app/src/test/java/com/companion/chat/data/context/`

**Step 1: 运行记忆相关单测**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.memory.*"`
Expected: PASS

**Step 2: 运行上下文相关单测**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.context.*"`
Expected: PASS

**Step 3: 运行全量单元测试**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: PASS

**Step 4: 运行编译**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: PASS

### Task 7: 真机部署与补丁验收

**Files:**
- Update: `jindu.md`
- Check: `app/build/outputs/apk/debug/app-debug.apk`

**Step 1: 真机部署**

- 安装补丁后的新包
- 启动应用
- 确认模型处于 `Ready`

**Step 2: 验收长期记忆常驻**

- 手动新增一条长期关系或角色设定记忆
- 发送不完全同词的相关问题
- 检查日志是否出现 `常驻长期记忆注入: count=N`

**Step 3: 验收增强 FTS**

- 用事实类或关系类记忆做非完全同词提问
- 检查日志是否出现动态命中记录

**Step 4: 记录结果**

- 将完成项、未完成项、限制和证据写入 `jindu.md`
