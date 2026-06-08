package com.companion.chat.data.voice

enum class VoiceInputBackend {
    LOCAL_SENSEVOICE,
    CLOUD_HTTP_ASR
}

data class VoiceInputConfig(
    val backend: VoiceInputBackend = VoiceInputBackend.LOCAL_SENSEVOICE,
    val localSenseVoiceModelDirectory: String = ""
) {
    val recognitionModeLabel: String = "本地多语言识别"
}

sealed class LocalSenseVoiceModelStatus {
    data object Ready : LocalSenseVoiceModelStatus()
    data object DirectoryNotConfigured : LocalSenseVoiceModelStatus()
    data class MissingFiles(val fileNames: List<String>) : LocalSenseVoiceModelStatus()
}
