package com.companion.chat.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceDrivenChatPolicyTest {

    @Test
    fun `blank transcript is held for user`() {
        val decision = VoiceDrivenChatPolicy.evaluateTranscript(
            transcript = "  ",
            isGenerating = false,
            isEngineReady = true
        )

        assertEquals(VoiceTranscriptDecision.HoldForUser("未识别到文本"), decision)
    }

    @Test
    fun `transcript is held while model is generating`() {
        val decision = VoiceDrivenChatPolicy.evaluateTranscript(
            transcript = "你好",
            isGenerating = true,
            isEngineReady = true
        )

        assertEquals(VoiceTranscriptDecision.HoldForUser("正在生成回复，请稍后再说"), decision)
    }

    @Test
    fun `transcript is held when engine is not ready`() {
        val decision = VoiceDrivenChatPolicy.evaluateTranscript(
            transcript = "你好",
            isGenerating = false,
            isEngineReady = false
        )

        assertEquals(VoiceTranscriptDecision.HoldForUser("模型未就绪，语音内容已保留在输入框"), decision)
    }

    @Test
    fun `ready transcript auto sends`() {
        val decision = VoiceDrivenChatPolicy.evaluateTranscript(
            transcript = "你好",
            isGenerating = false,
            isEngineReady = true
        )

        assertTrue(decision is VoiceTranscriptDecision.AutoSend)
    }
}
