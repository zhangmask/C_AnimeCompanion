package com.companion.chat.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.companion.chat.data.local.entity.RoleCard
import com.companion.chat.data.voice.VoiceClipScanner

private enum class RoleEditorSection(val label: String) {
    BASIC("基础"),
    PERSONA("人设"),
    IMAGE("图片"),
    VOICE("语音")
}

@Composable
fun RoleCardEditorDialog(
    roleCard: RoleCard? = null,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        description: String,
        avatar: String,
        persona: String,
        speakingStyle: String,
        background: String,
        rules: String,
        taboos: String,
        openingMessage: String,
        exampleDialogue: String,
        avatarImageUri: String,
        galleryImageUris: List<String>,
        imageStylePrompt: String,
        voiceProfileUri: String,
        voiceMode: String,
        voiceDisplayName: String
    ) -> Unit
) {
    var selectedSectionIndex by remember(roleCard) { mutableIntStateOf(0) }
    var name by remember(roleCard) { mutableStateOf(roleCard?.name.orEmpty()) }
    var description by remember(roleCard) { mutableStateOf(roleCard?.description.orEmpty()) }
    var avatar by remember(roleCard) { mutableStateOf(roleCard?.avatar.orEmpty().ifBlank { "person" }) }
    var persona by remember(roleCard) { mutableStateOf(roleCard?.persona.orEmpty()) }
    var speakingStyle by remember(roleCard) { mutableStateOf(roleCard?.speakingStyle.orEmpty()) }
    var background by remember(roleCard) { mutableStateOf(roleCard?.background.orEmpty()) }
    var rules by remember(roleCard) { mutableStateOf(roleCard?.rules.orEmpty()) }
    var taboos by remember(roleCard) { mutableStateOf(roleCard?.taboos.orEmpty()) }
    var openingMessage by remember(roleCard) { mutableStateOf(roleCard?.openingMessage.orEmpty()) }
    var exampleDialogue by remember(roleCard) { mutableStateOf(roleCard?.exampleDialogue.orEmpty()) }
    var avatarImageUri by remember(roleCard) { mutableStateOf(roleCard?.avatarImageUri.orEmpty()) }
    var galleryImageUris by remember(roleCard) {
        mutableStateOf(roleCard?.galleryImageUris?.joinToString("\n").orEmpty())
    }
    var imageStylePrompt by remember(roleCard) { mutableStateOf(roleCard?.imageStylePrompt.orEmpty()) }
    var voiceProfileUri by remember(roleCard) { mutableStateOf(roleCard?.voiceProfileUri.orEmpty()) }
    var voiceMode by remember(roleCard) { mutableStateOf(roleCard?.voiceMode ?: "CLONE") }
    var voiceDisplayName by remember(roleCard) { mutableStateOf(roleCard?.voiceDisplayName.orEmpty()) }
    val sections = RoleEditorSection.entries

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (roleCard == null) "新建角色卡" else "编辑角色卡",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 540.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TabRow(selectedTabIndex = selectedSectionIndex) {
                    sections.forEachIndexed { index, section ->
                        Tab(
                            selected = selectedSectionIndex == index,
                            onClick = { selectedSectionIndex = index },
                            text = { Text(section.label) }
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (sections[selectedSectionIndex]) {
                        RoleEditorSection.BASIC -> BasicSection(
                            name = name,
                            onNameChange = { name = it },
                            description = description,
                            onDescriptionChange = { description = it },
                            avatar = avatar,
                            onAvatarChange = { avatar = it },
                            openingMessage = openingMessage,
                            onOpeningMessageChange = { openingMessage = it }
                        )
                        RoleEditorSection.PERSONA -> PersonaSection(
                            persona = persona,
                            onPersonaChange = { persona = it },
                            speakingStyle = speakingStyle,
                            onSpeakingStyleChange = { speakingStyle = it },
                            background = background,
                            onBackgroundChange = { background = it },
                            rules = rules,
                            onRulesChange = { rules = it },
                            taboos = taboos,
                            onTaboosChange = { taboos = it },
                            exampleDialogue = exampleDialogue,
                            onExampleDialogueChange = { exampleDialogue = it }
                        )
                        RoleEditorSection.IMAGE -> ImageSection(
                            avatarImageUri = avatarImageUri,
                            onAvatarImageUriChange = { avatarImageUri = it },
                            galleryImageUris = galleryImageUris,
                            onGalleryImageUrisChange = { galleryImageUris = it },
                            imageStylePrompt = imageStylePrompt,
                            onImageStylePromptChange = { imageStylePrompt = it }
                        )
                        RoleEditorSection.VOICE -> VoiceSection(
                            voiceProfileUri = voiceProfileUri,
                            onVoiceProfileUriChange = { voiceProfileUri = it },
                            voiceMode = voiceMode,
                            onVoiceModeChange = { voiceMode = it },
                            voiceDisplayName = voiceDisplayName,
                            onVoiceDisplayNameChange = { voiceDisplayName = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && persona.isNotBlank(),
                onClick = {
                    onSave(
                        name,
                        description,
                        avatar,
                        persona,
                        speakingStyle,
                        background,
                        rules,
                        taboos,
                        openingMessage,
                        exampleDialogue,
                        avatarImageUri,
                        galleryImageUris.lines().map { it.trim() }.filter { it.isNotBlank() },
                        imageStylePrompt,
                        voiceProfileUri,
                        voiceMode,
                        voiceDisplayName
                    )
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun BasicSection(
    name: String,
    onNameChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    avatar: String,
    onAvatarChange: (String) -> Unit,
    openingMessage: String,
    onOpeningMessageChange: (String) -> Unit
) {
    RoleCardField("名称", name, onNameChange)
    RoleCardField("简介", description, onDescriptionChange, minLines = 2)
    RoleCardField("头像/图标标识", avatar, onAvatarChange)
    RoleCardField("开场白", openingMessage, onOpeningMessageChange, minLines = 2)
}

@Composable
private fun PersonaSection(
    persona: String,
    onPersonaChange: (String) -> Unit,
    speakingStyle: String,
    onSpeakingStyleChange: (String) -> Unit,
    background: String,
    onBackgroundChange: (String) -> Unit,
    rules: String,
    onRulesChange: (String) -> Unit,
    taboos: String,
    onTaboosChange: (String) -> Unit,
    exampleDialogue: String,
    onExampleDialogueChange: (String) -> Unit
) {
    RoleCardField("核心人设", persona, onPersonaChange, minLines = 4)
    RoleCardField("说话风格", speakingStyle, onSpeakingStyleChange, minLines = 2)
    RoleCardField("背景设定", background, onBackgroundChange, minLines = 2)
    RoleCardField("行为规则", rules, onRulesChange, minLines = 2)
    RoleCardField("禁止项", taboos, onTaboosChange, minLines = 2)
    RoleCardField("示例对话", exampleDialogue, onExampleDialogueChange, minLines = 3)
}

@Composable
private fun ImageSection(
    avatarImageUri: String,
    onAvatarImageUriChange: (String) -> Unit,
    galleryImageUris: String,
    onGalleryImageUrisChange: (String) -> Unit,
    imageStylePrompt: String,
    onImageStylePromptChange: (String) -> Unit
) {
    RoleCardField("头像图片 URI", avatarImageUri, onAvatarImageUriChange)
    RoleCardField("图库图片 URI（一行一个）", galleryImageUris, onGalleryImageUrisChange, minLines = 4)
    RoleCardField("图片风格提示词", imageStylePrompt, onImageStylePromptChange, minLines = 3)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceSection(
    voiceProfileUri: String,
    onVoiceProfileUriChange: (String) -> Unit,
    voiceMode: String,
    onVoiceModeChange: (String) -> Unit,
    voiceDisplayName: String,
    onVoiceDisplayNameChange: (String) -> Unit
) {
    val context = LocalContext.current
    val scanner = remember(context) { VoiceClipScanner(context) }
    var clips by remember { mutableStateOf(scanner.scanClips()) }
    var playingIndex by remember { mutableIntStateOf(-1) }
    var selectedIndex by remember(voiceProfileUri) {
        mutableIntStateOf(
            clips.indexOfFirst { voiceProfileUri == it.uri }.coerceAtLeast(
                if (voiceProfileUri.isBlank()) -1 else 0
            )
        )
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val imported = scanner.importClipFromUri(uri)
            if (imported != null) {
                clips = scanner.scanClips()
                // Auto-select the newly imported clip
                val newIndex = clips.indexOfFirst { it.uri == imported.uri }
                if (newIndex >= 0) {
                    selectedIndex = newIndex
                    onVoiceProfileUriChange(imported.uri)
                    if (voiceDisplayName.isBlank()) {
                        onVoiceDisplayNameChange(imported.displayName)
                    }
                }
            }
        }
    }

    Text(
        text = "语音模式",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("SYSTEM_TTS" to "系统 TTS", "CLONE" to "MOSS 本地克隆").forEach { (mode, label) ->
            FilterChip(
                selected = voiceMode == mode,
                onClick = { onVoiceModeChange(mode) },
                label = { Text(label) }
            )
        }
    }

    Text(
        text = "已上传的语音片段",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )

    if (clips.isEmpty()) {
        Text(
            text = "暂无语音片段，请先上传一段参考音频（WAV 格式最佳）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }

    clips.forEachIndexed { index, clip ->
        val isSelected = selectedIndex == index
        val isPlaying = playingIndex == index
        val bgColor by animateColorAsState(
            targetValue = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            label = "clipBg"
        )
        val borderColor by animateColorAsState(
            targetValue = if (isSelected)
                MaterialTheme.colorScheme.primary
            else
                Color.Transparent,
            label = "clipBorder"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
                .clickable {
                    selectedIndex = index
                    onVoiceProfileUriChange(clip.uri)
                    if (voiceDisplayName.isBlank()) {
                        onVoiceDisplayNameChange(clip.displayName)
                    }
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Play/Pause button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isPlaying) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.primary
                    )
                    .clickable {
                        playingIndex = if (isPlaying) -1 else index
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "停止" else "播放",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Clip info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = clip.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = clip.uploadedLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Selected check
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已选中",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    // Upload button
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                1.5.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(12.dp)
            )
            .clickable { filePickerLauncher.launch("audio/*") }
            .padding(12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Upload,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = "  上传新语音片段",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Text(
        text = "选中的语音片段将作为该角色的默认语音。克隆后端不可用时会自动回退系统 TTS。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    RoleCardField("语音显示名称", voiceDisplayName, onVoiceDisplayNameChange)
    RoleCardField("语音参考音频 URI", voiceProfileUri, onVoiceProfileUriChange)
}

@Composable
private fun RoleCardField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        minLines = minLines
    )
}
