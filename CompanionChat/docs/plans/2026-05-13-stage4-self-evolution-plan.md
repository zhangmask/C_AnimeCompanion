# Stage4 Self Evolution Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为 CompanionChat 完成阶段四自进化闭环，实现 Engine-B 后台偏好总结、Room 合并存储、主 prompt 注入和设置开关控制。

**Architecture:** 复用现有 `LiteRTLMInferenceEngine` 作为 Engine-B 的底层实现，新建 `SecondEngineManager` 管理第二引擎生命周期与抢占规则。偏好链路拆为“总结 prompt 构造 -> JSON 解析 -> Repository 合并写入 -> PromptAssembler 注入”，触发调度先挂在 `ChatViewModel` 上以最小改动打通闭环。

**Tech Stack:** Kotlin, Android, Jetpack Compose, Coroutines, Room, SharedPreferences, JUnit

---

### Task 1: 补阶段四设置项与持久化

**Files:**
- Modify: `app/src/main/java/com/companion/chat/data/context/ContextConfigRepository.kt`
- Modify: `app/src/main/java/com/companion/chat/ui/settings/SettingsScreen.kt`
- Test: `app/src/test/java/com/companion/chat/data/context/ContextConfigRepositoryTest.kt`

**Step 1: 写失败测试**

- 断言默认 `autoPreferenceLearningEnabled = true`
- 断言关闭后重新读取仍为 `false`
- 断言不影响已有 `retainedRounds` 读取

**Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.context.ContextConfigRepositoryTest"`
Expected: FAIL

**Step 3: 写最小实现**

- 在 `ContextConfigRepository` 增加 `KEY_AUTO_PREFERENCE_LEARNING_ENABLED`
- 增加 `getAutoPreferenceLearningEnabled()` 和 `updateAutoPreferenceLearningEnabled(enabled: Boolean)`
- 在 `SettingsScreen` 增加“自动学习偏好”开关项，默认读取仓库值

**Step 4: 运行测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.context.ContextConfigRepositoryTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/companion/chat/data/context/ContextConfigRepository.kt app/src/main/java/com/companion/chat/ui/settings/SettingsScreen.kt app/src/test/java/com/companion/chat/data/context/ContextConfigRepositoryTest.kt
git commit -m "feat: add stage4 preference learning setting"
```

### Task 2: 实现偏好总结 prompt builder 与 parser

**Files:**
- Create: `app/src/main/java/com/companion/chat/data/preferences/PreferenceSummaryPromptBuilder.kt`
- Create: `app/src/main/java/com/companion/chat/data/preferences/PreferenceSummaryParser.kt`
- Create: `app/src/main/java/com/companion/chat/data/preferences/ExtractedPreference.kt`
- Test: `app/src/test/java/com/companion/chat/data/preferences/PreferenceSummaryPromptBuilderTest.kt`
- Test: `app/src/test/java/com/companion/chat/data/preferences/PreferenceSummaryParserTest.kt`

**Step 1: 写失败测试**

- prompt 只包含最近 5 轮对话
- prompt 中固定列出 `name/style/interest/habit/other`
- 合法 JSON 数组可解析为结构化对象
- 乱码、非 JSON、空字符串返回空列表
- `[]` 返回空列表但不报错

**Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.preferences.PreferenceSummaryPromptBuilderTest" --tests "com.companion.chat.data.preferences.PreferenceSummaryParserTest"`
Expected: FAIL

**Step 3: 写最小实现**

- 定义 `ExtractedPreference(category, content)`
- `PreferenceSummaryPromptBuilder` 负责格式化最近 5 轮消息
- `PreferenceSummaryParser` 使用最小 JSON 解析逻辑，过滤空字段和未知类别

**Step 4: 运行测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.preferences.PreferenceSummaryPromptBuilderTest" --tests "com.companion.chat.data.preferences.PreferenceSummaryParserTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/companion/chat/data/preferences/ app/src/test/java/com/companion/chat/data/preferences/
git commit -m "feat: add stage4 preference summary parser"
```

### Task 3: 实现偏好仓库合并逻辑

**Files:**
- Create: `app/src/main/java/com/companion/chat/data/preferences/PreferenceRepository.kt`
- Modify: `app/src/main/java/com/companion/chat/data/local/dao/PreferenceDao.kt`
- Modify: `app/src/main/java/com/companion/chat/data/local/entity/UserPreference.kt`
- Test: `app/src/test/java/com/companion/chat/data/preferences/PreferenceRepositoryTest.kt`

**Step 1: 写失败测试**

- 新偏好写入 `confidence = 1`
- 相同 `category + normalizedContent` 再次出现时 `confidence + 1`
- `getConfirmedPreferences()` 仅返回 `confidence >= 3`
- 模型总结为空列表时不写入任何偏好

**Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.preferences.PreferenceRepositoryTest"`
Expected: FAIL

