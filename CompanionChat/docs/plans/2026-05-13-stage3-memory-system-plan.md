# Stage3 Memory System Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为 CompanionChat 完成阶段三记忆系统，实现规则提取、Room 写入、FTS 检索、记忆注入、生命周期管理和记忆管理 UI。

**Architecture:** 以现有 Room 表和 DAO 为基础，新增 `MemoryExtractor`、`MemoryRepository`、`MemoryRetriever`、`MemoryPromptBuilder` 与 `MemoryLifecycleManager`。聊天链路通过仓库层接入“发送前检索注入 + 发送后提取写入”，`MemoryScreen` 通过 `MemoryViewModel` 管理列表和手动操作。

**Tech Stack:** Kotlin, Android, Jetpack Compose, Coroutines, Room, SQLite FTS4, JUnit

---

### Task 1: 实现规则提取器

**Files:**
- Create: `app/src/main/java/com/companion/chat/data/memory/ExtractedMemory.kt`
- Create: `app/src/main/java/com/companion/chat/data/memory/MemoryExtractor.kt`
- Create: `app/src/main/java/com/companion/chat/data/memory/RuleBasedMemoryExtractor.kt`
- Test: `app/src/test/java/com/companion/chat/data/memory/RuleBasedMemoryExtractorTest.kt`

**Step 1: 写失败测试**

- 测试 `记住我叫小明` 提取出 `fact`：`用户叫小明`
- 测试 `我喜欢吃火锅` 提取出 `preference`：`用户喜欢吃火锅`
- 测试 `我住在北京` 提取出 `fact`：`用户住在北京`
- 测试 `不要再说这个了` 提取出 `preference`：`不要再说这个`
- 测试普通消息不提取

**Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.memory.RuleBasedMemoryExtractorTest"`
Expected: FAIL

**Step 3: 写最小实现**

- 定义 `ExtractedMemory`
- 定义 `MemoryExtractor`
- 用规则匹配完成最小提取逻辑

**Step 4: 运行测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.memory.RuleBasedMemoryExtractorTest"`
Expected: PASS

### Task 2: 实现记忆仓库与自动写入

**Files:**
- Create: `app/src/main/java/com/companion/chat/data/memory/MemoryRepository.kt`
- Test: `app/src/test/java/com/companion/chat/data/memory/MemoryRepositoryWriteTest.kt`
- Modify: `app/src/main/java/com/companion/chat/data/local/dao/MemoryDao.kt`

**Step 1: 写失败测试**

- 自动提取结果写入 `memories` 表
- 写入后的 `layer = short_term`
- `expiresAt = now + 7天`
- `source = rule_extractor`

**Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.memory.MemoryRepositoryWriteTest"`
Expected: FAIL

**Step 3: 写最小实现**

- 用 `MemoryDao` 写入规则提取结果
- 为时间戳和过期时间提供统一构造逻辑
- 为后续 UI 保留手动写入入口

**Step 4: 运行测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.memory.MemoryRepositoryWriteTest"`
Expected: PASS

### Task 3: 实现 FTS 检索与排序

**Files:**
- Create: `app/src/main/java/com/companion/chat/data/memory/MemoryRetriever.kt`
- Test: `app/src/test/java/com/companion/chat/data/memory/MemoryRetrieverTest.kt`
- Modify: `app/src/main/java/com/companion/chat/data/memory/MemoryRepository.kt`

**Step 1: 写失败测试**

- 插入匹配记忆后可通过 FTS 命中
- 空消息或过短消息不触发检索
- 最多返回 5 条
- 长期记忆排在短期记忆之前
- 检索命中后 `referenceCount` 自动增加

**Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.memory.MemoryRetrieverTest"`
Expected: FAIL

**Step 3: 写最小实现**

- 封装 FTS 查询构造
- 增加排序与结果裁剪
- 命中后递增引用计数

**Step 4: 运行测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.memory.MemoryRetrieverTest"`
Expected: PASS

### Task 4: 实现记忆 Prompt 构造器

**Files:**
- Create: `app/src/main/java/com/companion/chat/data/memory/MemoryPromptBuilder.kt`
- Test: `app/src/test/java/com/companion/chat/data/memory/MemoryPromptBuilderTest.kt`
- Modify: `app/src/main/java/com/companion/chat/data/context/PromptAssembler.kt`

**Step 1: 写失败测试**

- 有相关记忆时生成固定标题和列表格式
- 无相关记忆时不拼空段落
- 分类标签格式符合 `- [事实] xxx` / `- [偏好] xxx`

**Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.memory.MemoryPromptBuilderTest"`
Expected: FAIL

**Step 3: 写最小实现**

- 创建 `MemoryPromptBuilder`
- 让 `PromptAssembler` 能接入记忆段落

**Step 4: 运行测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.memory.MemoryPromptBuilderTest"`
Expected: PASS

### Task 5: 接入聊天发送前检索与发送后提取

