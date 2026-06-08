package com.companion.chat.data.preferences

import com.companion.chat.data.model.ChatMessage
import com.companion.chat.data.model.MessageRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceSummaryPromptBuilderTest {

    @Test
    fun `prompt 只包含最近5轮对话`() {
        val builder = PreferenceSummaryPromptBuilder()
        val messages = buildConversation(rounds = 6)

        val prompt = builder.buildPrompt(messages)

        assertFalse(prompt.contains("第1轮用户"))
        assertFalse(prompt.contains("第1轮助手"))
        assertTrue(prompt.contains("第2轮用户"))
        assertTrue(prompt.contains("第6轮助手"))
    }

    @Test
    fun `prompt 固定列出所有偏好类别`() {
        val builder = PreferenceSummaryPromptBuilder()

        val prompt = builder.buildPrompt(buildConversation(rounds = 2))

        assertTrue(prompt.contains("name"))
        assertTrue(prompt.contains("style"))
        assertTrue(prompt.contains("interest"))
        assertTrue(prompt.contains("habit"))
        assertTrue(prompt.contains("other"))
    }

    @Test
    fun `prompt 明确要求只输出纯 JSON`() {
        val builder = PreferenceSummaryPromptBuilder()

        val prompt = builder.buildPrompt(buildConversation(rounds = 2))

        assertTrue(prompt.contains("只输出一个 JSON 数组"))
        assertTrue(prompt.contains("不要输出解释"))
        assertTrue(prompt.contains("不要输出解释、标题、Markdown 代码块"))
    }

    private fun buildConversation(rounds: Int): List<ChatMessage> {
        return buildList {
            repeat(rounds) { index ->
                val round = index + 1
                add(ChatMessage(role = MessageRole.USER, content = "第${round}轮用户"))
                add(ChatMessage(role = MessageRole.ASSISTANT, content = "第${round}轮助手"))
            }
        }
    }
}
