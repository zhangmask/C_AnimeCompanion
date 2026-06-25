// MemoryPromptBuilderTest - buildPersistent/buildCombined removed from MemoryPromptBuilder
// TODO: rewrite tests for new build() and buildLayered() APIs
/*
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
        strength = 0.6,
        source = "manual",
        sessionId = null,
        createdAt = 0,
        updatedAt = 0,
        lastAccessedAt = 0
    )
}
*/
