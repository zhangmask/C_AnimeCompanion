package com.companion.chat.ui.chat

internal sealed class VoiceTranscriptDecision {
    data object AutoSend : VoiceTranscriptDecision()
    data class HoldForUser(val message: String) : VoiceTranscriptDecision()
}

internal object VoiceDrivenChatPolicy {
    fun evaluateTranscript(
        transcript: String,
        isGenerating: Boolean,
        isEngineReady: Boolean
    ): VoiceTranscriptDecision {
        return when {
            transcript.isBlank() -> VoiceTranscriptDecision.HoldForUser("未识别到文本")
            isGenerating -> VoiceTranscriptDecision.HoldForUser("正在生成回复，请稍后再说")
            !isEngineReady -> VoiceTranscriptDecision.HoldForUser("模型未就绪，语音内容已保留在输入框")
            else -> VoiceTranscriptDecision.AutoSend
        }
    }
}
