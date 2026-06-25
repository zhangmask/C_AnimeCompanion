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
import coil3.compose.AsyncImage
import com.companion.chat.data.role.RoleAvatarStore
import com.companion.chat.ui.theme.AvatarGradients
import com.companion.chat.ui.theme.BrandOutline
import com.companion.chat.ui.theme.BrandOutlineLight
import com.companion.chat.ui.theme.BrandOutlineVariant
import com.companion.chat.ui.theme.BrandPrimary
import com.companion.chat.ui.theme.BrandPrimaryContainer
import com.companion.chat.ui.theme.BrandSurfaceContainer
import com.companion.chat.data.voice.VoiceClipScanner
import com.companion.chat.locale.LocalLanguage
import com.companion.chat.locale.Strings
import com.companion.chat.locale.StringsKey

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
    val tabs = listOf(Strings.txt(StringsKey.role_tab_basic), Strings.txt(StringsKey.role_tab_persona), Strings.txt(StringsKey.role_tab_image), Strings.txt(StringsKey.role_tab_voice))

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
    val avatarStore = remember(sheetContext) { RoleAvatarStore(sheetContext) }
    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val persisted = avatarStore.persistUri(uri)
            if (persisted != null) {
                avatarImageUri = persisted
            }
        }
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
                            text = if (isEditing) Strings.txt(StringsKey.role_edit_card_title) else Strings.txt(StringsKey.role_create_card_title),
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
                                Strings.txt(StringsKey.close),
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
                                    label = Strings.txt(StringsKey.role_field_name),
                                    value = name,
                                    onValueChange = { name = it },
                                    placeholder = Strings.txt(StringsKey.role_name_placeholder)
                                )
                                FormField(
                                    label = Strings.txt(StringsKey.role_field_description),
                                    value = description,
                                    onValueChange = { description = it },
                                    placeholder = Strings.txt(StringsKey.role_desc_placeholder),
                                    maxLines = 2
                                )
                                FormField(
                                    label = Strings.txt(StringsKey.role_avatar_icon),
                                    value = avatar,
                                    onValueChange = { avatar = it },
                                    placeholder = Strings.txt(StringsKey.role_avatar_icon_hint)
                                )

                                // Avatar preview
                                Text(
                                    text = Strings.txt(StringsKey.role_avatar_preview),
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
                                        if (avatarImageUri.isNotBlank()) {
                                            AsyncImage(
                                                model = avatarImageUri,
                                                contentDescription = Strings.txt(StringsKey.role_field_avatar),
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(16.dp)),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        } else if (name.isNotBlank()) {
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
                                        text = if (name.isNotBlank()) name else Strings.txt(StringsKey.role_unnamed),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF1C1B1F)
                                    )
                                }

                                FormField(
                                    label = Strings.txt(StringsKey.role_field_opening),
                                    value = openingMessage,
                                    onValueChange = { openingMessage = it },
                                    placeholder = Strings.txt(StringsKey.role_opening_placeholder),
                                    maxLines = 3
                                )
                            }

                            1 -> {
                                // ── 人设 Tab ──
                                FormField(
                                    label = Strings.txt(StringsKey.role_persona_core),
                                    value = persona,
                                    onValueChange = { persona = it },
                                    placeholder = Strings.txt(StringsKey.role_persona_core_hint),
                                    maxLines = 4
                                )
                                FormField(
                                    label = Strings.txt(StringsKey.role_field_speaking_style),
                                    value = speakingStyle,
                                    onValueChange = { speakingStyle = it },
                                    placeholder = Strings.txt(StringsKey.role_speaking_style_hint),
                                    maxLines = 3
                                )
                                FormField(
                                    label = Strings.txt(StringsKey.role_background_story),
                                    value = background,
                                    onValueChange = { background = it },
                                    placeholder = Strings.txt(StringsKey.role_background_hint),
                                    maxLines = 4
                                )
                                FormField(
                                    label = Strings.txt(StringsKey.role_field_rules),
                                    value = rules,
                                    onValueChange = { rules = it },
                                    placeholder = Strings.txt(StringsKey.role_rules_hint),
                                    maxLines = 4
                                )
                                FormField(
                                    label = Strings.txt(StringsKey.role_field_taboos),
                                    value = taboos,
                                    onValueChange = { taboos = it },
                                    placeholder = Strings.txt(StringsKey.role_taboos_hint),
                                    maxLines = 3
                                )
                                FormField(
                                    label = Strings.txt(StringsKey.role_field_example_dialogue),
                                    value = exampleDialogue,
                                    onValueChange = { exampleDialogue = it },
                                    placeholder = Strings.txt(StringsKey.role_example_dialogue_hint),
                                    maxLines = 6
                                )
                            }

                            2 -> {
                                // ── 图片 Tab ──
                                Text(
                                    text = Strings.txt(StringsKey.role_avatar_image),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF49454F),
                                    modifier = Modifier.padding(start = 2.dp)
                                )

                                if (avatarImageUri.isNotBlank()) {
                                    // Show selected avatar with remove option
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(160.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFFF7F5FA)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = avatarImageUri,
                                            contentDescription = Strings.txt(StringsKey.role_avatar_preview),
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(12.dp)),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                        // Remove button
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(8.dp)
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(Color(0x88000000))
                                                .clickable { avatarImageUri = "" },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                Strings.txt(StringsKey.role_remove_avatar),
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                } else {
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
                                            .clickable { avatarPickerLauncher.launch("image/*") },
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
                                                text = Strings.txt(StringsKey.role_pick_avatar_image),
                                                fontSize = 13.sp,
                                                color = BrandPrimary
                                            )
                                        }
                                    }
                                }

                                FormField(
                                    label = Strings.txt(StringsKey.role_gallery_uri),
                                    value = galleryImageUris,
                                    onValueChange = { galleryImageUris = it },
                                    placeholder = "uri1, uri2, uri3",
                                    maxLines = 3
                                )
                                FormField(
                                    label = Strings.txt(StringsKey.role_field_image_style),
                                    value = imageStylePrompt,
                                    onValueChange = { imageStylePrompt = it },
                                    placeholder = "anime style, soft colors, warm lighting",
                                    maxLines = 3
                                )
                            }

                            3 -> {
                                // ── 语音 Tab ──
                                Text(
                                    text = Strings.txt(StringsKey.role_field_voice_mode),
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
                                        label = { Text(Strings.txt(StringsKey.role_voice_mode_system)) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = BrandPrimary,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                    FilterChip(
                                        selected = voiceMode == "CLONE",
                                        onClick = { voiceMode = "CLONE" },
                                        label = { Text(Strings.txt(StringsKey.role_voice_mode_clone)) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = BrandPrimary,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }

                                Text(
                                    text = Strings.txt(StringsKey.role_uploaded_clips),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF49454F),
                                    modifier = Modifier.padding(start = 2.dp, top = 4.dp)
                                )

                                if (voiceClips.isEmpty()) {
                                    Text(
                                        text = Strings.txt(StringsKey.role_no_clips_hint),
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
                                                contentDescription = if (isPlaying) Strings.txt(StringsKey.role_stop) else Strings.txt(StringsKey.role_play),
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
                                                contentDescription = Strings.txt(StringsKey.selected),
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
                                            text = Strings.txt(StringsKey.role_upload_new_clip),
                                            fontSize = 13.sp,
                                            color = Color(0xFF79747E)
                                        )
                                    }
                                }

                                Text(
                                    text = Strings.txt(StringsKey.role_clone_note),
                                    fontSize = 12.sp,
                                    color = Color(0xFF79747E),
                                    modifier = Modifier.padding(start = 2.dp)
                                )

                                FormField(
                                    label = Strings.txt(StringsKey.role_field_voice_display),
                                    value = voiceDisplayName,
                                    onValueChange = { voiceDisplayName = it },
                                    placeholder = Strings.txt(StringsKey.role_voice_display_hint)
                                )
                                FormField(
                                    label = Strings.txt(StringsKey.role_voice_package_uri),
                                    value = voiceProfileUri,
                                    onValueChange = { voiceProfileUri = it },
                                    placeholder = Strings.txt(StringsKey.role_voice_package_hint)
                                )

                                if (voiceMode == "CLONE" && voiceProfileUri.isBlank()) {
                                    Text(
                                        text = Strings.txt(StringsKey.role_default_moss_note),
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
                                Text(Strings.txt(StringsKey.cancel), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF49454F))
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
                                    Strings.txt(StringsKey.save),
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
