# Stage2 Context Management Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为 CompanionChat 增加可控的上下文窗口管理能力，在长对话时支持滑动窗口、历史摘要、Conversation 重建以及安全降级。

**Architecture:** 在 `ChatViewModel` 与 `LiteRTLMInferenceEngine` 之间增加 `ContextManager`、`PromptAssembler` 与 `SummaryGenerator` 抽象，由应用层统一决定何时压缩、如何构建 prompt、何时重建 Conversation。实现顺序先跑通 `NoOpSummaryGenerator + 重建框架 + 降级路径`，再验证最近消息回放是否可稳定启用。

**Tech Stack:** Kotlin, Android, Jetpack Compose, Coroutines, Room, LiteRT-LM, JUnit

---

### Task 1: 建立阶段二数据模型与配置入口

**Files:**
- Create: `app/src/main/java/com/companion/chat/data/context/ContextWindow.kt`
- Create: `app/src/main/java/com/companion/chat/data/context/ContextSettings.kt`
- Create: `app/src/main/java/com/companion/chat/data/context/ContextConfigRepository.kt`
- Modify: `app/src/main/java/com/companion/chat/ui/chat/ChatViewModel.kt`

**Step 1: 写配置与模型测试**

- 为 `ContextSettings` 默认值和序列化/读取行为设计最小测试
- 验证默认 `retainedRounds = 10`
- 验证阈值相关字段存在

**Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: 新增测试因类未实现失败

**Step 3: 写最小实现**

- 创建 `ContextWindow`
- 创建 `ContextSettings`
- 创建最小 `ContextConfigRepository`
- 在 `ChatViewModel` 中接入读取入口，但暂不改变发送逻辑

**Step 4: 再跑测试**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: 数据模型相关测试通过

### Task 2: 实现 `PromptAssembler`

**Files:**
- Create: `app/src/main/java/com/companion/chat/data/context/PromptAssembler.kt`
- Test: `app/src/test/java/com/companion/chat/data/context/PromptAssemblerTest.kt`

**Step 1: 写失败测试**

- 测试只有基础 prompt 时不拼空段落
- 测试基础 prompt + 偏好 + 摘要时顺序正确
- 测试摘要为空时不出现“之前对话的摘要”

**Step 2: 跑测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.context.PromptAssemblerTest"`
Expected: FAIL

**Step 3: 写最小实现**

- 定义 `PromptAssembler`
- 实现统一 prompt 组装逻辑

**Step 4: 跑测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.context.PromptAssemblerTest"`
Expected: PASS

### Task 3: 实现 `ContextManager` 的核心裁剪逻辑

**Files:**
- Create: `app/src/main/java/com/companion/chat/data/context/ContextManager.kt`
- Create: `app/src/main/java/com/companion/chat/data/context/DefaultContextManager.kt`
- Test: `app/src/test/java/com/companion/chat/data/context/DefaultContextManagerTest.kt`

**Step 1: 写失败测试**

- `shouldCompress()` 在 `<= N*2+10` 时返回 `false`
- `shouldCompress()` 在 `> N*2+10` 时返回 `true`
- `buildContext()` 返回最近 `N` 轮
- 无需摘要时 `historySummary == ""`

**Step 2: 跑测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.context.DefaultContextManagerTest"`
Expected: FAIL

**Step 3: 写最小实现**

- 定义 `ContextManager` 接口
- 实现 `DefaultContextManager`
- 先使用假摘要器或固定空摘要

**Step 4: 跑测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.context.DefaultContextManagerTest"`
Expected: PASS

### Task 4: 增加摘要器抽象与 No-Op 实现

**Files:**
- Create: `app/src/main/java/com/companion/chat/data/context/SummaryGenerator.kt`
- Create: `app/src/main/java/com/companion/chat/data/context/NoOpSummaryGenerator.kt`
- Modify: `app/src/main/java/com/companion/chat/data/context/DefaultContextManager.kt`
- Test: `app/src/test/java/com/companion/chat/data/context/NoOpSummaryGeneratorTest.kt`

**Step 1: 写失败测试**

- `NoOpSummaryGenerator` 返回空字符串
- `DefaultContextManager.compressHistory()` 在摘要器不可用时也返回空字符串

**Step 2: 跑测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.context.NoOpSummaryGeneratorTest"`
Expected: FAIL

**Step 3: 写最小实现**

- 定义摘要器接口
- 增加 `NoOpSummaryGenerator`
- 将 `DefaultContextManager` 改为依赖注入摘要器

**Step 4: 跑测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.companion.chat.data.context.NoOpSummaryGeneratorTest"`
Expected: PASS

### Task 5: 扩展引擎以支持 Conversation 重建

