# Stage2 Gap Closure Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 以最小闭环方式补齐 CompanionChat 阶段二剩余缺口，加入规则摘要器、真实降级摘要注入，并完成 30+ 条真机长对话与 UI 可操作性验收。

**Architecture:** 保持现有 `ContextManager -> PromptAssembler -> LiteRTLMInferenceEngine` 架构不变，只在摘要器实现、降级 prompt 组装和引擎降级重建入口上做增量扩展。主方案仍优先使用最近消息回放，只有回放失败时才走“历史摘要 + 最近片段”注入的新会话。

**Tech Stack:** Kotlin, Android, Jetpack Compose, Coroutines, Room, LiteRT-LM, JUnit

---

### Task 1: 实现规则摘要器

**Files:**
- Create: `app/src/main/java/com/companion/chat/data/context/RuleBasedSummaryGenerator.kt`
- Test: `app/src/test/java/com/companion/chat/data/context/RuleBasedSummaryGeneratorTest.kt`

**Step 1: 写失败测试**

- 测试非空消息列表时返回非空摘要
- 测试摘要长度不超过 `summaryMaxChars`
- 测试空消息列表返回空字符串

**Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.context.RuleBasedSummaryGeneratorTest"`
Expected: FAIL

**Step 3: 写最小实现**

- 将历史消息格式化为 `用户：...` / `助手：...`
- 过滤空内容
- 截断单条过长内容
- 最终摘要长度限制在 `summaryMaxChars`

**Step 4: 运行测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.context.RuleBasedSummaryGeneratorTest"`
Expected: PASS

### Task 2: 接入规则摘要器到上下文管理

**Files:**
- Modify: `app/src/main/java/com/companion/chat/data/context/DefaultContextManager.kt`
- Modify: `app/src/main/java/com/companion/chat/ui/chat/ChatViewModel.kt`
- Test: `app/src/test/java/com/companion/chat/data/context/DefaultContextManagerTest.kt`

**Step 1: 先写失败测试或补充断言**

- `buildContext()` 在被裁剪历史存在时返回非空 `historySummary`
- `historySummary.length <= summaryMaxChars`

**Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.context.DefaultContextManagerTest"`
Expected: FAIL

**Step 3: 写最小实现**

- 将 `DefaultContextManager` 默认摘要器从 `NoOpSummaryGenerator` 切换为 `RuleBasedSummaryGenerator`
- 确保 `ChatViewModel` 使用新默认行为，不改动主发送链路顺序

**Step 4: 运行测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.context.DefaultContextManagerTest"`
Expected: PASS

### Task 3: 扩展 PromptAssembler 支持降级片段注入

**Files:**
- Modify: `app/src/main/java/com/companion/chat/data/context/PromptAssembler.kt`
- Test: `app/src/test/java/com/companion/chat/data/context/PromptAssemblerTest.kt`

**Step 1: 写失败测试**

- 历史摘要与最近片段同时存在时顺序正确
- 最近片段为空时不出现“最近几轮对话片段”
- 仅有最近片段时不拼空摘要标题

**Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.context.PromptAssemblerTest"`
Expected: FAIL

**Step 3: 写最小实现**

- 为 `PromptAssembler` 增加最近片段参数
- 固定最近片段标题格式
- 保持原有普通 prompt 组装兼容

**Step 4: 运行测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.context.PromptAssemblerTest"`
Expected: PASS

### Task 4: 实现回放失败后的真实降级摘要注入

**Files:**
- Modify: `app/src/main/java/com/companion/chat/engine/LiteRTLMInferenceEngine.kt`
- Modify: `app/src/main/java/com/companion/chat/data/engine/InferenceEngine.kt`
- Modify: `app/src/main/java/com/companion/chat/ui/chat/ChatViewModel.kt`

**Step 1: 先写行为清单**

- 回放失败时不只是恢复空历史会话
- 回放失败时使用“历史摘要 + 最近片段”重建新会话
- 降级后当前用户消息仍可继续发送

**Step 2: 写最小实现**

