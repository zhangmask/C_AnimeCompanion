package com.companion.chat.data.context.tokenizer

/**
 * 分词器测试（仅用于验证效果）
 */
object TokenizerTest {
    @JvmStatic
    fun main(args: Array<String>) {
        val testCases = listOf(
            "我喜欢吃火锅",
            "今天天气真好",
            "我的生日是1995年3月15日",
            "小夏是一个温柔的角色",
            "I like programming in Kotlin"
        )

        for (text in testCases) {
            val tokens = SimpleChineseTokenizer.tokenize(text)
            println("原文: $text")
            println("分词: $tokens")
            println()
        }
    }
}
