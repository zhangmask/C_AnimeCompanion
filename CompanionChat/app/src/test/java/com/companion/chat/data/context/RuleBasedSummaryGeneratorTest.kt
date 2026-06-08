package com.companion.chat.data.context

import com.companion.chat.data.model.ChatMessage
import com.companion.chat.data.model.MessageRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBasedSummaryGeneratorTest {

    @Test
    fun `非空消息列表返回非空摘要`() = runBlocking {
        val generator = RuleBasedSummaryGenerator()

        val result = generator.summarize(
            messages = listOf(
                ChatMessage(role = MessageRole.USER, content = "我想讨论今天的开发计划"),
                ChatMessage(role = MessageRole.ASSISTANT, content = "可以，我们先拆分任务")
            ),
            settings = ContextSettings(summaryMaxChars = 200)
        )

        assertTrue(result.isNotBlank())
        assertTrue(result.contains("用户："))
    }

    @Test
    fun `摘要长度不超过summaryMaxChars`() = runBlocking {
        val generator = RuleBasedSummaryGenerator()

        val result = generator.summarize(
            messages = listOf(
                ChatMessage(role = MessageRole.USER, content = "a".repeat(120)),
                ChatMessage(role = MessageRole.ASSISTANT, content = "b".repeat(120))
            ),
            settings = ContextSettings(summaryMaxChars = 40)
        )

        assertTrue(result.length <= 40)
    }

    @Test
    fun `空消息列表返回空字符串`() = runBlocking {
        val generator = RuleBasedSummaryGenerator()

        val result = generator.summarize(
            messages = emptyList(),
            settings = ContextSettings()
        )

        assertEquals("", result)
    }

    @Test
    fun `空内容消息不会进入摘要`() = runBlocking {
        val generator = RuleBasedSummaryGenerator()

        val result = generator.summarize(
            messages = listOf(
                ChatMessage(role = MessageRole.USER, content = "   "),
                ChatMessage(role = MessageRole.ASSISTANT, content = "保留这条")
            ),
            settings = ContextSettings(summaryMaxChars = 200)
        )

        assertFalse(result.contains("用户："))
        assertTrue(result.contains("助手：保留这条"))
    }
}
