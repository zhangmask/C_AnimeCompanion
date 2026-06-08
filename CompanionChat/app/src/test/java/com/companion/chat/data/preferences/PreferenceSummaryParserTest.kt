package com.companion.chat.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceSummaryParserTest {

    @Test
    fun `合法 JSON 数组可解析为结构化对象`() {
        val parser = PreferenceSummaryParser()

        val result = parser.parse(
            """
            [
              {"category":"name","content":"小明"},
              {"category":"style","content":"喜欢简洁回答"}
            ]
            """.trimIndent()
        )

        assertEquals(
            listOf(
                ExtractedPreference(category = "name", content = "小明"),
                ExtractedPreference(category = "style", content = "喜欢简洁回答")
            ),
            result
        )
    }

    @Test
    fun `乱码非 JSON 和空字符串返回空列表`() {
        val parser = PreferenceSummaryParser()

        assertTrue(parser.parse("这不是 JSON").isEmpty())
        assertTrue(parser.parse("{\"category\":\"name\"}").isEmpty())
        assertTrue(parser.parse("").isEmpty())
    }

    @Test
    fun `空数组返回空列表但不报错`() {
        val parser = PreferenceSummaryParser()

        assertTrue(parser.parse("[]").isEmpty())
    }

    @Test
    fun `过滤空字段和未知类别`() {
        val parser = PreferenceSummaryParser()

        val result = parser.parse(
            """
            [
              {"category":"unknown","content":"无效"},
              {"category":"name","content":"   "},
              {"category":"habit","content":"喜欢晚上聊天"}
            ]
            """.trimIndent()
        )

        assertEquals(listOf(ExtractedPreference(category = "habit", content = "喜欢晚上聊天")), result)
    }

    @Test
    fun `支持解析带代码块和前后说明的 JSON`() {
        val parser = PreferenceSummaryParser()

        val result = parser.parse(
            """
            下面是提取结果：
            ```json
            [
              {"category":"style","content":"喜欢简洁回答"},
              {"category":"interest","content":"游戏和科幻"}
            ]
            ```
            请查收。
            """.trimIndent()
        )

        assertEquals(
            listOf(
                ExtractedPreference(category = "style", content = "喜欢简洁回答"),
                ExtractedPreference(category = "interest", content = "游戏和科幻")
            ),
            result
        )
    }

    @Test
    fun `支持解析中文字段名和类别别名`() {
        val parser = PreferenceSummaryParser()

        val result = parser.parse(
            """
            [
              {"类别":"称呼","内容":"老王"},
              {"类别":"风格","内容":"简洁回答"},
              {"类别":"兴趣","内容":"游戏和科幻"}
            ]
            """.trimIndent()
        )

        assertEquals(
            listOf(
                ExtractedPreference(category = "name", content = "老王"),
                ExtractedPreference(category = "style", content = "简洁回答"),
                ExtractedPreference(category = "interest", content = "游戏和科幻")
            ),
            result
        )
    }
}
