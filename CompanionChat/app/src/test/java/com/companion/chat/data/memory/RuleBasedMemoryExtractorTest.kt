// RuleBasedMemoryExtractor deleted - tests deprecated
/*
package com.companion.chat.data.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBasedMemoryExtractorTest {

    private val extractor = RuleBasedMemoryExtractor()

    @Test
    fun `记住我叫小明提取事实记忆`() {
        val memories = extractor.extract(
            userMessage = "记住我叫小明",
            sessionId = "session-1"
        )

        assertEquals(1, memories.size)
        assertEquals("用户叫小明", memories.first().content)
        assertEquals("fact", memories.first().category)
    }

    @Test
    fun `我喜欢吃火锅提取偏好记忆`() {
        val memories = extractor.extract(
            userMessage = "我喜欢吃火锅",
            sessionId = "session-1"
        )

        assertEquals(1, memories.size)
        assertEquals("用户喜欢吃火锅", memories.first().content)
        assertEquals("preference", memories.first().category)
    }

    @Test
    fun `我住在北京提取事实记忆`() {
        val memories = extractor.extract(
            userMessage = "我住在北京",
            sessionId = "session-1"
        )

        assertEquals(1, memories.size)
        assertEquals("用户住在北京", memories.first().content)
        assertEquals("fact", memories.first().category)
    }

    @Test
    fun `不要再说这个了提取偏好记忆`() {
        val memories = extractor.extract(
            userMessage = "不要再说这个了",
            sessionId = "session-1"
        )

        assertEquals(1, memories.size)
        assertEquals("不要再说这个", memories.first().content)
        assertEquals("preference", memories.first().category)
    }

    @Test
    fun `我不喜欢太官方的回答提取负向偏好记忆`() {
        val memories = extractor.extract(
            userMessage = "我不喜欢太官方的回答",
            sessionId = "session-1"
        )

        assertEquals(1, memories.size)
        assertEquals("用户不喜欢太官方的回答", memories.first().content)
        assertEquals("preference", memories.first().category)
    }

    @Test
    fun `我一般晚上十点后聊天提取时间记忆`() {
        val memories = extractor.extract(
            userMessage = "我一般晚上十点后聊天",
            sessionId = "session-1"
        )

        assertEquals(1, memories.size)
        assertEquals("用户一般晚上十点后聊天", memories.first().content)
        assertEquals("time", memories.first().category)
    }

    @Test
    fun `我比较慢热提取自我描述记忆`() {
        val memories = extractor.extract(
            userMessage = "我比较慢热",
            sessionId = "session-1"
        )

        assertEquals(1, memories.size)
        assertEquals("用户比较慢热", memories.first().content)
        assertEquals("other", memories.first().category)
    }

    @Test
    fun `以后请尽量直接一点提取回答偏好记忆`() {
        val memories = extractor.extract(
            userMessage = "以后请尽量直接一点",
            sessionId = "session-1"
        )

        assertEquals(1, memories.size)
        assertEquals("用户偏好直接一点", memories.first().content)
        assertEquals("preference", memories.first().category)
    }

    @Test
    fun `一句话包含多条稳定信息时可提取多条记忆`() {
        val memories = extractor.extract(
            userMessage = "我喜欢科幻，也不喜欢太官方的回答",
            sessionId = "session-1"
        )

        assertEquals(2, memories.size)
        assertEquals(
            listOf("用户喜欢科幻", "用户不喜欢太官方的回答"),
            memories.map { it.content }
        )
    }

    @Test
    fun `普通消息不提取记忆`() {
        val memories = extractor.extract(
            userMessage = "今天天气不错",
            sessionId = "session-1"
        )

        assertTrue(memories.isEmpty())
    }
}

*/