**Files:**
- Modify: `app/src/main/java/com/companion/chat/ui/chat/ChatViewModel.kt`
- Modify: `app/src/main/java/com/companion/chat/data/context/ContextWindow.kt`
- Modify: `app/src/main/java/com/companion/chat/data/context/DefaultContextManager.kt`
- Modify: `app/src/main/java/com/companion/chat/data/memory/MemoryRepository.kt`

**Step 1: 写行为清单**

- 发送前根据当前用户消息检索相关记忆
- 记忆 prompt 接入现有 prompt 组装链路
- 用户消息写入会话后触发规则提取和自动落库

**Step 2: 写最小实现**

- 在 `ChatViewModel` 发送前调用检索
- 将记忆注入 prompt 作为单独段落传入
- 在发送后触发规则提取和数据库写入

**Step 3: 编译验证**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: PASS

**Step 4: 自查**

- 确认阶段二上下文管理链路未被破坏
- 确认无记忆时发送逻辑保持不变

### Task 6: 实现生命周期管理

**Files:**
- Create: `app/src/main/java/com/companion/chat/data/memory/MemoryLifecycleManager.kt`
- Modify: `app/src/main/java/com/companion/chat/CompanionChatApplication.kt`
- Test: `app/src/test/java/com/companion/chat/data/memory/MemoryLifecycleManagerTest.kt`

**Step 1: 写失败测试**

- 启动时清理过期短期记忆
- 启动时将 `referenceCount >= 3` 的短期记忆提升为长期记忆
- 长期记忆不受清理影响

**Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.memory.MemoryLifecycleManagerTest"`
Expected: FAIL

**Step 3: 写最小实现**

- 实现生命周期管理器
- 在 `Application` 启动时调用清理和提升逻辑

**Step 4: 运行测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.memory.MemoryLifecycleManagerTest"`
Expected: PASS

### Task 7: 实现 MemoryViewModel

**Files:**
- Create: `app/src/main/java/com/companion/chat/ui/memory/MemoryViewModel.kt`
- Create: `app/src/main/java/com/companion/chat/ui/memory/MemoryUiState.kt`
- Modify: `app/src/main/java/com/companion/chat/data/memory/MemoryRepository.kt`
- Test: `app/src/test/java/com/companion/chat/ui/memory/MemoryViewModelTest.kt`

**Step 1: 写失败测试**

- 能加载全部记忆
- 能按分类筛选
- 手动新增记忆直接写为 `long_term`
- 可删除和提升短期记忆

**Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.ui.memory.MemoryViewModelTest"`
Expected: FAIL

**Step 3: 写最小实现**

- 建立 `MemoryViewModel`
- 增加筛选、增删改、提升操作

**Step 4: 运行测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.ui.memory.MemoryViewModelTest"`
Expected: PASS

### Task 8: 实现 MemoryScreen 最小可用版

**Files:**
- Modify: `app/src/main/java/com/companion/chat/ui/memory/MemoryScreen.kt`
- Modify: `app/src/main/java/com/companion/chat/MainActivity.kt`
- Modify: `app/src/main/java/com/companion/chat/ui/settings/SettingsScreen.kt`

**Step 1: 写行为清单**

- 列表展示内容、分类、层级、时间
- 空状态文案正确
- 分类筛选可用
- 手动添加、编辑、删除、提升可操作

**Step 2: 写最小实现**

- 把占位页替换为真实管理页
- 接入 `MemoryViewModel`
- 保持设置页和底部导航跳转可用

**Step 3: 编译验证**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: PASS

### Task 9: 做阶段三单测与编译回归

**Files:**
- Check: `app/src/test/java/com/companion/chat/data/memory/`
- Check: `app/src/test/java/com/companion/chat/ui/memory/`

**Step 1: 运行记忆相关单测**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.memory.*"`
Expected: PASS

**Step 2: 运行 UI 相关单测**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.ui.memory.*"`
Expected: PASS

**Step 3: 运行全量单元测试**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: PASS

**Step 4: 运行编译**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: PASS

### Task 10: 真机部署与阶段三集成验收

**Files:**
- Update: `jindu.md`
- Check: `app/build/outputs/apk/debug/app-debug.apk`

**Step 1: 真机部署**

- 卸载旧 app
- 安装新 app
- 推送模型

**Step 2: 验收 3.1**

- 发送 `记住我叫小明`
- 验证数据库或记忆页中出现对应短期记忆

**Step 3: 验收 3.2 和 3.4**

- 发送与记忆相关的问题，例如 `我叫什么`
- 验证模型能通过注入记忆回答

**Step 4: 验收 3.3**

- 插入可提升短期记忆或过期短期记忆
- 重启应用验证提升和清理

**Step 5: 验收 3.5**

- 在记忆页执行筛选、手动新增、编辑、删除、提升
- 确认设置页入口和底部导航都可正常进入

**Step 6: 记录结果**

- 将已完成验收项、未完成项和证据写入 `jindu.md`
