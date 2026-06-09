package com.companion.chat.data.context

import com.companion.chat.data.engine.InferenceEngine
import com.companion.chat.data.model.ChatMessage
import com.companion.chat.data.model.MessageRole
import kotlinx.coroutines.withTimeoutOrNull

class DefaultContextManager(
    private val promptAssembler: PromptAssembler = PromptAssembler(),
    private val summaryGenerator: SummaryGenerator = RuleBasedSummaryGenerator(),
    private val inferenceEngineProvider: (() -> InferenceEngine?)? = null
) : ContextManager {

    fun withLlmSummary(): DefaultContextManager {
        val llmProvider = inferenceEngineProvider ?: return this
        return DefaultContextManager(
            promptAssembler = promptAssembler,
            summaryGenerator = LlmSummaryGenerator(llmProvider),
            inferenceEngineProvider = llmProvider
        )
    }

    override fun shouldCompress(messages: List<ChatMessage>, settings: ContextSettings): Boolean {
        return messages.size > settings.compressionThreshold
    }

    override suspend fun buildContext(
        messages: List<ChatMessage>,
        systemPrompt: String,
        userPreferences: String,
        persistentMemoryPrompt: String,
        memoryPrompt: String,
        settings: ContextSettings
    ): ContextWindow {
        val currentMessage = messages.lastOrNull { it.role == MessageRole.USER }
            ?: throw IllegalArgumentException("缺少当前用户消息")
        val currentMessageIndex = messages.indexOfLast { it.id == currentMessage.id }
        val historyMessages = if (currentMessageIndex > 0) {
            messages.subList(0, currentMessageIndex)
        } else {
            emptyList()
        }
        val maxRecentMessageCount = settings.retainedRounds * 2
        val recentMessages = historyMessages.takeLast(maxRecentMessageCount)
        val droppedMessages = historyMessages.dropLast(maxRecentMessageCount)
        val historySummary = if (droppedMessages.isEmpty()) {
            ""
        } else {
            compressHistory(droppedMessages, settings)
        }

        return ContextWindow(
            systemPrompt = promptAssembler.assemble(
                baseSystemPrompt = systemPrompt,
                userPreferences = userPreferences,
                persistentMemoryPrompt = persistentMemoryPrompt,
                memoryPrompt = memoryPrompt,
                historySummary = historySummary
            ),
            userPreferences = userPreferences,
            persistentMemoryPrompt = persistentMemoryPrompt,
            memoryPrompt = memoryPrompt,
            historySummary = historySummary,
            recentMessages = recentMessages,
            currentMessage = currentMessage
        )
    }

    override suspend fun compressHistory(
        messages: List<ChatMessage>,
        settings: ContextSettings
    ): String {
        return try {
            withTimeoutOrNull(settings.summaryTimeoutMillis) {
                summaryGenerator.summarize(messages, settings)
            } ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}
