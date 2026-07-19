package com.companion.chat.companion

import com.companion.chat.data.context.ContextConfigRepository
import com.companion.chat.data.engine.EngineConfig
import com.companion.chat.data.engine.InferenceState
import com.companion.chat.data.memory.MemoryExtractLoop
import com.companion.chat.data.memory.MemoryRepository
import com.companion.chat.data.model.ChatMessage
import com.companion.chat.data.model.MessageRole
import com.companion.chat.data.preferences.PreferenceMemoryDeriver
import com.companion.chat.data.preferences.PreferenceRepository
import com.companion.chat.data.preferences.SecondEngineManager
import com.companion.chat.data.preferences.SummaryRunResult
import com.companion.chat.data.preferences.UnifiedExtractionParser
import com.companion.chat.data.preferences.UnifiedExtractionPromptBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PreferenceLearningCoordinator(
    private val scope: CoroutineScope,
    private val contextConfigRepository: ContextConfigRepository,
    private val memoryRepository: MemoryRepository,
    private val preferenceRepository: PreferenceRepository,
    private val preferenceMemoryDeriver: PreferenceMemoryDeriver,
    private val unifiedExtractionPromptBuilder: UnifiedExtractionPromptBuilder,
    private val unifiedExtractionParser: UnifiedExtractionParser,
    private val secondEngineManager: SecondEngineManager,
    private val memoryExtractLoop: MemoryExtractLoop,
    private val engineStateProvider: () -> InferenceState,
    private val currentEngineConfigProvider: () -> EngineConfig?,
    private val baseSystemPromptProvider: () -> String,
    private val logger: (String) -> Unit,
    private val roleCardIdProvider: () -> Long? = { null }
) {
    private var delayJob: Job? = null
    /** 每个 session 已判断过的消息 ID，避免重复处理 */
    private val processedMessageIds = mutableMapOf<String, MutableSet<String>>()

    fun cancelRunningSummary() {
        secondEngineManager.cancelRunningSummary()
    }

    fun scheduleAfterIdle(
        sessionIdProvider: () -> String,
        messagesProvider: () -> List<ChatMessage>
    ) {
        delayJob?.cancel()
        delayJob = scope.launch {
            delay(STAGE4_IDLE_DELAY_MILLIS)
            runIfNeeded(
                reason = "发送后静置",
                sessionId = sessionIdProvider(),
                messages = messagesProvider()
            )
        }
    }

    fun triggerNow(
        reason: String,
        sessionId: String,
        messages: List<ChatMessage>
    ) {
        delayJob?.cancel()
        scope.launch {
            runIfNeeded(
                reason = reason,
                sessionId = sessionId,
                messages = messages
            )
        }
    }

    fun release() {
        delayJob?.cancel()
        secondEngineManager.release()
    }

    private suspend fun runIfNeeded(
        reason: String,
        sessionId: String,
        messages: List<ChatMessage>,
        retryAttempt: Int = 0
    ) {
        logger("阶段四开始检查: reason=$reason, sessionId=$sessionId, retryAttempt=$retryAttempt")
        if (!contextConfigRepository.getAutoPreferenceLearningEnabled()) {
            logger("阶段四跳过: 自动学习偏好已关闭, reason=$reason")
            return
        }
        if (sessionId.isBlank()) {
            logger("阶段四跳过: sessionId 为空, reason=$reason")
            return
        }
        if (engineStateProvider() is InferenceState.Generating) {
            logger("阶段四跳过: 前台仍在生成, reason=$reason")
            return
        }

        // 筛选未处理过的消息，最多 MAX_USER_MESSAGES 条用户消息
        val newMessages = selectUnprocessedMessages(sessionId, messages)
        if (newMessages.isEmpty()) {
            logger("阶段四跳过: 没有新消息需要处理, reason=$reason")
            return
        }
        val userMsgCount = newMessages.count { it.role == MessageRole.USER }
        if (userMsgCount < MIN_USER_MESSAGES_FOR_EXTRACTION) {
            logger("阶段四跳过: 新用户消息不足, userMsgCount=$userMsgCount, min=$MIN_USER_MESSAGES_FOR_EXTRACTION, reason=$reason")
            return
        }

        val currentConfig = currentEngineConfigProvider() ?: run {
            logger("阶段四跳过: 无法获取当前引擎配置, reason=$reason")
            return
        }
        logger("阶段四开始: reason=$reason, newMessages=${newMessages.size}, userMsgCount=$userMsgCount")

        // 直接提取记忆（跳过判断步骤——reasoning 模型判断太慢容易超时）
        // maxTokens 提高到 2048：reasoning 模型先思考再输出 JSON，小 token 会导致只输出思考过程
        // fallbackToReasoningContent=false：只要 content 字段的 JSON，不要 reasoning_content 的思考过程
        // jsonMode=true：请求体加 response_format=json_object 强制 JSON 输出
        val extractionConfig = currentConfig.copy(
            systemPrompt = "你是一个信息提取器。只输出 JSON，不输出其他内容。",
            temperature = 0f,
            maxTokens = 2048,
            fallbackToReasoningContent = false,
            jsonMode = true
        )
        val extractionPrompt = unifiedExtractionPromptBuilder.buildPrompt(newMessages)
        logger("阶段四开始提取记忆, userMsgCount=$userMsgCount")
        when (val result = secondEngineManager.runSummaryIfAllowed(extractionConfig, extractionPrompt)) {
            is SummaryRunResult.Completed -> {
                val memoryCount = handleCompletedSummary(
                    result = result,
                    sessionId = sessionId,
                    reason = reason,
                    retryAttempt = retryAttempt
                )
                // 如果模型有输出但解析出 0 条记忆（说明输出不是 JSON），重试而非标记已处理
                if (memoryCount == 0 && result.content.isNotBlank() && !result.content.contains("{") && retryAttempt < MAX_STAGE4_RETRY_COUNT) {
                    logger("阶段四输出非 JSON，重试: retry=${retryAttempt + 1}, outputLen=${result.content.length}")
                    scope.launch {
                        delay(STAGE4_RETRY_DELAY_MILLIS)
                        runIfNeeded(
                            reason = "$reason-JSON重试",
                            sessionId = sessionId,
                            messages = messages,
                            retryAttempt = retryAttempt + 1
                        )
                    }
                } else {
                    markAsProcessed(sessionId, newMessages)
                }
            }
            SummaryRunResult.SkippedPrimaryBusy -> {
                logger("阶段四跳过: 前台繁忙, 不标记已处理, reason=$reason")
            }
            SummaryRunResult.SkippedAlreadyRunning -> {
                logger("阶段四跳过: 后台总结已在运行, 不标记已处理, reason=$reason")
            }
            SummaryRunResult.Cancelled -> {
                logger("阶段四取消, 不标记已处理, reason=$reason")
            }
            SummaryRunResult.TimedOut -> {
                logger("阶段四超时, 不标记已处理, reason=$reason")
            }
            is SummaryRunResult.Failed -> {
                logger("阶段四失败, 标记已处理: reason=$reason, message=${result.message}")
                markAsProcessed(sessionId, newMessages)
            }
        }
    }

    /**
     * 筛选未处理过的消息，最多取 MAX_USER_MESSAGES 条用户消息及其对应的助手回复
     */
    private fun selectUnprocessedMessages(sessionId: String, allMessages: List<ChatMessage>): List<ChatMessage> {
        val processed = processedMessageIds.getOrPut(sessionId) { mutableSetOf() }
        val unprocessed = allMessages.filterNot { it.id in processed }
            .filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
            .filterNot { it.isStreaming }

        if (unprocessed.isEmpty()) return emptyList()

        // 从末尾往前找，取最多 MAX_USER_MESSAGES 条用户消息
        var userCount = 0
        var startIdx = 0
        for (i in unprocessed.indices.reversed()) {
            if (unprocessed[i].role == MessageRole.USER) {
                userCount++
                if (userCount >= MAX_USER_MESSAGES) {
                    startIdx = i
                    break
                }
            }
        }
        return unprocessed.subList(startIdx, unprocessed.size)
    }

    /**
     * 标记消息为已处理
     */
    private fun markAsProcessed(sessionId: String, messages: List<ChatMessage>) {
        val processed = processedMessageIds.getOrPut(sessionId) { mutableSetOf() }
        messages.forEach { processed.add(it.id) }
        // 防止无限增长：只保留最近 200 条
        if (processed.size > 200) {
            val toKeep = processed.toList().takeLast(200).toMutableSet()
            processed.clear()
            processed.addAll(toKeep)
        }
    }

    /**
     * 第一步 prompt：判断对话是否包含值得记忆的内容
     */
    private fun buildJudgmentPrompt(messages: List<ChatMessage>): String {
        val conversationText = messages.joinToString(separator = "\n") { message ->
            val speaker = when (message.role) {
                MessageRole.USER -> "用户"
                MessageRole.ASSISTANT -> "助手"
                MessageRole.SYSTEM -> "系统"
            }
            "[$speaker]: ${message.content}"
        }
        return buildString {
            appendLine("判断以下对话是否包含值得长期记忆的用户信息。")
            appendLine()
            appendLine("记忆类别：")
            appendLine("- preference: 用户喜欢/不喜欢什么")
            appendLine("- habit: 用户的生活习惯、时间规律")
            appendLine("- fact: 用户的个人基本信息（名字、职业等）")
            appendLine("- style: 用户对回答方式的期望")
            appendLine()
            appendLine("不值得记忆：知识问答、临时闲聊、通用话题、一次性行为。")
            appendLine("原则：宁可漏记也不要错记。只在用户明确表达时才判断为 true。")
            appendLine()
            appendLine("只输出 JSON：{\"should_extract\": true/false, \"reason\": \"简要说明\"}")
            appendLine()
            appendLine("对话内容：")
            append(conversationText)
        }
    }

    /**
     * 解析判断结果
     */
    private fun parseShouldExtract(raw: String): Boolean {
        val jsonMatch = Regex("""\{[^{}]*\}""").find(raw)
        if (jsonMatch != null) {
            val trueMatch = Regex(""""should_extract"\s*:\s*(true|false)""", RegexOption.IGNORE_CASE)
                .find(jsonMatch.value)
            if (trueMatch != null) {
                return trueMatch.groupValues[1].equals("true", ignoreCase = true)
            }
        }
        return raw.contains("should_extract", ignoreCase = true) &&
            raw.contains("true", ignoreCase = true)
    }

    private suspend fun handleCompletedSummary(
        result: SummaryRunResult.Completed,
        sessionId: String,
        reason: String,
        retryAttempt: Int
    ): Int {
        val rawPreview = result.content.replace("\n", "\\n").take(300)
        logger("阶段四原始输出: length=${result.content.length}, preview=$rawPreview")

        val extractResult = memoryExtractLoop.execute(
            llmRawOutput = result.content,
            sessionId = sessionId,
            roleCardId = roleCardIdProvider()
        )
        logger("阶段四提取完成: memories=${extractResult.memories.size}, entities=${extractResult.entities.size}, links=${extractResult.links.size}")

        val extractionResult = unifiedExtractionParser.parse(result.content)
        val derivedMemories = preferenceMemoryDeriver.derive(extractionResult.userPreferences)
        if (derivedMemories.isNotEmpty()) {
            logger("阶段四偏好派生记忆: count=${derivedMemories.size}")
            for (mem in derivedMemories) {
                memoryRepository.storeMemory(
                    content = mem.content,
                    category = mem.category,
                    source = mem.source,
                    sessionId = sessionId,
                    roleCardId = roleCardIdProvider()
                )
            }
        }

        val rcId = roleCardIdProvider()
        preferenceRepository.mergePreferences(extractionResult.userPreferences, rcId)
        val confirmedPreferences = if (rcId != null) {
            preferenceRepository.getConfirmedPreferencesForRole(roleCardId = rcId)
        } else {
            preferenceRepository.getConfirmedPreferences()
        }
        val totalMemories = extractResult.memories.size + derivedMemories.size
        logger(
            "阶段四总结完成: reason=$reason, memoryCount=${extractResult.memories.size}, " +
                "derivedCount=${derivedMemories.size}, preferenceCount=${extractionResult.userPreferences.size}, " +
                "confirmed=${confirmedPreferences.size}, sessionId=$sessionId"
        )
        return totalMemories
    }

    private suspend fun retryOrFallback(
        reason: String,
        sessionId: String,
        messages: List<ChatMessage>,
        retryAttempt: Int,
        resultLabel: String
    ) {
        if (retryAttempt < MAX_STAGE4_RETRY_COUNT) {
            logger("阶段四$resultLabel: reason=$reason, retry=${retryAttempt + 1}")
            scope.launch {
                delay(STAGE4_RETRY_DELAY_MILLIS)
                runIfNeeded(
                    reason = "$reason-重试",
                    sessionId = sessionId,
                    messages = messages,
                    retryAttempt = retryAttempt + 1
                )
            }
        } else {
            logger("阶段四$resultLabel: reason=$reason, retry=$retryAttempt")
        }
    }

    private companion object {
        const val STAGE4_IDLE_DELAY_MILLIS = 60 * 1000L
        const val STAGE4_RETRY_DELAY_MILLIS = 3_000L
        const val MAX_STAGE4_RETRY_COUNT = 2
        const val MAX_USER_MESSAGES = 8
        // 提高到 3 条用户消息：不是每次对话都要记忆，减少低质量记忆
        const val MIN_USER_MESSAGES_FOR_EXTRACTION = 3
    }
}
