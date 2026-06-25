package com.companion.chat.companion

import com.companion.chat.data.context.ContextConfigRepository
import com.companion.chat.data.engine.EngineConfig
import com.companion.chat.data.engine.InferenceState
import com.companion.chat.data.memory.ExtractedMemory
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
    private val memoryExtractLoop: MemoryExtractLoop,  // [v2 新增] 统一提取循环
    private val engineStateProvider: () -> InferenceState,
    private val currentEngineConfigProvider: () -> EngineConfig?,
    private val baseSystemPromptProvider: () -> String,
    private val logger: (String) -> Unit,
    private val roleCardIdProvider: () -> Long? = { null }
) {
    private var delayJob: Job? = null
    private val lastSummaryTimestamps = mutableMapOf<String, Long>()

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
        if (!contextConfigRepository.getAutoPreferenceLearningEnabled()) {
            logger("阶段四跳过: 自动学习偏好已关闭, reason=$reason")
            return
        }
        if (sessionId.isBlank()) {
            return
        }
        if (engineStateProvider() is InferenceState.Generating) {
            logger("阶段四跳过: 前台仍在生成, reason=$reason")
            return
        }

        val stableMessages = messages.filterNot { it.isStreaming }
            .filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
        if (stableMessages.size < MIN_STAGE4_MESSAGE_COUNT) {
            logger("阶段四跳过: 对话轮数不足, reason=$reason, messageCount=${stableMessages.size}")
            return
        }

        val now = System.currentTimeMillis()
        val lastSummaryAt = lastSummaryTimestamps[sessionId] ?: 0L
        if (now - lastSummaryAt < STAGE4_THROTTLE_MILLIS) {
            logger("阶段四跳过: 节流中, reason=$reason")
            return
        }

        val currentConfig = currentEngineConfigProvider() ?: return
        val summaryConfig = currentConfig.copy(systemPrompt = baseSystemPromptProvider())
        val prompt = unifiedExtractionPromptBuilder.buildPrompt(stableMessages)
        when (val result = secondEngineManager.runSummaryIfAllowed(summaryConfig, prompt)) {
            is SummaryRunResult.Completed -> {
                handleCompletedSummary(
                    result = result,
                    stableMessages = stableMessages,
                    sessionId = sessionId,
                    now = now,
                    reason = reason,
                    retryAttempt = retryAttempt
                )
            }
            SummaryRunResult.SkippedPrimaryBusy -> {
                logger("阶段四跳过: 前台繁忙, reason=$reason")
            }
            SummaryRunResult.SkippedAlreadyRunning -> {
                logger("阶段四跳过: 后台总结已在运行, reason=$reason")
            }
            SummaryRunResult.Cancelled -> {
                retryOrFallback(
                    reason = reason,
                    sessionId = sessionId,
                    stableMessages = stableMessages,
                    retryAttempt = retryAttempt,
                    resultLabel = "取消"
                )
            }
            SummaryRunResult.TimedOut -> {
                retryOrFallback(
                    reason = reason,
                    sessionId = sessionId,
                    stableMessages = stableMessages,
                    retryAttempt = retryAttempt,
                    resultLabel = "超时"
                )
            }
            is SummaryRunResult.Failed -> {

                logger("阶段四失败: reason=$reason, message=${result.message}")
            }
        }
    }

    private suspend fun handleCompletedSummary(
        result: SummaryRunResult.Completed,
        stableMessages: List<ChatMessage>,
        sessionId: String,
        now: Long,
        reason: String,
        retryAttempt: Int
    ) {
        val rawSummaryPreview = result.content.replace("\n", "\\n").take(300)
        logger("阶段四原始输出: preview=$rawSummaryPreview")

        // 使用 MemoryExtractLoop 统一提取 memories + entities + links + metaMemories
        val extractResult = memoryExtractLoop.execute(
            llmRawOutput = result.content,
            sessionId = sessionId,
            roleCardId = roleCardIdProvider()
        )
        logger("阶段四提取完成: memories=${extractResult.memories.size}, entities=${extractResult.entities.size}, links=${extractResult.links.size}")

        // 兼容旧逻辑：从 user_preferences 派生记忆
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

        // 合并偏好
        val rcId = roleCardIdProvider()
        preferenceRepository.mergePreferences(extractionResult.userPreferences, rcId)
        val confirmedPreferences = if (rcId != null) {
            preferenceRepository.getConfirmedPreferencesForRole(roleCardId = rcId)
        } else {
            preferenceRepository.getConfirmedPreferences()
        }
        logger(
            "阶段四偏好合并完成: merged=${extractionResult.userPreferences.size}, " +
                "confirmed=${confirmedPreferences.size}, retryAttempt=$retryAttempt"
        )
        lastSummaryTimestamps[sessionId] = now
        logger(
            "阶段四总结完成: reason=$reason, memoryCount=${extractResult.memories.size}, " +
                "preferenceCount=${extractionResult.userPreferences.size}, " +
                "sessionId=$sessionId"
        )
    }

    private suspend fun retryOrFallback(
        reason: String,
        sessionId: String,
        stableMessages: List<ChatMessage>,
        retryAttempt: Int,
        resultLabel: String
    ) {
        if (retryAttempt < MAX_STAGE4_RETRY_COUNT) {
            logger("阶段四$resultLabel: reason=$reason, retry=${retryAttempt + 1}")
            scheduleRetry(
                reason = reason,
                sessionId = sessionId,
                messages = stableMessages,
                retryAttempt = retryAttempt + 1
            )
            return
        }
        logger("阶段四$resultLabel: reason=$reason, retry=$retryAttempt")
    }

    private fun scheduleRetry(
        reason: String,
        sessionId: String,
        messages: List<ChatMessage>,
        retryAttempt: Int
    ) {
        scope.launch {
            delay(STAGE4_RETRY_DELAY_MILLIS)
            runIfNeeded(
                reason = "$reason-重试",
                sessionId = sessionId,
                messages = messages,
                retryAttempt = retryAttempt
            )
        }
    }

    private companion object {
        const val STAGE4_IDLE_DELAY_MILLIS = 30 * 1000L        // 30s
        const val STAGE4_THROTTLE_MILLIS = 2 * 60 * 1000L      // 2min
        const val STAGE4_RETRY_DELAY_MILLIS = 3_000L
        const val MAX_STAGE4_RETRY_COUNT = 1
        const val MIN_STAGE4_MESSAGE_COUNT = 4
    }
}
