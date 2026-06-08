package com.companion.chat.data.context

import com.companion.chat.data.model.ChatMessage
import com.companion.chat.data.model.MessageRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class NoOpSummaryGeneratorTest {

    @Test
    fun `NoOpSummaryGenerator返回空字符串`() = runBlocking {
        val generator = NoOpSummaryGenerator()

        val result = generator.summarize(
            messages = listOf(ChatMessage(role = MessageRole.USER, content = "你好")),
            settings = ContextSettings()
        )

        assertEquals("", result)
    }

    @Test
    fun `摘要器不可用时返回空字符串`() = runBlocking {
        val manager = DefaultContextManager(
            summaryGenerator = object : SummaryGenerator {
                override suspend fun summarize(
                    messages: List<ChatMessage>,
                    settings: ContextSettings
                ): String {
                    throw IllegalStateException("摘要器不可用")
                }
            }
        )

        val result = manager.compressHistory(
            messages = listOf(
                ChatMessage(role = MessageRole.USER, content = "你好"),
                ChatMessage(role = MessageRole.ASSISTANT, content = "你好，有什么可以帮你")
            ),
            settings = ContextSettings()
        )

        assertEquals("", result)
    }
}
