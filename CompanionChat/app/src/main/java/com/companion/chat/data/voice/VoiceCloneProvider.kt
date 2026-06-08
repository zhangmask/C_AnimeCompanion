package com.companion.chat.data.voice

enum class VoiceCloneProvider {
    SYSTEM_TTS,
    HTTP_CLONE,
    LOCAL_CLONE_PLACEHOLDER,
    MOSS_TTS_NANO
}

data class VoiceCloneRequest(
    val text: String,
    val referenceAudioUri: String = "",
    val roleId: String = "",
    val displayName: String = ""
)

data class VoiceCloneResult(
    val provider: VoiceCloneProvider,
    val audioUri: String? = null,
    val fallbackToSystemTts: Boolean = false,
    val message: String = ""
)

interface VoiceCloneEngine {
    suspend fun synthesize(request: VoiceCloneRequest): Result<VoiceCloneResult>
}

class PlaceholderVoiceCloneEngine(
    private val provider: VoiceCloneProvider
) : VoiceCloneEngine {
    override suspend fun synthesize(request: VoiceCloneRequest): Result<VoiceCloneResult> {
        val message = when (provider) {
            VoiceCloneProvider.SYSTEM_TTS -> "使用系统 TTS"
            VoiceCloneProvider.HTTP_CLONE -> "HTTP 语音克隆后端未配置"
            VoiceCloneProvider.LOCAL_CLONE_PLACEHOLDER -> "本地语音克隆推理尚未接入"
            VoiceCloneProvider.MOSS_TTS_NANO -> "moss-tts-nano 模型未配置"
        }
        return Result.success(
            VoiceCloneResult(
                provider = provider,
                fallbackToSystemTts = provider != VoiceCloneProvider.SYSTEM_TTS,
                message = message
            )
        )
    }
}
