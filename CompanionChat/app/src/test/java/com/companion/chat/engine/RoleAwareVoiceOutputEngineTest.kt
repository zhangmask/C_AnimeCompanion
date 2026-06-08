package com.companion.chat.engine

import com.companion.chat.data.engine.VoiceOutputConfig
import com.companion.chat.data.engine.VoiceOutputEngine
import com.companion.chat.data.engine.VoiceOutputMode
import com.companion.chat.data.engine.VoiceOutputState
import com.companion.chat.data.voice.VoiceCloneEngine
import com.companion.chat.data.voice.VoiceCloneProvider
import com.companion.chat.data.voice.VoiceCloneRequest
import com.companion.chat.data.voice.VoiceCloneResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RoleAwareVoiceOutputEngineTest {

    @Test
    fun `SYSTEM_TTS 直接使用系统 TTS`() = runBlocking {
        val fallback = FakeVoiceOutputEngine()
        val engine = RoleAwareVoiceOutputEngine(
            fallbackEngine = fallback,
            roleCardRepository = null,
            activeRoleConfigProvider = { VoiceOutputConfig(mode = VoiceOutputMode.SYSTEM_TTS) }
        )

        engine.speak("你好")

        assertEquals(listOf("你好"), fallback.spokenTexts)
    }

    @Test
    fun `CLONE 成功时播放生成音频`() = runBlocking {
        val fallback = FakeVoiceOutputEngine()
        val player = FakeGeneratedAudioPlayer()
        val engine = RoleAwareVoiceOutputEngine(
            fallbackEngine = fallback,
            roleCardRepository = null,
            cloneEngine = FakeVoiceCloneEngine(
                VoiceCloneResult(
                    provider = VoiceCloneProvider.MOSS_TTS_NANO,
                    audioUri = "file:///tmp/moss.wav",
                    fallbackToSystemTts = false
                )
            ),
            localAudioPlaybackEngine = player,
            activeRoleConfigProvider = {
                VoiceOutputConfig(
                    mode = VoiceOutputMode.CLONE,
                    referenceAudioUri = "file:///tmp/ref.wav"
                )
            }
        )

        engine.speak("你好")

        assertEquals(emptyList<String>(), fallback.spokenTexts)
        assertEquals(listOf("file:///tmp/moss.wav"), player.playedUris)
    }

    @Test
    fun `CLONE 回退时使用系统 TTS`() = runBlocking {
        val fallback = FakeVoiceOutputEngine()
        val engine = RoleAwareVoiceOutputEngine(
            fallbackEngine = fallback,
            roleCardRepository = null,
            cloneEngine = FakeVoiceCloneEngine(
                VoiceCloneResult(
                    provider = VoiceCloneProvider.MOSS_TTS_NANO,
                    fallbackToSystemTts = true,
                    message = "缺模型"
                )
            ),
            localAudioPlaybackEngine = FakeGeneratedAudioPlayer(),
            activeRoleConfigProvider = { VoiceOutputConfig(mode = VoiceOutputMode.CLONE) }
        )

        engine.speak("你好")

        assertEquals(listOf("你好"), fallback.spokenTexts)
    }

    private class FakeVoiceOutputEngine : VoiceOutputEngine {
        override val state: Flow<VoiceOutputState> = MutableStateFlow(VoiceOutputState.Idle)
        val spokenTexts = mutableListOf<String>()

        override suspend fun speak(text: String, config: VoiceOutputConfig) {
            spokenTexts += text
        }

        override fun stop() = Unit
        override fun release() = Unit
    }

    private class FakeVoiceCloneEngine(
        private val result: VoiceCloneResult
    ) : VoiceCloneEngine {
        override suspend fun synthesize(request: VoiceCloneRequest): Result<VoiceCloneResult> {
            return Result.success(result)
        }
    }

    private class FakeGeneratedAudioPlayer : GeneratedAudioPlayer {
        override val state: StateFlow<VoiceOutputState> = MutableStateFlow(VoiceOutputState.Idle)
        val playedUris = mutableListOf<String>()

        override fun play(audioUri: String) {
            playedUris += audioUri
        }

        override fun stop() = Unit
        override fun release() = Unit
    }
}
