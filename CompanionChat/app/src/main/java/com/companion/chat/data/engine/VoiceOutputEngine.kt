package com.companion.chat.data.engine

import kotlinx.coroutines.flow.Flow

sealed class VoiceOutputState {
    data object Idle : VoiceOutputState()
    data object Speaking : VoiceOutputState()
    data class Error(val message: String) : VoiceOutputState()
}

enum class VoiceOutputMode {
    SYSTEM_TTS,
    CLONE
}

/** How new utterances interact with currently playing audio. */
enum class TtsQueueMode {
    /** Replace any currently playing audio (default). */
    FLUSH,
    /** Append after the current audio finishes. */
    ADD
}

data class VoiceOutputConfig(
    val mode: VoiceOutputMode = VoiceOutputMode.SYSTEM_TTS,
    val referenceAudioUri: String = "",
    val displayName: String = ""
)

interface VoiceOutputEngine {
    val state: Flow<VoiceOutputState>

    suspend fun speak(
        text: String,
        config: VoiceOutputConfig = VoiceOutputConfig(),
        queueMode: TtsQueueMode = TtsQueueMode.FLUSH
    )

    /**
     * 带 messageId 的合成播放：命中缓存则直接播放，合成完成后写回缓存。
     * 默认实现退化为普通 speak（不缓存），由支持缓存的实现覆写。
     */
    suspend fun speakWithCache(
        messageId: String,
        text: String,
        config: VoiceOutputConfig = VoiceOutputConfig(),
        queueMode: TtsQueueMode = TtsQueueMode.FLUSH
    ) {
        speak(text, config, queueMode)
    }

    fun stop()

    fun release()
}