- 在引擎接口中增加降级重建入口
- 在 `ChatViewModel` 中，当 `replayMessages()` 返回 `false` 时，构造降级 system prompt
- 用新 prompt 调用降级重建方法

**Step 3: 编译验证**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: PASS

**Step 4: 日志验证**

- 日志能区分：
  - 回放成功
  - 回放失败
  - 降级摘要注入成功
  - 降级摘要注入失败

### Task 5: 验证摘要器降级和超时兜底

**Files:**
- Modify: `app/src/test/java/com/companion/chat/data/context/NoOpSummaryGeneratorTest.kt`
- Modify: `app/src/test/java/com/companion/chat/data/context/DefaultContextManagerTest.kt`

**Step 1: 写失败测试**

- 摘要器异常时 `compressHistory()` 返回空字符串
- 摘要器超时时 `compressHistory()` 返回空字符串

**Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.context.DefaultContextManagerTest"`
Expected: FAIL

**Step 3: 写最小实现**

- 使用抛异常的假摘要器验证兜底
- 使用延迟摘要器验证 `withTimeoutOrNull` 行为

**Step 4: 运行测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.context.DefaultContextManagerTest"`
Expected: PASS

### Task 6: 重新做完整单测与编译验证

**Files:**
- Check: `app/src/test/java/com/companion/chat/data/context/`
- Check: `app/src/main/java/com/companion/chat/`

**Step 1: 运行上下文相关单测**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.context.*"`
Expected: PASS

**Step 2: 运行全量单元测试**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: PASS

**Step 3: 运行编译**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: PASS

### Task 7: 重新部署到真机

**Files:**
- Check: `app/build/outputs/apk/debug/app-debug.apk`
- Check: `D:\Desktop\phone\models\gemma-4-E2B-it.litertlm`

**Step 1: 卸载旧 app**

Run: `adb uninstall com.companion.chat`
Expected: SUCCESS

**Step 2: 安装新 app**

Run: `adb push + pm install -r -t --user 0`
Expected: SUCCESS（小米弹窗需人工确认）

**Step 3: 启动 app 并推送模型**

Run: `adb shell am start -n com.companion.chat/.MainActivity`
Expected: 应用启动成功并创建目录

Run: `adb push model -> /sdcard/Android/data/com.companion.chat/files/models/`
Expected: 模型推送成功

### Task 8: 完成 30+ 条真机长对话专项

**Files:**
- Check: `app/src/main/java/com/companion/chat/ui/chat/ChatViewModel.kt`
- Check: `app/src/main/java/com/companion/chat/engine/LiteRTLMInferenceEngine.kt`
- Update: `jindu.md`

**Step 1: 设置 `N=5`**

- 在设置页中把上下文窗口大小调为 `5`

**Step 2: 执行长对话**

- 在同一会话连续发送 `30+` 条短消息
- 至少发一次“我们刚才聊了什么”

**Step 3: 抓日志验证**

- 查看是否触发压缩
- 查看是否走回放或降级
- 查看追问结果是否合理

**Step 4: 记录结果**

- 把触发阈值、触发消息数、最终分支和追问结果写入 `jindu.md`

### Task 9: 完成 UI 可操作性专项验收

**Files:**
- Update: `jindu.md`

**Step 1: 在压缩前后执行人工操作**

- 滑动消息列表
- 切到其他页面
- 返回聊天页

**Step 2: 记录现象**

- 是否出现 ANR
- 是否出现明显卡死
- 是否还能继续发送

**Step 3: 写入进度记录**

- 将 UI 验收结论写入 `jindu.md`

### Task 10: 对照阶段二清单重新核对

**Files:**
- Check: `d:\Desktop\phone\COMPANIONCHAT_TEST_CHECKLIST.md`
- Check: `docs/plans/2026-05-12-stage2-context-management-design.md`
- Update: `jindu.md`

**Step 1: 逐条核对 `2.1` ~ `2.4`**

- 标出已完成项
- 标出仍存在证据缺口的项

**Step 2: 输出阶段二结论**

- 明确“主功能完成 / 清单完成度 / 剩余风险”

**Step 3: 更新 `jindu.md`**

- 写入最终阶段二核对结果
