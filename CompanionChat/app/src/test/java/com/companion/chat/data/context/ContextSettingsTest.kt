package com.companion.chat.data.context

import org.junit.Assert.assertEquals
import org.junit.Test

class ContextSettingsTest {

    @Test
    fun `默认保留轮数是10`() {
        val settings = ContextSettings()

        assertEquals(10, settings.retainedRounds)
    }

    @Test
    fun `默认阈值相关字段存在`() {
        val settings = ContextSettings()

        assertEquals(10, settings.compressionBuffer)
        assertEquals(200, settings.summaryMaxChars)
        assertEquals(60_000L, settings.summaryTimeoutMillis)
    }
}
