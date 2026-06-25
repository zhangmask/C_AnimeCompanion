package com.companion.chat.ui.settings

import android.util.Log
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil3.compose.AsyncImage
import com.companion.chat.data.local.entity.RoleCard
import com.companion.chat.data.role.RoleAvatarStore
import com.companion.chat.data.voice.VoiceClipScanner
import com.companion.chat.locale.LocalLanguage
import com.companion.chat.locale.Strings
import com.companion.chat.locale.StringsKey

private enum class RoleEditorSection {
    BASIC, PERSONA, IMAGE, VOICE
}

@Composable
private fun sectionLabel(section: RoleEditorSection): String = when (section) {
    RoleEditorSection.BASIC   -> Strings.txt(StringsKey.role_tab_basic)
    RoleEditorSection.PERSONA -> Strings.txt(StringsKey.role_tab_persona)
    RoleEditorSection.IMAGE   -> Strings.txt(StringsKey.role_tab_image)
    RoleEditorSection.VOICE   -> Strings.txt(StringsKey.role_tab_voice)
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
    val canSave = name.isNotBlank() && persona.isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Title bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (roleCard == null) Strings.txt(StringsKey.role_create_card_title) else Strings.txt(StringsKey.role_edit_card_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, Strings.txt(StringsKey.close))
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Tabs
                TabRow(selectedTabIndex = selectedSectionIndex) {
                    sections.forEachIndexed { index, section ->
                        Tab(
                            selected = selectedSectionIndex == index,
                            onClick = { selectedSectionIndex = index },
                            text = { Text(sectionLabel(section), fontSize = MaterialTheme.typography.labelMedium.fontSize) }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
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

                Spacer(Modifier.height(12.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(Strings.txt(StringsKey.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        enabled = canSave,
                        onClick = {
                            Log.d("RoleCardEditor", "Save clicked: name=$name, persona=${persona.take(20)}")
                            onSave(
                                name, description, avatar, persona, speakingStyle, background,
                                rules, taboos, openingMessage, exampleDialogue,
                                avatarImageUri, galleryImageUris.lines().map { it.trim() }.filter { it.isNotBlank() },
                                imageStylePrompt, voiceProfileUri, voiceMode, voiceDisplayName
                            )
                        }
                    ) {
                        Text(Strings.txt(StringsKey.save))
                    }
                }
            }
        }
    }
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
    RoleCardField(Strings.txt(StringsKey.role_field_name), name, onNameChange)
    RoleCardField(Strings.txt(StringsKey.role_field_description), description, onDescriptionChange, minLines = 2)
    RoleCardField(Strings.txt(StringsKey.role_avatar_icon), avatar, onAvatarChange)
    RoleCardField(Strings.txt(StringsKey.role_field_opening), openingMessage, onOpeningMessageChange, minLines = 2)
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
    RoleCardField(Strings.txt(StringsKey.role_persona_core), persona, onPersonaChange, minLines = 4)
    RoleCardField(Strings.txt(StringsKey.role_field_speaking_style), speakingStyle, onSpeakingStyleChange, minLines = 2)
    RoleCardField(Strings.txt(StringsKey.role_background_story), background, onBackgroundChange, minLines = 2)
    RoleCardField(Strings.txt(StringsKey.role_field_rules), rules, onRulesChange, minLines = 2)
    RoleCardField(Strings.txt(StringsKey.role_field_taboos), taboos, onTaboosChange, minLines = 2)
    RoleCardField(Strings.txt(StringsKey.role_field_example_dialogue), exampleDialogue, onExampleDialogueChange, minLines = 3)
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
    val context = LocalContext.current
    val avatarStore = remember(context) { RoleAvatarStore(context) }
    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val persisted = avatarStore.persistUri(uri)
            if (persisted != null) {
                onAvatarImageUriChange(persisted)
            }
        }
    }

    Text(
        text = Strings.txt(StringsKey.role_avatar_image),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp)
    )

    if (avatarImageUri.isNotBlank()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 160.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = avatarImageUri,
                contentDescription = Strings.txt(StringsKey.role_avatar_preview),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0x88000000))
                    .clickable { onAvatarImageUriChange("") },
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(
                    1.5.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(12.dp)
                )
                .clickable { avatarPickerLauncher.launch("image/*") }
                .padding(12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Add,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "  " + Strings.txt(StringsKey.role_pick_avatar_image),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    RoleCardField(Strings.txt(StringsKey.role_gallery_uri), galleryImageUris, onGalleryImageUrisChange, minLines = 4)
    RoleCardField(Strings.txt(StringsKey.role_field_image_style), imageStylePrompt, onImageStylePromptChange, minLines = 3)
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
        Log.d("RoleCardEditor", "File picker returned: uri=$uri")
        if (uri != null) {
            val imported = scanner.importClipFromUri(uri)
            Log.d("RoleCardEditor", "Import result: $imported")
            if (imported != null) {
                clips = scanner.scanClips()
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
        text = Strings.txt(StringsKey.role_field_voice_mode),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("SYSTEM_TTS" to Strings.txt(StringsKey.role_voice_mode_system), "CLONE" to Strings.txt(StringsKey.role_voice_mode_clone)).forEach { (mode, label) ->
            FilterChip(
                selected = voiceMode == mode,
                onClick = { onVoiceModeChange(mode) },
                label = { Text(label) }
            )
        }
    }

    Text(
        text = Strings.txt(StringsKey.role_uploaded_clips),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )

    if (clips.isEmpty()) {
        Text(
            text = Strings.txt(StringsKey.role_no_clips_hint),
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
                    contentDescription = if (isPlaying) Strings.txt(StringsKey.role_stop) else Strings.txt(StringsKey.role_play),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

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

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = Strings.txt(StringsKey.selected),
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
            .clickable {
                Log.d("RoleCardEditor", "Upload button clicked, launching file picker")
                filePickerLauncher.launch("audio/*")
            }
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
            text = "  " + Strings.txt(StringsKey.role_upload_new_clip),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Text(
        text = Strings.txt(StringsKey.role_clone_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    RoleCardField(Strings.txt(StringsKey.role_field_voice_display), voiceDisplayName, onVoiceDisplayNameChange)
    RoleCardField(Strings.txt(StringsKey.role_voice_package_uri), voiceProfileUri, onVoiceProfileUriChange)
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
