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
                        text = "语音设置",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
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
                text = "语音设置",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "语音输入默认使用本地 SenseVoice。默认使用 moss-tts-nano ONNX 模型进行语音克隆，缺模型或缺参考音频时自动回退系统 TTS。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            VoiceInfoRow("识别模式", voiceInputConfig.recognitionModeLabel)
            VoiceInfoRow("识别后端", voiceInputConfig.backend.displayName())
            VoiceInfoRow("模型目录", voiceInputConfig.localSenseVoiceModelDirectory.ifBlank { "未配置" })
            VoiceInfoRow("模型状态", localModelStatus.displayName())
            VoiceInfoRow("云 ASR", if (cloudAsrConfig.isConfigured) "已配置" else "未配置")
            VoiceInfoRow("云响应字段", cloudAsrConfig.responseTextFieldPath)
            VoiceInfoRow("MOSS 目录", voiceCloneConfig.mossModelDirectory.ifBlank { "未配置" })
            VoiceInfoRow("MOSS 状态", mossModelStatus.displayName())
            VoiceInfoRow("本地克隆", if (mossModelStatus is MossTtsNanoModelStatus.Ready) "MOSS TTS Nano（默认引擎）" else "回退系统 TTS")
            VoiceInfoRow("输出模式", "MOSS 本地克隆（默认）")
            VoiceInfoRow("默认音色", "moss_default_voice.wav")
            VoiceInfoRow("角色语音", "在角色管理中配置参考音频 URI、模式和显示名称")

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "语音输出",
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
                        text = "AI 回复自动朗读",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "AI 开始回复 0.5 秒后，按句子自动朗读",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.voiceOutputSettings.autoPlayTts,
                    onCheckedChange = { viewModel.toggleAutoPlayTts(it) }
                )
            }
        }
    }
}

private fun VoiceInputBackend.displayName(): String {
    return when (this) {
        VoiceInputBackend.LOCAL_SENSEVOICE -> "本地 SenseVoice ASR"
        VoiceInputBackend.CLOUD_HTTP_ASR -> "云 HTTP ASR"
    }
}

private fun LocalSenseVoiceModelStatus.displayName(): String {
    return when (this) {
        LocalSenseVoiceModelStatus.Ready -> "完整"
        LocalSenseVoiceModelStatus.DirectoryNotConfigured -> "本地 SenseVoice 模型未配置"
        is LocalSenseVoiceModelStatus.MissingFiles -> "文件缺失：${fileNames.joinToString()}"
    }
}

private fun MossTtsNanoModelStatus.displayName(): String {
    return when (this) {
        MossTtsNanoModelStatus.Ready -> "完整"
        MossTtsNanoModelStatus.DirectoryNotConfigured -> "moss-tts-nano 模型未配置"
        is MossTtsNanoModelStatus.InvalidConfig -> "配置无效：$message"
        is MossTtsNanoModelStatus.MissingFiles -> "文件缺失：${fileNames.joinToString()}"
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
