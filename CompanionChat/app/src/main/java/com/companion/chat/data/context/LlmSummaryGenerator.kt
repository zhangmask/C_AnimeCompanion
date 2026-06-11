package com.companion.chat.data.context

import com.companion.chat.data.engine.InferenceEngine
import com.companion.chat.data.model.ChatMessage
import com.companion.chat.data.model.MessageRole

class LlmSummaryGenerator(
    private val inferenceEngineProvider: () -> InferenceEngine?
) : SummaryGenerator {

    override suspend fun summarize(
        messages: List<ChatMessage>,
        settings: ContextSettings
    ): String {
        if (messages.isEmpty()) return ""

        val engine = inferenceEngineProvider() ?: return fallbackSummarize(messages, settings)

        val conversationText = buildConversationText(messages)
        val prompt = buildString {
            appendLine("请将以下对话历史压缩成简洁的摘要，保留关键信息、重要决定、用户偏好和情感基调。")
            appendLine("摘要应该是一段连贯的文字，而不是逐条列举。")
            appendLine("最大字数：${settings.summaryMaxChars}字")
            appendLine()
            appendLine("对话历史：")
            appendLine(conversationText)
            appendLine()
            appendLine("请直接输出摘要内容，不要添加任何前缀或说明：")
        }

        return try {
            val result = StringBuilder()
            engine.sendMessageStream(
                listOf(
                    ChatMessage(
                        role = MessageRole.USER,
                        content = prompt
                    )
                )
            ).collect { token ->
                result.append(token)
            }
            result.toString().trim().take(settings.summaryMaxChars)
        } catch (e: Exception) {
            fallbackSummarize(messages, settings)
        }
    }

    private fun buildConversationText(messages: List<ChatMessage>): String {
        return messages.joinToString(separator = "\n") { message ->
            val roleLabel = when (message.role) {
                MessageRole.USER -> "用户"
                MessageRole.ASSISTANT -> "助手"
                MessageRole.SYSTEM -> "系统"
            }
            val content = message.content.take(200)
            // 图片只给索引提示，不包含实际内容，避免超过图片数量限制
            val imageHint = if (message.images.isNotEmpty()) " [附带${message.images.size}张图片]" else ""
            "$roleLabel：$content$imageHint"
        }
    }

    private fun fallbackSummarize(
        messages: List<ChatMessage>,
        settings: ContextSettings
    ): String {
        val normalizedLines = messages.mapNotNull { message ->
            val content = message.content.trim()
            if (content.isEmpty()) return@mapNotNull null
            val roleLabel = when (message.role) {
                MessageRole.USER -> "用户"
                MessageRole.ASSISTANT -> "助手"
                MessageRole.SYSTEM -> "系统"
            }
            val clippedContent = content.take(48)
            "$roleLabel：$clippedContent"
        }
        if (normalizedLines.isEmpty()) return ""
        val rawSummary = normalizedLines.joinToString(separator = "；")
        return if (rawSummary.length <= settings.summaryMaxChars) {
            rawSummary
        } else {
            rawSummary.take(settings.summaryMaxChars - 3) + "..."
        }
    }
}
