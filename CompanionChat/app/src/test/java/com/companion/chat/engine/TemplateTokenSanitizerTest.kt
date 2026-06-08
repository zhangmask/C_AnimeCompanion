package com.companion.chat.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateTokenSanitizerTest {
    private val sanitizer = TemplateTokenSanitizer(
        stopMarkers = listOf("<end_of_turn>", "<start_of_turn>"),
        removableMarkers = listOf("<|assistant|>", "<|user|>", "<|system|>")
    )

    @Test
    fun `marker 单 token 出现时被过滤并停止`() {
        val result = sanitizer.append("正文<end_of_turn>尾巴")

        assertEquals("正文", result.text)
        assertTrue(result.shouldStop)
        assertEquals("", sanitizer.flush())
    }

    @Test
    fun `marker 被拆分到多个 token 时仍被过滤`() {
        val first = sanitizer.append("正文<end")
        val second = sanitizer.append("_of_turn>尾巴")

        assertEquals("正文", first.text)
        assertFalse(first.shouldStop)
        assertEquals("", second.text)
        assertTrue(second.shouldStop)
        assertEquals("", sanitizer.flush())
    }

    @Test
    fun `stop marker 后的尾部内容不再输出`() {
        val result = sanitizer.append("A<start_of_turn>assistant\nB")

        assertEquals("A", result.text)
        assertTrue(result.shouldStop)
        assertEquals("", sanitizer.append("C").text)
    }

    @Test
    fun `role marker 会被移除但不会停止`() {
        val result = sanitizer.append("<|assistant|>你好")

        assertEquals("你好", result.text)
        assertFalse(result.shouldStop)
    }

    @Test
    fun `role marker 跨 token 出现时仍被移除`() {
        val first = sanitizer.append("<|assis")
        val second = sanitizer.append("tant|>你好")

        assertEquals("", first.text)
        assertFalse(first.shouldStop)
        assertEquals("你好", second.text)
        assertFalse(second.shouldStop)
    }

    @Test
    fun `普通 Markdown 内容不被清洗破坏`() {
        val result = sanitizer.append("# 标题\n- **粗体** `code`\n> quote\n```kotlin\nval x = 1\n```")

        assertEquals("# 标题\n- **粗体** `code`\n> quote\n```kotlin\nval x = 1\n```", result.text + sanitizer.flush())
        assertFalse(result.shouldStop)
    }
}
