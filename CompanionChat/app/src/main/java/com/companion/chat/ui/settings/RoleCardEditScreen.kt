package com.companion.chat.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.companion.chat.ui.chat.components.RoleCardEditorSheet
import com.companion.chat.locale.Strings
import com.companion.chat.locale.StringsKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleCardEditScreen(
    roleId: Long,
    onBack: () -> Unit = {},
    roleManagementViewModel: RoleManagementViewModel = viewModel()
) {
    val uiState by roleManagementViewModel.uiState.collectAsState()
    val isCreate = roleId <= 0
    val roleCard = if (!isCreate) {
        remember(uiState.roleCards, uiState.activeRoleCard, roleId) {
            uiState.roleCards.find { it.id == roleId }
                ?: uiState.activeRoleCard?.takeIf { it.id == roleId }
        }
    } else null

    var saved by remember { mutableStateOf(false) }
    if (saved) { LaunchedEffect(Unit) { onBack() } }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (isCreate) Strings.txt(StringsKey.role_create_card_title)
                               else Strings.txt(StringsKey.role_edit_card_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, Strings.txt(StringsKey.back))
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isCreate || roleCard != null) {
            Box(modifier = Modifier.padding(paddingValues)) {
                if (isCreate) {
                    RoleCardEditorSheet(
                        onDismiss = onBack,
                        onSave = { name, description, avatar, persona, speakingStyle, background, rules, taboos, openingMessage, exampleDialogue, avatarImageUri, galleryImageUris, imageStylePrompt, voiceProfileUri, voiceMode, voiceDisplayName, tags ->
                            roleManagementViewModel.createRoleCard(
                                name = name, description = description, avatar = avatar,
                                persona = persona, speakingStyle = speakingStyle, background = background,
                                rules = rules, taboos = taboos, openingMessage = openingMessage,
                                exampleDialogue = exampleDialogue, avatarImageUri = avatarImageUri,
                                galleryImageUris = galleryImageUris, imageStylePrompt = imageStylePrompt,
                                voiceProfileUri = voiceProfileUri, voiceMode = voiceMode, voiceDisplayName = voiceDisplayName,
                                tags = tags
                            )
                            saved = true
                        }
                    )
                } else {
                    RoleCardEditorSheet(
                        onDismiss = onBack,
                        onSave = { name, description, avatar, persona, speakingStyle, background, rules, taboos, openingMessage, exampleDialogue, avatarImageUri, galleryImageUris, imageStylePrompt, voiceProfileUri, voiceMode, voiceDisplayName, tags ->
                            roleManagementViewModel.updateRoleCard(
                                id = roleCard!!.id, name = name, description = description, avatar = avatar,
                                persona = persona, speakingStyle = speakingStyle, background = background,
                                rules = rules, taboos = taboos, openingMessage = openingMessage,
                                exampleDialogue = exampleDialogue, avatarImageUri = avatarImageUri,
                                galleryImageUris = galleryImageUris, imageStylePrompt = imageStylePrompt,
                                voiceProfileUri = voiceProfileUri, voiceMode = voiceMode, voiceDisplayName = voiceDisplayName,
                                tags = tags
                            )
                            saved = true
                        },
                        existingName = roleCard!!.name, existingDescription = roleCard.description,
                        existingAvatar = roleCard.avatar, existingPersona = roleCard.persona,
                        existingSpeakingStyle = roleCard.speakingStyle, existingBackground = roleCard.background,
                        existingRules = roleCard.rules, existingTaboos = roleCard.taboos,
                        existingOpeningMessage = roleCard.openingMessage, existingExampleDialogue = roleCard.exampleDialogue,
                        existingAvatarImageUri = roleCard.avatarImageUri, existingGalleryImageUris = roleCard.galleryImageUris,
                        existingImageStylePrompt = roleCard.imageStylePrompt, existingVoiceProfileUri = roleCard.voiceProfileUri,
                        existingVoiceMode = roleCard.voiceMode, existingVoiceDisplayName = roleCard.voiceDisplayName,
                        existingTags = roleCard.tags.joinToString(", "),
                        isEditing = true
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text(Strings.txt(StringsKey.home_not_found), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
