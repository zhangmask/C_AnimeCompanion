package com.companion.chat.ui.chat.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownMessageParserTest {

    @Test
    fun `标题段落和列表会解析成预期块类型`() {
        val blocks = MarkdownMessageParser.parse(
            """
            # 标题
            这是 **粗体** 和 `code`

            - 第一项
            - 第二项
            """.trimIndent()
        )

        assertEquals(3, blocks.size)
        assertEquals(MarkdownBlock.Heading(level = 1, text = "标题"), blocks[0])
        assertEquals(MarkdownBlock.Paragraph("这是 **粗体** 和 `code`"), blocks[1])
        assertEquals(MarkdownBlock.UnorderedList(listOf("第一项", "第二项")), blocks[2])
    }

    @Test
    fun `代码块会保留语言和内部换行`() {
        val blocks = MarkdownMessageParser.parse(
            """
            ```kotlin
            fun main() {
                println("hi")
            }
            ```
            """.trimIndent()
        )

        assertEquals(
            MarkdownBlock.CodeBlock(
                language = "kotlin",
                code = "fun main() {\n    println(\"hi\")\n}"
            ),
            blocks.single()
        )
    }

    @Test
    fun `未闭合 fenced code block 会稳定展示到消息末尾`() {
        val blocks = MarkdownMessageParser.parse(
            """
            ```json
            {"ok": true}
            """.trimIndent()
        )

        assertEquals(MarkdownBlock.CodeBlock(language = "json", code = "{\"ok\": true}"), blocks.single())
    }

    @Test
    fun `有序列表和引用会解析成对应块`() {
        val blocks = MarkdownMessageParser.parse(
            """
            1. 第一
            2. 第二

            > 引用第一行
            > 引用第二行
            """.trimIndent()
        )

        assertEquals(MarkdownBlock.OrderedList(listOf("第一", "第二")), blocks[0])
        assertEquals(MarkdownBlock.Quote("引用第一行\n引用第二行"), blocks[1])
    }

    @Test
    fun `空白消息返回空块列表`() {
        assertTrue(MarkdownMessageParser.parse("  \n ").isEmpty())
    }
}
