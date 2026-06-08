package com.companion.chat.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedExtractionParserTest {

    @Test
    fun `合法 JSON 对象可同时解析记忆和偏好`() {
        val parser = UnifiedExtractionParser()

        val result = parser.parse(
            """
            {
              "memories": [
                {"category":"relation","content":"小王是用户的哥哥"},
                {"category":"time","content":"用户一般晚上聊天比较多"}
              ],
              "user_preferences": [
                {"category":"style","content":"喜欢简洁回答"},
                {"category":"interest","content":"游戏和科幻"}
              ]
            }
            """.trimIndent()
        )

        assertEquals(listOf("relation", "time"), result.memories.map { it.category })
        assertEquals(listOf("小王是用户的哥哥", "用户一般晚上聊天比较多"), result.memories.map { it.content })
        assertEquals(listOf("style", "interest", "habit"), result.userPreferences.map { it.category })
    }

    @Test
    fun `支持代码块中文字段名和类别别名`() {
        val parser = UnifiedExtractionParser()

        val result = parser.parse(
            """
            下面是提取结果：
            ```json
            {
              "记忆": [
                {"类别":"关系","内容":"小王是用户的哥哥"},
                {"类别":"时间","内容":"用户一般晚上聊天比较多"},
                {"类别":"其他","内容":"用户在意回答准确性"}
              ],
              "偏好": [
                {"类别":"称呼","内容":"老王"},
                {"类别":"风格","内容":"简洁回答"}
              ]
            }
            ```
            """.trimIndent()
        )

        assertEquals(listOf("relation", "time", "other"), result.memories.map { it.category })
        assertEquals(listOf("name", "style", "habit", "other"), result.userPreferences.map { it.category })
    }

    @Test
    fun `乱码或不完整对象返回空结果`() {
        val parser = UnifiedExtractionParser()

        val result = parser.parse("这不是 JSON")

        assertTrue(result.memories.isEmpty())
        assertTrue(result.userPreferences.isEmpty())
    }

    @Test
    fun `偏好误落到 memories 区时会自动纠偏回 user_preferences`() {
        val parser = UnifiedExtractionParser()

        val result = parser.parse(
            """
            {
              "memories": [
                {"category":"style","content":"不喜欢太官方的回答"},
                {"category":"time","content":"一般晚上十点后聊天"},
                {"category":"preference","content":"以后请尽量直接一点，简洁"},
                {"category":"preference","content":"比较慢热"},
                {"category":"preference","content":"喜欢科幻和游戏"}
              ],
              "user_preferences": []
            }
            """.trimIndent()
        )

        assertEquals(
            listOf("style", "habit", "style", "other", "interest"),
            result.userPreferences.map { it.category }
        )
        assertEquals(
            listOf(
                "不喜欢太官方的回答",
                "一般晚上十点后聊天",
                "以后请尽量直接一点，简洁",
                "比较慢热",
                "喜欢科幻和游戏"
            ),
            result.userPreferences.map { it.content }
        )
    }
}
