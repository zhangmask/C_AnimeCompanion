package com.companion.chat.data.memory

import com.companion.chat.data.local.entity.Memory
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryPromptBuilderTest {

    private val builder = MemoryPromptBuilder()

    @Test
    fun `有相关记忆时生成固定标题和列表格式`() {
        val prompt = builder.build(
            listOf(
                memory(content = "用户叫小明", category = "fact"),
                memory(content = "用户喜欢简洁回答", category = "preference")
            )
        )

        assertEquals(
            "从记忆中检索到的与当前对话相关的信息：\n以下内容均为用户本人的记忆，不代表助手自身。\n- [事实] 用户叫小明\n- [偏好] 用户喜欢简洁回答",
            prompt
        )
    }

    @Test
    fun `无相关记忆时不拼空段落`() {
        val prompt = builder.build(emptyList())

        assertEquals("", prompt)
    }

    @Test
    fun `长期记忆段使用固定标题并显示关系标签`() {
        val prompt = builder.buildPersistent(
            listOf(memory(content = "用户是同事", category = "relation"))
        )

        assertEquals(
            "长期记忆中的关键信息：\n以下内容均为用户本人的记忆，不代表助手自身。\n- [关系] 用户是同事",
            prompt
        )
    }

    @Test
    fun `记忆段保留原文但补充用户视角说明`() {
        val prompt = builder.buildPersistent(
            listOf(memory(content = "小王是我的哥哥", category = "relation"))
        )

        assertEquals(
            "长期记忆中的关键信息：\n以下内容均为用户本人的记忆，不代表助手自身。\n- [关系] 小王是我的哥哥",
            prompt
        )
    }

    @Test
    fun `常驻段和动态段都为空时返回空字符串`() {
        val prompt = builder.buildCombined(
            persistentMemories = emptyList(),
            retrievedMemories = emptyList()
        )

        assertEquals("", prompt)
    }

    @Test
    fun `时间和其他分类使用固定中文标签`() {
        val prompt = builder.build(
            listOf(
                memory(content = "用户一般晚上聊天比较多", category = "time"),
                memory(content = "用户在意回答准确性", category = "other")
            )
        )

        assertEquals(
            "从记忆中检索到的与当前对话相关的信息：\n以下内容均为用户本人的记忆，不代表助手自身。\n- [时间] 用户一般晚上聊天比较多\n- [其他] 用户在意回答准确性",
            prompt
        )
    }

    private fun memory(content: String, category: String) = Memory(
        id = 1,
        content = content,
        category = category,
        layer = "long_term",
        source = "manual",
        referenceCount = 0,
        sessionId = null,
        createdAt = 0,
        updatedAt = 0,
        expiresAt = null
    )
}