**Step 3: 写最小实现**

- 封装 `mergePreferences(preferences: List<ExtractedPreference>)`
- 增加内容规范化方法，统一空白、大小写和首尾标点
- 复用 `PreferenceDao.findExactMatch()` 与 `getConfirmed()`

**Step 4: 运行测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.preferences.PreferenceRepositoryTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/companion/chat/data/preferences/PreferenceRepository.kt app/src/main/java/com/companion/chat/data/local/dao/PreferenceDao.kt app/src/test/java/com/companion/chat/data/preferences/PreferenceRepositoryTest.kt
git commit -m "feat: add stage4 preference repository"
```

### Task 4: 实现 SecondEngineManager

**Files:**
- Create: `app/src/main/java/com/companion/chat/data/preferences/SecondEngineManager.kt`
- Modify: `app/src/main/java/com/companion/chat/data/engine/InferenceEngine.kt`
- Modify: `app/src/main/java/com/companion/chat/engine/LiteRTLMInferenceEngine.kt`
- Test: `app/src/test/java/com/companion/chat/data/preferences/SecondEngineManagerTest.kt`

**Step 1: 写失败测试**

- Engine-B 能创建独立 `LiteRTLMInferenceEngine`
- Engine-A 为 `Generating` 时不启动
- Engine-B 运行中被取消后返回取消结果而不是异常
- 总结超时 60 秒后被硬中断
- 完成后一定执行 `cancel/release`

**Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.preferences.SecondEngineManagerTest"`
Expected: FAIL

**Step 3: 写最小实现**

- `SecondEngineManager` 注入 `Context` 和 Engine 工厂
- 提供 `runSummaryIfAllowed(...)`、`cancelRunningSummary()`、`release()`
- 用 `withTimeout(60_000)` 包裹后台总结
- 使用独立引擎实例，禁止共享 Conversation

**Step 4: 运行测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.preferences.SecondEngineManagerTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/companion/chat/data/preferences/SecondEngineManager.kt app/src/main/java/com/companion/chat/data/engine/InferenceEngine.kt app/src/main/java/com/companion/chat/engine/LiteRTLMInferenceEngine.kt app/src/test/java/com/companion/chat/data/preferences/SecondEngineManagerTest.kt
git commit -m "feat: add stage4 second engine manager"
```

### Task 5: 接入 ChatViewModel 的阶段四触发调度

**Files:**
- Modify: `app/src/main/java/com/companion/chat/ui/chat/ChatViewModel.kt`
- Modify: `app/src/main/java/com/companion/chat/MainActivity.kt`
- Modify: `app/src/main/java/com/companion/chat/ui/chat/ChatScreen.kt`
- Test: `app/src/test/java/com/companion/chat/ui/chat/ChatViewModelStage4Test.kt`

**Step 1: 写失败测试**

- 用户 3 分钟无新消息时触发总结
- 切换会话时触发当前会话总结
- 仅 1-2 轮消息时不触发
- 距离上次总结 < 5 分钟时不触发
- 用户再次发送消息时会先取消 Engine-B

**Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.ui.chat.ChatViewModelStage4Test"`
Expected: FAIL

**Step 3: 写最小实现**

- 在 `ChatViewModel` 中维护最近消息时间和最近总结时间
- 发送完成后启动延迟任务，3 分钟后检查触发条件
- 会话切换与进入后台时复用同一触发入口
- 新消息发送前先调用 `secondEngineManager.cancelRunningSummary()`

