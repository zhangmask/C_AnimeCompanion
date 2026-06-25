package com.companion.chat.ui.chat

import com.companion.chat.locale.AppLanguage
import com.companion.chat.locale.Strings
import com.companion.chat.locale.StringsKey

internal sealed class VoiceTranscriptDecision {
    data object AutoSend : VoiceTranscriptDecision()
    data class HoldForUser(val message: String) : VoiceTranscriptDecision()
}

internal object VoiceDrivenChatPolicy {
    fun evaluateTranscript(
        transcript: String,
        isGenerating: Boolean,
        isEngineReady: Boolean,
        lang: AppLanguage = AppLanguage.DEFAULT
    ): VoiceTranscriptDecision {
        return when {
            transcript.isBlank() -> VoiceTranscriptDecision.HoldForUser(Strings.get(lang, StringsKey.voice_policy_no_text))
            isGenerating -> VoiceTranscriptDecision.HoldForUser(Strings.get(lang, StringsKey.voice_policy_generating))
            !isEngineReady -> VoiceTranscriptDecision.HoldForUser(Strings.get(lang, StringsKey.voice_policy_not_ready))
            else -> VoiceTranscriptDecision.AutoSend
        }
    }
}
