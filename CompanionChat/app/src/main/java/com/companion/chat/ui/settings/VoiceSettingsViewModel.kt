package com.companion.chat.ui.settings

import androidx.lifecycle.ViewModel
import com.companion.chat.data.voice.CloudAsrConfig
import com.companion.chat.data.voice.CloudAsrConfigRepository
import com.companion.chat.data.voice.LocalSenseVoiceModelStatus
import com.companion.chat.data.voice.MossTtsNanoModelStatus
import com.companion.chat.data.voice.VoiceCloneConfig
import com.companion.chat.data.voice.VoiceCloneConfigRepository
import com.companion.chat.data.voice.VoiceInputConfig
import com.companion.chat.data.voice.VoiceInputConfigRepository
import com.companion.chat.data.voice.VoiceOutputSettings
import com.companion.chat.data.voice.VoiceOutputSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class VoiceSettingsUiState(
    val voiceInputConfig: VoiceInputConfig,
    val localModelStatus: LocalSenseVoiceModelStatus,
    val cloudAsrConfig: CloudAsrConfig,
    val voiceCloneConfig: VoiceCloneConfig,
    val mossModelStatus: MossTtsNanoModelStatus,
    val voiceOutputSettings: VoiceOutputSettings = VoiceOutputSettings()
)

class VoiceSettingsViewModel(
    private val voiceInputConfigRepository: VoiceInputConfigRepository,
    private val cloudAsrConfigRepository: CloudAsrConfigRepository,
    private val voiceCloneConfigRepository: VoiceCloneConfigRepository,
    private val voiceOutputSettingsRepository: VoiceOutputSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildUiState())
    val uiState: StateFlow<VoiceSettingsUiState> = _uiState.asStateFlow()

    fun refresh() {
        _uiState.update { buildUiState() }
    }

    fun toggleAutoPlayTts(enabled: Boolean) {
        val current = _uiState.value.voiceOutputSettings
        val updated = current.copy(autoPlayTts = enabled)
        voiceOutputSettingsRepository.updateSettings(updated)
        _uiState.update { it.copy(voiceOutputSettings = updated) }
    }

    private fun buildUiState(): VoiceSettingsUiState {
        val voiceInputConfig = voiceInputConfigRepository.getConfig()
        val cloudAsrConfig = cloudAsrConfigRepository.getConfig()
        val voiceCloneConfig = voiceCloneConfigRepository.getConfig()
        return VoiceSettingsUiState(
            voiceInputConfig = voiceInputConfig,
            localModelStatus = voiceInputConfigRepository.getLocalSenseVoiceModelStatus(voiceInputConfig),
            cloudAsrConfig = cloudAsrConfig,
            voiceCloneConfig = voiceCloneConfig,
            mossModelStatus = voiceCloneConfigRepository.getMossModelStatus(voiceCloneConfig),
            voiceOutputSettings = voiceOutputSettingsRepository.getSettings()
        )
    }
}