**Step 4: 运行测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.ui.chat.ChatViewModelStage4Test"`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/companion/chat/ui/chat/ChatViewModel.kt app/src/main/java/com/companion/chat/MainActivity.kt app/src/main/java/com/companion/chat/ui/chat/ChatScreen.kt app/src/test/java/com/companion/chat/ui/chat/ChatViewModelStage4Test.kt
git commit -m "feat: wire stage4 preference learning triggers"
```

### Task 6: 实现偏好 prompt 注入

**Files:**
- Modify: `app/src/main/java/com/companion/chat/data/context/PromptAssembler.kt`
- Modify: `app/src/main/java/com/companion/chat/data/context/DefaultContextManager.kt`
- Modify: `app/src/main/java/com/companion/chat/ui/chat/ChatViewModel.kt`
- Test: `app/src/test/java/com/companion/chat/data/context/PromptAssemblerTest.kt`
- Test: `app/src/test/java/com/companion/chat/data/context/DefaultContextManagerTest.kt`

**Step 1: 写失败测试**

- confirmed 偏好存在时追加“关于当前用户的已知信息”段
- 仅注入 `confidence >= 3` 的偏好
- 无 confirmed 偏好时不拼空段落
- 与现有记忆段、摘要段共存时顺序稳定

**Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.context.PromptAssemblerTest" --tests "com.companion.chat.data.context.DefaultContextManagerTest"`
Expected: FAIL

**Step 3: 写最小实现**

- 在 `ChatViewModel` 发送前读取 `PreferenceRepository.getConfirmedPreferences()`
- 用固定格式拼成偏好段
- 让 `PromptAssembler` 和 `DefaultContextManager` 接纳新段落参数

**Step 4: 运行测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.context.PromptAssemblerTest" --tests "com.companion.chat.data.context.DefaultContextManagerTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/companion/chat/data/context/PromptAssembler.kt app/src/main/java/com/companion/chat/data/context/DefaultContextManager.kt app/src/main/java/com/companion/chat/ui/chat/ChatViewModel.kt app/src/test/java/com/companion/chat/data/context/PromptAssemblerTest.kt app/src/test/java/com/companion/chat/data/context/DefaultContextManagerTest.kt
git commit -m "feat: inject confirmed user preferences into prompt"
```

### Task 7: 保证规则提取降级与开关控制

**Files:**
- Modify: `app/src/main/java/com/companion/chat/data/memory/RuleBasedMemoryExtractor.kt`
- Modify: `app/src/main/java/com/companion/chat/ui/chat/ChatViewModel.kt`
- Modify: `app/src/main/java/com/companion/chat/data/preferences/SecondEngineManager.kt`
- Test: `app/src/test/java/com/companion/chat/ui/chat/ChatViewModelStage4FallbackTest.kt`

**Step 1: 写失败测试**

- 关闭自动学习偏好后不触发 Engine-B
- Engine-B 失败时规则提取链路不受影响
- “记住我叫小明”仍进入 `memories` 而不是 `user_preferences`

**Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.ui.chat.ChatViewModelStage4FallbackTest"`
Expected: FAIL

**Step 3: 写最小实现**

- 在所有阶段四触发入口先检查开关
- Engine-B 失败时仅记录日志，不中断现有消息发送与记忆写入
- 明确规则提取仍走阶段三记忆仓库

**Step 4: 运行测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.ui.chat.ChatViewModelStage4FallbackTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/companion/chat/ui/chat/ChatViewModel.kt app/src/main/java/com/companion/chat/data/preferences/SecondEngineManager.kt app/src/test/java/com/companion/chat/ui/chat/ChatViewModelStage4FallbackTest.kt
git commit -m "feat: keep stage4 fallback and toggle behavior safe"
```

### Task 8: 做阶段四回归、编译和真机验收

**Files:**
- Check: `app/src/test/java/com/companion/chat/data/preferences/`
- Check: `app/src/test/java/com/companion/chat/ui/chat/`
- Update: `jindu.md`

**Step 1: 运行阶段四后端单测**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.preferences.*"`
Expected: PASS

**Step 2: 运行聊天链路相关单测**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.ui.chat.*" --tests "com.companion.chat.data.context.*"`
Expected: PASS

**Step 3: 运行全量单测**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: PASS

**Step 4: 运行编译**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: PASS

**Step 5: 真机验收**

- 卸载旧 app
- 安装新 app
- 推送模型
- 打开“自动学习偏好”后，准备一组风格偏好对话并等待触发
- 关闭开关后重复相同流程，验证日志中不再触发总结
- 手动插入 `confidence >= 3` 偏好，验证回答体现 prompt 注入

**Step 6: 记录结果**

- 将已完成验收项、受设备条件限制的项和日志证据写入 `jindu.md`

**Step 7: Commit**

```bash
git add jindu.md
git commit -m "test: verify stage4 self evolution flow"
```
