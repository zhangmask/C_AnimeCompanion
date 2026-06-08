package com.companion.chat.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class PreferenceMemoryDeriverTest {

    private val deriver = PreferenceMemoryDeriver()

    @Test
    fun `style interest habit 和 name 会强制派生为记忆`() {
        val result = deriver.derive(
            listOf(
                ExtractedPreference(category = "style", content = "要求简洁回答"),
                ExtractedPreference(category = "interest", content = "喜欢游戏和科幻"),
                ExtractedPreference(category = "habit", content = "一般晚上十点后聊天"),
                ExtractedPreference(category = "name", content = "老王")
            )
        )

        assertEquals(
            listOf("preference", "preference", "time", "fact"),
            result.map { it.category }
        )
        assertEquals(
            listOf("用户偏好要求简洁回答", "用户喜欢游戏和科幻", "用户一般晚上十点后聊天", "用户叫老王"),
            result.map { it.content }
        )
    }

    @Test
    fun `非时间习惯会派生为 other 记忆`() {
        val result = deriver.derive(
            listOf(
                ExtractedPreference(category = "habit", content = "聊天前会先打招呼")
            )
        )

        assertEquals(listOf("other"), result.map { it.category })
        assertEquals(listOf("用户习惯聊天前会先打招呼"), result.map { it.content })
    }

    @Test
    fun `other 会派生为 other 记忆并补用户前缀`() {
        val result = deriver.derive(
            listOf(
                ExtractedPreference(category = "other", content = "在意回答准确性")
            )
        )

        assertEquals(listOf("other"), result.map { it.category })
        assertEquals(listOf("用户在意回答准确性"), result.map { it.content })
    }

    @Test
    fun `负向兴趣和性格描述会保留原始语义`() {
        val result = deriver.derive(
            listOf(
                ExtractedPreference(category = "interest", content = "不喜欢太官方的回答"),
                ExtractedPreference(category = "other", content = "比较慢热")
            )
        )

        assertEquals(
            listOf("preference", "other"),
            result.map { it.category }
        )
        assertEquals(
            listOf("用户不喜欢太官方的回答", "用户比较慢热"),
            result.map { it.content }
        )
    }

    @Test
    fun `非时间型一般表达不会被错误归类为 time`() {
        val result = deriver.derive(
            listOf(
                ExtractedPreference(category = "habit", content = "一般说话很直接")
            )
        )

        assertEquals(listOf("other"), result.map { it.category })
        assertEquals(listOf("用户一般说话很直接"), result.map { it.content })
    }
}
