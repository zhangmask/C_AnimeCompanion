package com.companion.chat.data.context

import com.companion.chat.data.model.ChatMessage
import com.companion.chat.data.model.MessageRole

class RuleBasedSummaryGenerator : SummaryGenerator {

    override suspend fun summarize(
        messages: List<ChatMessage>,
        settings: ContextSettings
    ): String {
        if (messages.isEmpty()) {
            return ""
        }

        val normalizedLines = messages.mapNotNull { message ->
            val content = message.content.trim()
            if (content.isEmpty() && message.images.isEmpty()) {
                return@mapNotNull null
            }

            val roleLabel = when (message.role) {
                MessageRole.USER -> "用户"
                MessageRole.ASSISTANT -> "助手"
                MessageRole.SYSTEM -> "系统"
            }

            val clippedContent = content.take(MAX_MESSAGE_CHARS)
            // 图片只给索引提示，不包含实际内容
            val imageHint = if (message.images.isNotEmpty()) " [${message.images.size}张图片]" else ""
            "$roleLabel：$clippedContent$imageHint"
        }

        if (normalizedLines.isEmpty()) {
            return ""
        }

        val rawSummary = normalizedLines.joinToString(separator = "；")
        val maxChars = settings.summaryMaxChars.coerceAtLeast(MIN_SUMMARY_CHARS)

        return if (rawSummary.length <= maxChars) {
            rawSummary
        } else {
            rawSummary.take(maxChars - ELLIPSIS.length) + ELLIPSIS
        }
    }

    private companion object {
        const val MAX_MESSAGE_CHARS = 200
        const val MIN_SUMMARY_CHARS = 1
        const val ELLIPSIS = "..."
    }
}
