package com.companion.chat.data.voice

class VoiceCloneProviderSelector(
    private val systemTtsEngine: VoiceCloneEngine = PlaceholderVoiceCloneEngine(VoiceCloneProvider.SYSTEM_TTS),
    private val httpCloneEngine: VoiceCloneEngine = PlaceholderVoiceCloneEngine(VoiceCloneProvider.HTTP_CLONE),
    private val localCloneEngine: VoiceCloneEngine = PlaceholderVoiceCloneEngine(VoiceCloneProvider.LOCAL_CLONE_PLACEHOLDER),
    private val mossTtsNanoEngine: VoiceCloneEngine = PlaceholderVoiceCloneEngine(VoiceCloneProvider.MOSS_TTS_NANO)
) {
    fun chooseProvider(modelStatus: MossTtsNanoModelStatus): VoiceCloneProvider {
        return when (modelStatus) {
            MossTtsNanoModelStatus.Ready -> VoiceCloneProvider.MOSS_TTS_NANO
            MossTtsNanoModelStatus.DirectoryNotConfigured,
            is MossTtsNanoModelStatus.InvalidConfig,
            is MossTtsNanoModelStatus.MissingFiles -> VoiceCloneProvider.SYSTEM_TTS
        }
    }

    suspend fun synthesize(
        provider: VoiceCloneProvider,
        request: VoiceCloneRequest
    ): Result<VoiceCloneResult> {
        return when (provider) {
            VoiceCloneProvider.SYSTEM_TTS -> systemTtsEngine.synthesize(request)
            VoiceCloneProvider.HTTP_CLONE -> httpCloneEngine.synthesize(request)
            VoiceCloneProvider.LOCAL_CLONE_PLACEHOLDER -> localCloneEngine.synthesize(request)
            VoiceCloneProvider.MOSS_TTS_NANO -> mossTtsNanoEngine.synthesize(request)
        }
    }
}
