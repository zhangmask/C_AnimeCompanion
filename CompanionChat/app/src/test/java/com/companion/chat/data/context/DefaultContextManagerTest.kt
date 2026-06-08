package com.companion.chat.data.context

import com.companion.chat.data.model.ChatMessage
import com.companion.chat.data.model.MessageRole
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultContextManagerTest {

    private val settings = ContextSettings(retainedRounds = 10, compressionBuffer = 10)

    @Test
    fun `消息数小于等于阈值时不压缩`() {
        val manager = DefaultContextManager()
        val messages = buildMessages(count = 30)

        assertFalse(manager.shouldCompress(messages, settings))
    }

    @Test
    fun `消息数大于阈值时压缩`() {
        val manager = DefaultContextManager()
        val messages = buildMessages(count = 31)

        assertTrue(manager.shouldCompress(messages, settings))
    }

    @Test
    fun `buildContext返回最近N轮消息`() = runBlocking {
        val manager = DefaultContextManager()
        val settings = ContextSettings(retainedRounds = 3, compressionBuffer = 10)
        val messages = buildConversationWithCurrentMessage(historyCount = 12, currentContent = "当前问题")

        val contextWindow = manager.buildContext(
            messages = messages,
            systemPrompt = "基础提示词",
            userPreferences = "",
            settings = settings
        )

        assertEquals(listOf("历史消息7", "历史消息8", "历史消息9", "历史消息10", "历史消息11", "历史消息12"), contextWindow.recentMessages.map { it.content })
        assertEquals("当前问题", contextWindow.currentMessage.content)
    }

    @Test
    fun `无需摘要时historySummary为空字符串`() = runBlocking {
        val manager = DefaultContextManager()
        val messages = buildConversationWithCurrentMessage(historyCount = 4, currentContent = "当前问题")

        val contextWindow = manager.buildContext(
            messages = messages,
            systemPrompt = "基础提示词",
            userPreferences = "",
            settings = ContextSettings(retainedRounds = 10, compressionBuffer = 10)
        )

        assertEquals("", contextWindow.historySummary)
    }

    @Test
    fun `存在被裁剪历史时返回非空摘要且长度受限`() = runBlocking {
        val manager = DefaultContextManager()
        val settings = ContextSettings(retainedRounds = 2, compressionBuffer = 0, summaryMaxChars = 60)
        val messages = buildConversationWithCurrentMessage(historyCount = 10, currentContent = "当前问题")

        val contextWindow = manager.buildContext(
            messages = messages,
            systemPrompt = "基础提示词",
            userPreferences = "",
            settings = settings
        )

        assertTrue(contextWindow.historySummary.isNotBlank())
        assertTrue(contextWindow.historySummary.length <= 60)
    }

    @Test
    fun `buildContext会保留长期记忆段和动态记忆段`() = runBlocking {
        val manager = DefaultContextManager()
        val messages = buildConversationWithCurrentMessage(historyCount = 4, currentContent = "当前问题")

        val contextWindow = manager.buildContext(
            messages = messages,
            systemPrompt = "基础提示词",
            userPreferences = "",
            persistentMemoryPrompt = "长期记忆中的关键信息：\n- [关系] 小王是用户同事",
            memoryPrompt = "从记忆中检索到的与当前对话相关的信息：\n- [事实] 用户最近在做 Android 项目",
            settings = ContextSettings(retainedRounds = 10, compressionBuffer = 10)
        )

        assertEquals("长期记忆中的关键信息：\n- [关系] 小王是用户同事", contextWindow.persistentMemoryPrompt)
        assertEquals("从记忆中检索到的与当前对话相关的信息：\n- [事实] 用户最近在做 Android 项目", contextWindow.memoryPrompt)
        assertTrue(contextWindow.systemPrompt.contains("长期记忆中的关键信息"))
        assertTrue(contextWindow.systemPrompt.contains("从记忆中检索到的与当前对话相关的信息"))
    }

    @Test
    fun `buildContext会保留confirmed偏好段`() = runBlocking {
        val manager = DefaultContextManager()
        val messages = buildConversationWithCurrentMessage(historyCount = 4, currentContent = "当前问题")

        val contextWindow = manager.buildContext(
            messages = messages,
            systemPrompt = "基础提示词",
            userPreferences = "关于当前用户的已知信息（请自然地融入对话，不要刻意提及你知道这些）：\n- 喜欢简洁回答",
            settings = ContextSettings(retainedRounds = 10, compressionBuffer = 10)
        )

        assertEquals("关于当前用户的已知信息（请自然地融入对话，不要刻意提及你知道这些）：\n- 喜欢简洁回答", contextWindow.userPreferences)
        assertTrue(contextWindow.systemPrompt.contains("关于当前用户的已知信息"))
    }

    @Test
    fun `摘要器超时时compressHistory返回空字符串`() = runBlocking {
        val manager = DefaultContextManager(
            summaryGenerator = object : SummaryGenerator {
                override suspend fun summarize(
                    messages: List<ChatMessage>,
                    settings: ContextSettings
                ): String {
                    delay(100)
                    return "不会返回"
                }
            }
        )

        val result = manager.compressHistory(
            messages = listOf(
                ChatMessage(role = MessageRole.USER, content = "你好"),
                ChatMessage(role = MessageRole.ASSISTANT, content = "你好")
            ),
            settings = ContextSettings(summaryTimeoutMillis = 10)
        )

        assertEquals("", result)
    }

    private fun buildMessages(count: Int): List<ChatMessage> {
        return (1..count).map { index ->
            ChatMessage(
                role = if (index % 2 == 0) MessageRole.ASSISTANT else MessageRole.USER,
                content = "消息$index"
            )
        }
    }

    private fun buildConversationWithCurrentMessage(
        historyCount: Int,
        currentContent: String
    ): List<ChatMessage> {
        val historyMessages = (1..historyCount).map { index ->
            ChatMessage(
                role = if (index % 2 == 0) MessageRole.ASSISTANT else MessageRole.USER,
                content = "历史消息$index"
            )
        }
        return historyMessages + ChatMessage(
            role = MessageRole.USER,
            content = currentContent
        )
    }
}
