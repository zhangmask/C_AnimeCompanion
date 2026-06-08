package com.companion.chat.data.preferences

import com.companion.chat.data.model.ChatMessage
import com.companion.chat.data.model.MessageRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedExtractionPromptBuilderTest {

    @Test
    fun `prompt 只包含最近5轮对话`() {
        val builder = UnifiedExtractionPromptBuilder()
        val messages = buildConversation(rounds = 6)

        val prompt = builder.buildPrompt(messages)

        assertFalse(prompt.contains("第1轮用户"))
        assertFalse(prompt.contains("第1轮助手"))
        assertTrue(prompt.contains("第2轮用户"))
        assertTrue(prompt.contains("第6轮助手"))
    }

    @Test
    fun `prompt 同时声明 memories 和 user_preferences 分类`() {
        val builder = UnifiedExtractionPromptBuilder()

        val prompt = builder.buildPrompt(buildConversation(rounds = 2))

        assertTrue(prompt.contains("fact / preference / event / relation / time / other"))
        assertTrue(prompt.contains("name / style / interest / habit / other"))
        assertTrue(prompt.contains("\"memories\""))
        assertTrue(prompt.contains("\"user_preferences\""))
    }

    @Test
    fun `prompt 明确强调兴趣习惯性格和回答偏好都应提取`() {
        val builder = UnifiedExtractionPromptBuilder()

        val prompt = builder.buildPrompt(buildConversation(rounds = 2))

        assertTrue(prompt.contains("喜欢/不喜欢"))
        assertTrue(prompt.contains("习惯"))
        assertTrue(prompt.contains("性格特征"))
        assertTrue(prompt.contains("回答偏好"))
        assertTrue(prompt.contains("我比较慢热"))
        assertTrue(prompt.contains("以后请尽量直接一点，多举例"))
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
