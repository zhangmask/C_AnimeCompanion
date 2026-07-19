package com.companion.chat.data.voice

import com.companion.chat.locale.AppLanguage
import com.companion.chat.locale.Strings
import com.companion.chat.locale.StringsKey

enum class VoiceInputBackend {
    LOCAL_SENSEVOICE,
    LOCAL_MNN_SENSEVOICE,
    CLOUD_HTTP_ASR;

    fun displayName(lang: AppLanguage): String = when (this) {
        LOCAL_SENSEVOICE -> Strings.get(lang, StringsKey.voice_local_sensevoice)
        LOCAL_MNN_SENSEVOICE -> Strings.get(lang, StringsKey.voice_local_sensevoice) + " (MNN)"
        CLOUD_HTTP_ASR -> Strings.get(lang, StringsKey.voice_cloud_http_asr)
    }
}

data class VoiceInputConfig(
    val backend: VoiceInputBackend = VoiceInputBackend.LOCAL_SENSEVOICE,
    val localSenseVoiceModelDirectory: String = ""
) {
    fun recognitionModeLabel(lang: AppLanguage): String = when (lang) {
        AppLanguage.ZH -> "本地多语言识别"
        AppLanguage.EN -> "Local multilingual ASR"
    }
}

sealed class LocalSenseVoiceModelStatus {
    data object Ready : LocalSenseVoiceModelStatus()
    data object DirectoryNotConfigured : LocalSenseVoiceModelStatus()
    data class MissingFiles(val fileNames: List<String>) : LocalSenseVoiceModelStatus()

    fun displayName(lang: AppLanguage): String = when (this) {
        is Ready -> Strings.get(lang, StringsKey.voice_ready)
        is DirectoryNotConfigured -> Strings.get(lang, StringsKey.voice_local_not_configured)
        is MissingFiles -> Strings.get(lang, StringsKey.voice_missing_files, fileNames.joinToString(", "))
    }
}
