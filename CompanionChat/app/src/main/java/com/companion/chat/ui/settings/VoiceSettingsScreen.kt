package com.companion.chat.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.companion.chat.data.voice.LocalSenseVoiceModelStatus
import com.companion.chat.data.voice.MossTtsNanoModelStatus
import com.companion.chat.data.voice.VoiceInputBackend
import com.companion.chat.locale.AppLanguage
import com.companion.chat.locale.LocalLanguage
import com.companion.chat.locale.Strings
import com.companion.chat.locale.StringsKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: VoiceSettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val voiceInputConfig = uiState.voiceInputConfig
    val localModelStatus = uiState.localModelStatus
    val cloudAsrConfig = uiState.cloudAsrConfig
    val voiceCloneConfig = uiState.voiceCloneConfig
    val mossModelStatus = uiState.mossModelStatus

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = Strings.txt(StringsKey.voice_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = Strings.txt(StringsKey.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = Strings.txt(StringsKey.voice_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = Strings.txt(StringsKey.voice_desc),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            VoiceInfoRow(Strings.txt(StringsKey.voice_recognition_mode), voiceInputConfig.recognitionModeLabel(LocalLanguage.current))
            VoiceInfoRow(Strings.txt(StringsKey.voice_recognition_backend), voiceInputConfig.backend.displayName(LocalLanguage.current))
            VoiceInfoRow(Strings.txt(StringsKey.voice_model_directory), voiceInputConfig.localSenseVoiceModelDirectory.ifBlank { Strings.txt(StringsKey.voice_not_configured) })
            VoiceInfoRow(Strings.txt(StringsKey.voice_model_status), localModelStatus.displayName(LocalLanguage.current))
            VoiceInfoRow(Strings.txt(StringsKey.voice_cloud_asr_label), if (cloudAsrConfig.isConfigured) Strings.txt(StringsKey.voice_configured) else Strings.txt(StringsKey.voice_not_configured))
            VoiceInfoRow(Strings.txt(StringsKey.voice_cloud_response_field), cloudAsrConfig.responseTextFieldPath)
            VoiceInfoRow(Strings.txt(StringsKey.voice_moss_directory), voiceCloneConfig.mossModelDirectory.ifBlank { Strings.txt(StringsKey.voice_not_configured) })
            VoiceInfoRow(Strings.txt(StringsKey.voice_moss_status), mossModelStatus.displayName(LocalLanguage.current))
            VoiceInfoRow(Strings.txt(StringsKey.voice_local_clone), if (mossModelStatus is MossTtsNanoModelStatus.Ready) Strings.txt(StringsKey.voice_moss_nano_default) else Strings.txt(StringsKey.voice_fallback_tts))
            VoiceInfoRow(Strings.txt(StringsKey.voice_output_mode), Strings.txt(StringsKey.voice_clone_default))
            VoiceInfoRow(Strings.txt(StringsKey.voice_default_timbre), "moss_default_voice.wav")
            VoiceInfoRow(Strings.txt(StringsKey.voice_role_voice), Strings.txt(StringsKey.voice_role_voice_hint))

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = Strings.txt(StringsKey.voice_output),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = Strings.txt(StringsKey.voice_auto_read),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = Strings.txt(StringsKey.voice_auto_read_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.voiceOutputSettings.autoPlayTts,
                    onCheckedChange = { viewModel.toggleAutoPlayTts(it) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = Strings.txt(StringsKey.voice_interrupt_on_new),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = Strings.txt(StringsKey.voice_interrupt_on_new_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.voiceOutputSettings.interruptTtsOnNewMessage,
                    onCheckedChange = { viewModel.toggleInterruptTtsOnNewMessage(it) }
                )
            }
        }
    }
}

private fun VoiceInputBackend.displayName(lang: AppLanguage): String {
    return when (this) {
        VoiceInputBackend.LOCAL_SENSEVOICE -> Strings.get(lang, StringsKey.voice_local_sensevoice)
        VoiceInputBackend.LOCAL_MNN_SENSEVOICE -> Strings.get(lang, StringsKey.voice_local_sensevoice) + " (MNN)"
        VoiceInputBackend.CLOUD_HTTP_ASR -> Strings.get(lang, StringsKey.voice_cloud_http_asr)
    }
}

private fun LocalSenseVoiceModelStatus.displayName(lang: AppLanguage): String {
    return when (this) {
        LocalSenseVoiceModelStatus.Ready -> Strings.get(lang, StringsKey.voice_ready)
        LocalSenseVoiceModelStatus.DirectoryNotConfigured -> Strings.get(lang, StringsKey.voice_local_not_configured)
        is LocalSenseVoiceModelStatus.MissingFiles -> Strings.get(lang, StringsKey.voice_missing_files, fileNames.joinToString())
    }
}

private fun MossTtsNanoModelStatus.displayName(lang: AppLanguage): String {
    return when (this) {
        MossTtsNanoModelStatus.Ready -> Strings.get(lang, StringsKey.voice_ready)
        MossTtsNanoModelStatus.DirectoryNotConfigured -> Strings.get(lang, StringsKey.voice_moss_not_configured)
        is MossTtsNanoModelStatus.InvalidConfig -> Strings.get(lang, StringsKey.voice_invalid_config, message)
        is MossTtsNanoModelStatus.MissingFiles -> Strings.get(lang, StringsKey.voice_missing_files, fileNames.joinToString())
    }
}

@Composable
private fun VoiceInfoRow(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