**Files:**
- Modify: `app/src/main/java/com/companion/chat/engine/LiteRTLMInferenceEngine.kt`
- Modify: `app/src/main/java/com/companion/chat/data/engine/InferenceEngine.kt`
- Test: `app/src/test/java/com/companion/chat/engine/LiteRTLMInferenceEngineRebuildPlan.md` 

**Step 1: 先写接口层测试或伪测试计划**

- 明确需要的新增接口：
  - 获取当前配置
  - 重建 conversation
  - 回放消息

**Step 2: 实现最小重建框架**

- 不立即启用真实回放
- 先实现：
  - 关闭旧 conversation
  - 用新 system prompt 创建新 conversation
  - 失败时回滚状态

**Step 3: 编译验证**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: 编译通过

**Step 4: 日志验证**

- 引擎日志能区分：
  - 正常发送
  - 重建
  - 重建失败

### Task 6: 在 `ChatViewModel` 中接入发送前上下文计算

**Files:**
- Modify: `app/src/main/java/com/companion/chat/ui/chat/ChatViewModel.kt`
- Modify: `app/src/main/java/com/companion/chat/data/context/ContextConfigRepository.kt`

**Step 1: 写失败测试或行为清单**

- 发送前能够读取当前设置
- 超过阈值时进入压缩分支
- 未超过阈值时不触发重建

**Step 2: 实现最小接线**

- 在发送链路中接入 `ContextManager`
- 生成 `ContextWindow`
- 必要时调用引擎重建

**Step 3: 编译验证**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: 编译通过

**Step 4: 自查**

- 确认没有破坏当前消息保存和流式输出逻辑

### Task 7: 增加并发保护与降级路径

**Files:**
- Modify: `app/src/main/java/com/companion/chat/ui/chat/ChatViewModel.kt`
- Modify: `app/src/main/java/com/companion/chat/engine/LiteRTLMInferenceEngine.kt`
- Modify: `app/src/main/java/com/companion/chat/data/context/DefaultContextManager.kt`

**Step 1: 写失败测试或场景清单**

- 压缩/重建中再次发送消息不崩溃
- 回放失败时进入降级
- 摘要为空时仍能继续发送

**Step 2: 写最小实现**

- 为重建增加互斥控制
- 为新请求增加取消旧重建或复算逻辑
- 回放异常时直接降级为摘要注入

**Step 3: 编译验证**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: 编译通过

**Step 4: 日志检查**

- 可在日志中看见：
  - 是否触发压缩
  - 是否走回放
  - 是否走降级

### Task 8: 增加设置页中的保留轮数入口

**Files:**
- Modify: `app/src/main/java/com/companion/chat/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/companion/chat/ui/settings/ModelConfigScreen.kt`
- Modify: `app/src/main/java/com/companion/chat/ui/navigation/AppNavigation.kt`
- Modify: `app/src/main/java/com/companion/chat/data/context/ContextConfigRepository.kt`

**Step 1: 写行为测试或手工验收条件**

- 可以修改 `N`
- 修改后持久化
- 修改后 `shouldCompress()` 阈值变化

**Step 2: 写最小实现**

- 增加“上下文窗口大小”设置入口
- 保存用户选择

**Step 3: 编译验证**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: 编译通过

### Task 9: 验证最近消息回放

**Files:**
- Modify: `app/src/main/java/com/companion/chat/engine/LiteRTLMInferenceEngine.kt`
- Check: `app/src/main/java/com/companion/chat/ui/chat/ChatViewModel.kt`

**Step 1: 进行受控回放实验**

- 先只回放最近少量消息
- 观察 LiteRT-LM 的行为与生成结果

**Step 2: 若稳定则启用主方案**

- 打开最近 `N` 轮回放

**Step 3: 若不稳定则保留降级为默认路径**

- 仍满足阶段二可用性
- 把日志中明确标记为降级运行

**Step 4: 编译验证**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: 编译通过

### Task 10: 阶段二整体验证

**Files:**
- Check: `app/src/main/java/com/companion/chat/ui/chat/ChatViewModel.kt`
- Check: `app/src/main/java/com/companion/chat/engine/LiteRTLMInferenceEngine.kt`
- Check: `docs/plans/2026-05-12-stage2-context-management-design.md`

**Step 1: 运行单元测试**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: 上下文管理相关测试通过

**Step 2: 运行编译**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: 成功产出 debug APK

**Step 3: 真机部署**

Run:
- 卸载旧 app
- 安装新 app
- 推送模型

Expected: 应用可正常启动

**Step 4: 真机验收**

- 连续发送 30+ 条消息
- 观察是否触发压缩
- 询问“我们刚才聊了什么”
- 修改 `N=5` 后再次验证阈值变化
- 检查压缩时 UI 是否卡顿

**Step 5: 记录结果**

- 标记阶段二哪些验收项已完成
- 若默认走降级方案，也要明确记录原因与证据
