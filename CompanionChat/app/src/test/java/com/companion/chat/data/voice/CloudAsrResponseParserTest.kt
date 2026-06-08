package com.companion.chat.data.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudAsrResponseParserTest {

    @Test
    fun `解析顶层文本字段`() {
        val parser = CloudAsrResponseParser()

        assertEquals("你好", parser.extractText("""{"text":"你好"}""", "text"))
    }

    @Test
    fun `解析嵌套文本字段`() {
        val parser = CloudAsrResponseParser()

        assertEquals(
            "hello",
            parser.extractText("""{"data":{"results":[{"text":"hello"}]}}""", "data.results.0.text")
        )
    }

    @Test
    fun `字段不存在时返回空字符串`() {
        val parser = CloudAsrResponseParser()

        assertEquals("", parser.extractText("""{"data":{}}""", "data.text"))
    }
}
