package com.companion.chat.ui.chat.components

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.companion.chat.ui.theme.AvatarGradients
import com.companion.chat.ui.theme.BrandOutline
import com.companion.chat.ui.theme.BrandOutlineLight
import com.companion.chat.ui.theme.BrandOutlineVariant
import com.companion.chat.ui.theme.BrandPrimary
import com.companion.chat.ui.theme.BrandPrimaryContainer
import com.companion.chat.ui.theme.BrandSurfaceContainer
import com.companion.chat.data.voice.VoiceClipScanner

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoleCardEditorSheet(
    onDismiss: () -> Unit,
    onSave: (name: String, description: String, avatar: String, persona: String,
             speakingStyle: String, background: String, rules: String, taboos: String,
             openingMessage: String, exampleDialogue: String, avatarImageUri: String,
             galleryImageUris: List<String>, imageStylePrompt: String,
             voiceProfileUri: String, voiceMode: String, voiceDisplayName: String) -> Unit,
    existingName: String = "",
    existingDescription: String = "",
    existingAvatar: String = "",
    existingPersona: String = "",
    existingSpeakingStyle: String = "",
    existingBackground: String = "",
    existingRules: String = "",
    existingTaboos: String = "",
    existingOpeningMessage: String = "",
    existingExampleDialogue: String = "",
    existingAvatarImageUri: String = "",
    existingGalleryImageUris: List<String> = emptyList(),
    existingImageStylePrompt: String = "",
    existingVoiceProfileUri: String = "",
    existingVoiceMode: String = "CLONE",
    existingVoiceDisplayName: String = "",
    isEditing: Boolean = false
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("基础", "人设", "图片", "语音")

    // Tab 0 - 基础
    var name by remember { mutableStateOf(existingName) }
    var description by remember { mutableStateOf(existingDescription) }
    var avatar by remember { mutableStateOf(existingAvatar) }
    var openingMessage by remember { mutableStateOf(existingOpeningMessage) }

    // Tab 1 - 人设
    var persona by remember { mutableStateOf(existingPersona) }
    var speakingStyle by remember { mutableStateOf(existingSpeakingStyle) }
    var background by remember { mutableStateOf(existingBackground) }
    var rules by remember { mutableStateOf(existingRules) }
    var taboos by remember { mutableStateOf(existingTaboos) }
    var exampleDialogue by remember { mutableStateOf(existingExampleDialogue) }

    // Tab 2 - 图片
    var avatarImageUri by remember { mutableStateOf(existingAvatarImageUri) }
    var galleryImageUris by remember { mutableStateOf(existingGalleryImageUris.joinToString(", ")) }
    var imageStylePrompt by remember { mutableStateOf(existingImageStylePrompt) }

    // Tab 3 - 语音
    val sheetContext = LocalContext.current
    val voiceClipScanner = remember(sheetContext) { VoiceClipScanner(sheetContext) }
    var voiceClips by remember { mutableStateOf(voiceClipScanner.scanClips()) }
    var voiceMode by remember { mutableStateOf(existingVoiceMode) }
    var voiceDisplayName by remember { mutableStateOf(existingVoiceDisplayName) }
    var voiceProfileUri by remember { mutableStateOf(existingVoiceProfileUri) }
    var voicePlayingIndex by remember { mutableIntStateOf(-1) }
    var voiceSelectedIndex by remember(existingVoiceProfileUri) {
        mutableIntStateOf(
            voiceClips.indexOfFirst { existingVoiceProfileUri == it.uri }.coerceAtLeast(
                if (existingVoiceProfileUri.isBlank()) -1 else 0
            )
        )
    }
    val sheetFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val imported = voiceClipScanner.importClipFromUri(uri)
            if (imported != null) {
                voiceClips = voiceClipScanner.scanClips()
                val newIndex = voiceClips.indexOfFirst { it.uri == imported.uri }
                if (newIndex >= 0) {
                    voiceSelectedIndex = newIndex
                    voiceProfileUri = imported.uri
                    if (voiceDisplayName.isBlank()) {
                        voiceDisplayName = imported.displayName
                    }
                }
            }
        }
    }

    val canSave = name.isNotBlank() && persona.isNotBlank()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x59000000))
                .clickable { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) { },
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = Color.White
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // ── Header ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEditing) "编辑角色卡" else "创建角色卡",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(BrandSurfaceContainer)
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Close,
                                "关闭",
                                tint = Color(0xFF49454F),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // ── Tabs ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            val isSelected = selectedTab == index
                            val textColor by animateColorAsState(
                                targetValue = if (isSelected) BrandPrimary else BrandOutline,
                                label = "tabColor"
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedTab = index },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = tab,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = textColor
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .width(if (isSelected) 24.dp else 0.dp)
                                        .height(2.5.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (isSelected) BrandPrimary else Color.Transparent)
                                )
                            }
                        }
                    }

                    // Divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(BrandOutlineLight)
                    )

                    // ── Content ──
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        when (selectedTab) {
                            0 -> {
                                // ── 基础 Tab ──
                                FormField(
                                    label = "名称",
                                    value = name,
                                    onValueChange = { name = it },
                                    placeholder = "给角色取个名字"
                                )
                                FormField(
                                    label = "简介",
                                    value = description,
                                    onValueChange = { description = it },
                                    placeholder = "温柔治愈的邻家女孩",
                                    maxLines = 2
                                )
                                FormField(
                                    label = "头像图标",
                                    value = avatar,
                                    onValueChange = { avatar = it },
                                    placeholder = "图标标识符，如 person、star、heart"
                                )

                                // Avatar preview
                                Text(
                                    text = "头像预览",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF49454F)
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(AvatarGradients[0]),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (name.isNotBlank()) {
                                            Text(
                                                text = name.first().toString(),
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.Person,
                                                null,
                                                tint = Color.White,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (name.isNotBlank()) name else "未命名",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF1C1B1F)
                                    )
                                }

                                FormField(
                                    label = "开场白",
                                    value = openingMessage,
                                    onValueChange = { openingMessage = it },
                                    placeholder = "你好呀~今天想聊什么呢？",
                                    maxLines = 3
                                )
                            }

                            1 -> {
                                // ── 人设 Tab ──
                                FormField(
                                    label = "核心人设 *",
                                    value = persona,
                                    onValueChange = { persona = it },
                                    placeholder = "描述角色的核心性格和行为特点",
                                    maxLines = 4
                                )
                                FormField(
                                    label = "说话风格",
                                    value = speakingStyle,
                                    onValueChange = { speakingStyle = it },
                                    placeholder = "温暖亲切，偶尔撒娇，喜欢用语气词",
                                    maxLines = 3
                                )
                                FormField(
                                    label = "背景故事",
                                    value = background,
                                    onValueChange = { background = it },
                                    placeholder = "角色的来历和背景设定",
                                    maxLines = 4
                                )
                                FormField(
                                    label = "规则",
                                    value = rules,
                                    onValueChange = { rules = it },
                                    placeholder = "角色必须遵守的行为规则和约束",
                                    maxLines = 4
                                )
                                FormField(
                                    label = "禁忌",
                                    value = taboos,
                                    onValueChange = { taboos = it },
                                    placeholder = "角色绝对不能触碰的话题或行为",
                                    maxLines = 3
                                )
                                FormField(
                                    label = "示例对话",
                                    value = exampleDialogue,
                                    onValueChange = { exampleDialogue = it },
                                    placeholder = "用户: 你好\n角色: 你好呀~很高兴见到你！",
                                    maxLines = 6
                                )
                            }

                            2 -> {
                                // ── 图片 Tab ──
                                FormField(
                                    label = "头像图片 URI",
                                    value = avatarImageUri,
                                    onValueChange = { avatarImageUri = it },
                                    placeholder = "content://media/... 或 https://..."
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            width = 1.5.dp,
                                            color = BrandOutlineVariant,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { /* TODO: pick image */ },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            null,
                                            tint = BrandPrimary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Text(
                                            text = "选择头像图片",
                                            fontSize = 13.sp,
                                            color = BrandPrimary
                                        )
                                    }
                                }

                                FormField(
                                    label = "相册图片 URI（逗号分隔）",
                                    value = galleryImageUris,
                                    onValueChange = { galleryImageUris = it },
                                    placeholder = "uri1, uri2, uri3",
                                    maxLines = 3
                                )
                                FormField(
                                    label = "图片风格提示词",
                                    value = imageStylePrompt,
                                    onValueChange = { imageStylePrompt = it },
                                    placeholder = "anime style, soft colors, warm lighting",
                                    maxLines = 3
                                )
                            }

                            3 -> {
                                // ── 语音 Tab ──
                                Text(
                                    text = "语音模式",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF49454F),
                                    modifier = Modifier.padding(start = 2.dp)
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = voiceMode == "SYSTEM_TTS",
                                        onClick = { voiceMode = "SYSTEM_TTS" },
                                        label = { Text("系统 TTS") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = BrandPrimary,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                    FilterChip(
                                        selected = voiceMode == "CLONE",
                                        onClick = { voiceMode = "CLONE" },
                                        label = { Text("MOSS 本地克隆") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = BrandPrimary,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }

                                Text(
                                    text = "已上传的语音片段",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF49454F),
                                    modifier = Modifier.padding(start = 2.dp, top = 4.dp)
                                )

                                if (voiceClips.isEmpty()) {
                                    Text(
                                        text = "暂无语音片段，请先上传一段参考音频（WAV 格式最佳）。",
                                        fontSize = 12.sp,
                                        color = Color(0xFF79747E),
                                        modifier = Modifier.padding(start = 2.dp)
                                    )
                                }

                                voiceClips.forEachIndexed { index, clip ->
                                    val isSelected = voiceSelectedIndex == index
                                    val isPlaying = voicePlayingIndex == index
                                    val bgColor by animateColorAsState(
                                        targetValue = if (isSelected) BrandPrimaryContainer else Color(0xFFF7F5FA),
                                        label = "voiceClipBg"
                                    )
                                    val borderClr by animateColorAsState(
                                        targetValue = if (isSelected) BrandPrimary else Color.Transparent,
                                        label = "voiceClipBorder"
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(bgColor)
                                            .border(1.5.dp, borderClr, RoundedCornerShape(12.dp))
                                            .clickable {
                                                voiceSelectedIndex = index
                                                voiceProfileUri = clip.uri
                                                if (voiceDisplayName.isBlank()) {
                                                    voiceDisplayName = clip.displayName
                                                }
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Play button
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isPlaying) Color(0xFFD4688C) else BrandPrimary
                                                )
                                                .clickable {
                                                    voicePlayingIndex = if (isPlaying) -1 else index
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                contentDescription = if (isPlaying) "停止" else "播放",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // Clip info
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = clip.displayName,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF1C1B1F)
                                            )
                                            Text(
                                                text = clip.uploadedLabel,
                                                fontSize = 11.sp,
                                                color = Color(0xFF79747E)
                                            )
                                        }

                                        // Selected indicator
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "已选中",
                                                tint = BrandPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                // Upload button
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            width = 1.5.dp,
                                            color = BrandOutlineVariant,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { sheetFilePickerLauncher.launch("audio/*") }
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Upload,
                                            null,
                                            tint = Color(0xFF79747E),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "上传新语音片段",
                                            fontSize = 13.sp,
                                            color = Color(0xFF79747E)
                                        )
                                    }
                                }

                                Text(
                                    text = "选中的语音片段将作为该角色的默认语音。克隆后端不可用时会自动回退系统 TTS。",
                                    fontSize = 12.sp,
                                    color = Color(0xFF79747E),
                                    modifier = Modifier.padding(start = 2.dp)
                                )

                                FormField(
                                    label = "语音显示名称",
                                    value = voiceDisplayName,
                                    onValueChange = { voiceDisplayName = it },
                                    placeholder = "温柔女声 / 磁性男声"
                                )
                                FormField(
                                    label = "语音包 URI",
                                    value = voiceProfileUri,
                                    onValueChange = { voiceProfileUri = it },
                                    placeholder = "自动从选中片段填入，也可手动输入"
                                )

                                if (voiceMode == "CLONE" && voiceProfileUri.isBlank()) {
                                    Text(
                                        text = "未配置时将使用默认 MOSS 音色",
                                        fontSize = 12.sp,
                                        color = Color(0xFF79747E),
                                        modifier = Modifier.padding(start = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // ── Actions ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp, top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Cancel
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clickable { onDismiss() },
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, BrandOutlineVariant)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("取消", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF49454F))
                            }
                        }
                        // Save
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clickable(enabled = canSave) {
                                    if (canSave) {
                                        val parsedGalleryUris = galleryImageUris
                                            .split(",")
                                            .map { it.trim() }
                                            .filter { it.isNotBlank() }
                                        onSave(
                                            name.trim(),
                                            description.trim(),
                                            avatar.trim(),
                                            persona.trim(),
                                            speakingStyle.trim(),
                                            background.trim(),
                                            rules.trim(),
                                            taboos.trim(),
                                            openingMessage.trim(),
                                            exampleDialogue.trim(),
                                            avatarImageUri.trim(),
                                            parsedGalleryUris,
                                            imageStylePrompt.trim(),
                                            voiceProfileUri.trim(),
                                            voiceMode,
                                            voiceDisplayName.trim()
                                        )
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (canSave) BrandPrimary else BrandPrimaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "保存",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (canSave) Color.White else BrandOutlineVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    maxLines: Int = 1
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF49454F),
            modifier = Modifier.padding(start = 2.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, fontSize = 13.sp) },
            maxLines = maxLines,
            minLines = if (maxLines > 1) 2 else 1,
            shape = RoundedCornerShape(10.dp),
            textStyle = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandPrimary,
                unfocusedBorderColor = BrandOutlineVariant,
                focusedContainerColor = BrandSurfaceContainer,
                unfocusedContainerColor = BrandSurfaceContainer
            )
        )
    }
}
