# LiteRTLMInferenceEngine 重建验证计划

## 当前新增接口

- `getCurrentConfig()`：读取当前引擎配置。
- `rebuildConversation(systemPrompt)`：使用新的 system prompt 重建 `Conversation`。
- `replayMessages(messages)`：使用 LiteRT-LM `ConversationConfig.initialMessages` 进行最近消息回放实验。

## 当前实现策略

- 先创建新的 `Conversation`，创建成功后再关闭旧实例，尽量保留回滚空间。
- 如果新建失败，保留旧 `Conversation` 引用，不切换当前会话对象。
- 最近消息回放优先把 `ChatMessage` 转成 LiteRT-LM `Message` 列表，再用 `initialMessages` 创建新 `Conversation`。
- 回放失败时保留旧 `Conversation`，并记录“降级为摘要注入”的日志。

## 本阶段验证点

- `:app:assembleDebug` 编译通过。
- 日志中能区分“开始重建 / 重建完成 / 重建失败 / 回放成功 / 回放失败降级”。
- 不改变现有 `sendMessageStream()` 的主发送链路。
