package com.companion.chat.data.voice

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCloneProviderSelectorTest {

    @Test
    fun `本地克隆占位会声明系统 TTS 回退`() = runBlocking {
        val selector = VoiceCloneProviderSelector()

        val result = selector.synthesize(
            provider = VoiceCloneProvider.LOCAL_CLONE_PLACEHOLDER,
            request = VoiceCloneRequest(text = "你好", roleId = "xia")
        ).getOrThrow()

        assertEquals(VoiceCloneProvider.LOCAL_CLONE_PLACEHOLDER, result.provider)
        assertTrue(result.fallbackToSystemTts)
        assertTrue(result.message.contains("尚未接入"))
    }

    @Test
    fun `MOSS 模型完整时选择 moss tts nano`() {
        val selector = VoiceCloneProviderSelector()

        assertEquals(
            VoiceCloneProvider.MOSS_TTS_NANO,
            selector.chooseProvider(MossTtsNanoModelStatus.Ready)
        )
    }

    @Test
    fun `MOSS 模型缺失时选择系统 TTS`() {
        val selector = VoiceCloneProviderSelector()

        assertEquals(
            VoiceCloneProvider.SYSTEM_TTS,
            selector.chooseProvider(MossTtsNanoModelStatus.MissingFiles(listOf("moss_config.json")))
        )
    }
}
