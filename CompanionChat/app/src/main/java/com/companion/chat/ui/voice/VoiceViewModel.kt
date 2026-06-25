package com.companion.chat.ui.voice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.companion.chat.data.engine.VoiceOutputState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 语音控制 ViewModel — 从 ChatViewModel 中抽出。
 *
 * Phase 3.2 优化：将语音 I/O、TTS、VAD 控制独立管理。
 */
class VoiceViewModel(application: Application) : AndroidViewModel(application) {

    private val _voiceOutputState = MutableStateFlow<VoiceOutputState>(VoiceOutputState.Idle)
    val voiceOutputState: StateFlow<VoiceOutputState> = _voiceOutputState.asStateFlow()

    private val _isVoiceInputActive = MutableStateFlow(false)
    val isVoiceInputActive: StateFlow<Boolean> = _isVoiceInputActive.asStateFlow()

    fun setVoiceInputActive(active: Boolean) {
        _isVoiceInputActive.value = active
    }

    fun setVoiceOutputState(state: VoiceOutputState) {
        _voiceOutputState.value = state
    }
}
