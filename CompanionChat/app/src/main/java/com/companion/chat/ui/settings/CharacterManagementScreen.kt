package com.companion.chat.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.companion.chat.data.local.entity.RoleCard
import com.companion.chat.ui.chat.components.RoleCardEditorSheet
import com.companion.chat.locale.LocalLanguage
import com.companion.chat.locale.Strings
import com.companion.chat.locale.StringsKey
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterManagementScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onActivateRoleCard: suspend (Long) -> Unit = {},
    onStartChat: (Long) -> Unit = {},
    onEditRoleCard: (Long) -> Unit = {},
    roleManagementViewModel: RoleManagementViewModel = viewModel(),
    editRoleId: Long? = null
) {
    val uiState by roleManagementViewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var editingRoleCard by remember { mutableStateOf<RoleCard?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var deletingRoleCard by remember { mutableStateOf<RoleCard?>(null) }
    var hasAutoOpenedForEdit by remember { mutableStateOf(false) }

    LaunchedEffect(editRoleId, uiState.roleCards) {
        if (editRoleId != null && editRoleId > 0 && !hasAutoOpenedForEdit) {
            val roleCard = uiState.roleCards.find { it.id == editRoleId }
                ?: uiState.activeRoleCard?.takeIf { it.id == editRoleId }
            if (roleCard != null) {
                editingRoleCard = roleCard
                hasAutoOpenedForEdit = true
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = Strings.txt(StringsKey.char_mgmt_title),
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
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = Strings.txt(StringsKey.char_mgmt_new)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = Strings.txt(StringsKey.settings_sub_characters),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            uiState.activeRoleCard?.let { activeRole ->
                item {
                    SectionTitle(Strings.txt(StringsKey.char_mgmt_set_active))
                }
                item {
                    RoleCardItem(
                        roleCard = activeRole,
                        isActive = true,
                        onActivate = {},
                        onStartChat = { onStartChat(activeRole.id) },
                        onEdit = { onEditRoleCard(activeRole.id) },
                        onDelete = if (activeRole.isBuiltIn) null else ({ deletingRoleCard = activeRole })
                    )
                }
            }

            item {
                SectionTitle(Strings.txt(StringsKey.char_mgmt_title))
            }

            if (uiState.roleCards.isEmpty()) {
                item {
                    EmptyState(
                        title = Strings.txt(StringsKey.char_mgmt_empty),
                        description = Strings.txt(StringsKey.drawer_no_character_hint)
                    )
                }
            } else {
                items(uiState.roleCards, key = { it.id }) { roleCard ->
                    RoleCardItem(
                        roleCard = roleCard,
                        isActive = roleCard.isActive,
                        onActivate = {
                            scope.launch {
                                onActivateRoleCard(roleCard.id)
                                roleManagementViewModel.refresh()
                            }
                        },
                        onStartChat = { onStartChat(roleCard.id) },
                        onEdit = { onEditRoleCard(roleCard.id) },
                        onDelete = if (roleCard.isBuiltIn) null else ({ deletingRoleCard = roleCard })
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        RoleCardEditorSheet(
            onDismiss = { showCreateDialog = false },
            onSave = { name, description, avatar, persona, speakingStyle, background, rules, taboos, openingMessage, exampleDialogue, avatarImageUri, galleryImageUris, imageStylePrompt, voiceProfileUri, voiceMode, voiceDisplayName, tags ->
                roleManagementViewModel.createRoleCard(
                    name = name,
                    description = description,
                    avatar = avatar,
                    persona = persona,
                    speakingStyle = speakingStyle,
                    background = background,
                    rules = rules,
                    taboos = taboos,
                    openingMessage = openingMessage,
                    exampleDialogue = exampleDialogue,
                    avatarImageUri = avatarImageUri,
                    galleryImageUris = galleryImageUris,
                    imageStylePrompt = imageStylePrompt,
                    voiceProfileUri = voiceProfileUri,
                    voiceMode = voiceMode,
                    voiceDisplayName = voiceDisplayName,
                    tags = tags
                )
                showCreateDialog = false
            }
        )
    }

    editingRoleCard?.let { roleCard ->
        RoleCardEditorSheet(
            onDismiss = { editingRoleCard = null },
            onSave = { name, description, avatar, persona, speakingStyle, background, rules, taboos, openingMessage, exampleDialogue, avatarImageUri, galleryImageUris, imageStylePrompt, voiceProfileUri, voiceMode, voiceDisplayName, tags ->
                roleManagementViewModel.updateRoleCard(
                    id = roleCard.id,
                    name = name,
                    description = description,
                    avatar = avatar,
                    persona = persona,
                    speakingStyle = speakingStyle,
                    background = background,
                    rules = rules,
                    taboos = taboos,
                    openingMessage = openingMessage,
                    exampleDialogue = exampleDialogue,
                    avatarImageUri = avatarImageUri,
                    galleryImageUris = galleryImageUris,
                    imageStylePrompt = imageStylePrompt,
                    voiceProfileUri = voiceProfileUri,
                    voiceMode = voiceMode,
                    voiceDisplayName = voiceDisplayName,
                    tags = tags
                )
                editingRoleCard = null
                // If we came from discover page to edit a specific role, go back after saving
                if (editRoleId != null && editRoleId > 0) {
                    onBack()
                }
            },
            existingName = roleCard.name,
            existingDescription = roleCard.description,
            existingAvatar = roleCard.avatar,
            existingPersona = roleCard.persona,
            existingSpeakingStyle = roleCard.speakingStyle,
            existingBackground = roleCard.background,
            existingRules = roleCard.rules,
            existingTaboos = roleCard.taboos,
            existingOpeningMessage = roleCard.openingMessage,
            existingExampleDialogue = roleCard.exampleDialogue,
            existingAvatarImageUri = roleCard.avatarImageUri,
            existingGalleryImageUris = roleCard.galleryImageUris,
            existingImageStylePrompt = roleCard.imageStylePrompt,
            existingVoiceProfileUri = roleCard.voiceProfileUri,
            existingVoiceMode = roleCard.voiceMode,
            existingVoiceDisplayName = roleCard.voiceDisplayName,
            existingTags = roleCard.tags.joinToString(", "),
            isEditing = true
        )
    }

    deletingRoleCard?.let { roleCard ->
        AlertDialog(
            onDismissRequest = { deletingRoleCard = null },
            title = { Text(Strings.txt(StringsKey.role_delete_title)) },
            text = { Text(Strings.txt(StringsKey.skills_delete_confirm, roleCard.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        roleManagementViewModel.deleteRoleCard(roleCard.id)
                        deletingRoleCard = null
                    }
                ) {
                    Text(Strings.txt(StringsKey.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingRoleCard = null }) {
                    Text(Strings.txt(StringsKey.cancel))
                }
            }
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun RoleCardItem(
    roleCard: RoleCard,
    isActive: Boolean,
    onActivate: () -> Unit,
    onStartChat: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (roleCard.avatarImageUri.isNotBlank()) {
                        AsyncImage(
                            model = roleCard.avatarImageUri,
                            contentDescription = roleCard.name,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = roleCard.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (roleCard.description.isNotBlank()) {
                            Text(
                                text = roleCard.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = Strings.txt(StringsKey.edit),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isActive) {
                        AssistChip(
                            onClick = {},
                            label = { Text(Strings.txt(StringsKey.drawer_active_tag)) }
                        )
                    }
                }
            }

            Text(
                text = Strings.txt(StringsKey.char_mgmt_persona_label, roleCard.persona),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (roleCard.speakingStyle.isNotBlank()) {
                Text(
                    text = Strings.txt(StringsKey.char_mgmt_style_label, roleCard.speakingStyle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (roleCard.avatarImageUri.isNotBlank() || roleCard.galleryImageUris.isNotEmpty()) {
                Text(
                    text = Strings.txt(StringsKey.char_mgmt_image_label, if (roleCard.avatarImageUri.isNotBlank()) Strings.txt(StringsKey.char_mgmt_avatar_configured) else Strings.txt(StringsKey.char_mgmt_avatar_missing), roleCard.galleryImageUris.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (roleCard.voiceProfileUri.isNotBlank() || roleCard.voiceDisplayName.isNotBlank()) {
                Text(
                    text = Strings.txt(StringsKey.char_mgmt_voice_label, roleCard.voiceDisplayName.ifBlank { roleCard.voiceMode }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onStartChat) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                    Text(Strings.txt(StringsKey.tab_chat))
                }
                if (!isActive) {
                    TextButton(onClick = onActivate) {
                        Text(Strings.txt(StringsKey.enable))
                    }
                }
                TextButton(onClick = onEdit) {
                    Text(Strings.txt(StringsKey.edit))
                }
                onDelete?.let {
                    TextButton(onClick = it) {
                        Text(Strings.txt(StringsKey.delete))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
