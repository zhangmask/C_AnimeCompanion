package com.companion.chat.data.engine

import kotlinx.coroutines.flow.Flow

sealed class VoiceInputEvent {
    data object WarmedUp : VoiceInputEvent()
    data class PartialResult(val text: String) : VoiceInputEvent()
    data class FinalResult(val text: String) : VoiceInputEvent()
    data object Listening : VoiceInputEvent()
    data object NotListening : VoiceInputEvent()
    data class Error(val message: String) : VoiceInputEvent()
}

interface VoiceInputEngine {
    val events: Flow<VoiceInputEvent>

    fun warmUp()

    fun startListening()

    fun stopListening()

    fun release()
}
