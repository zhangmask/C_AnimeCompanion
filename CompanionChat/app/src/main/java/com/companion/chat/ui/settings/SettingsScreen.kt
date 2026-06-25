package com.companion.chat.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil3.compose.AsyncImage
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.ui.text.font.FontWeight
import com.companion.chat.CompanionChatApplication
import com.companion.chat.data.context.ContextConfigRepository
import com.companion.chat.data.profile.UserAvatarStore
import com.companion.chat.ui.theme.BrandPrimary
import com.companion.chat.ui.theme.BrandPrimaryContainer
import com.companion.chat.ui.theme.BrandSecondaryContainer
import com.companion.chat.ui.theme.IconGradientBlue
import com.companion.chat.ui.theme.IconGradientGold
import com.companion.chat.ui.theme.IconGradientGray
import com.companion.chat.ui.theme.IconGradientGreen
import com.companion.chat.ui.theme.IconGradientPink
import com.companion.chat.ui.theme.IconGradientPurple
import com.companion.chat.locale.AppLanguage
import com.companion.chat.locale.LocalLanguage
import com.companion.chat.locale.Strings
import com.companion.chat.locale.StringsKey

/**
 * ModelConfigScreen 的跳转目标
 */
enum class ModelConfigScrollTarget {
    DEFAULT,
    CONTEXT_WINDOW,
    IMAGE_GENERATION
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onNavigateToCharacter: () -> Unit = {},
    onNavigateToSkills: () -> Unit = {},
    onNavigateToMemory: () -> Unit = {},
    onNavigateToModel: (ModelConfigScrollTarget) -> Unit = { _ -> },
    onNavigateToVoice: () -> Unit = {},
    onNavigateToLanguage: () -> Unit = {},
    onNavigateToDarkMode: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {}
) {
    val context = LocalContext.current
    val contextConfigRepository = remember(context) { ContextConfigRepository(context) }
    val appContainer = (context.applicationContext as CompanionChatApplication).appContainer
    val userProfile by appContainer.userProfileRepository.profileFlow.collectAsStateWithLifecycle()
    var retainedRounds by remember { mutableIntStateOf(contextConfigRepository.getSettings().retainedRounds) }
    var autoPreferenceLearningEnabled by remember {
        mutableStateOf(contextConfigRepository.getAutoPreferenceLearningEnabled())
    }

    LaunchedEffect(Unit) {
        retainedRounds = contextConfigRepository.getSettings().retainedRounds
        autoPreferenceLearningEnabled = contextConfigRepository.getAutoPreferenceLearningEnabled()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = Strings.txt(StringsKey.settings_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // ── 我的形象 Profile Card ──
            ProfileCard(
                nickname = userProfile.nickname.ifBlank { Strings.txt(StringsKey.settings_hint_nickname) },
                bio = userProfile.bio.ifBlank { Strings.txt(StringsKey.settings_hint_bio) },
                gender = userProfile.gender,
                age = userProfile.age,
                rawBio = userProfile.bio,
                interestTags = userProfile.interestTags,
                avatarUri = userProfile.avatarUri,
                onAvatarUriChange = { newUri ->
                    appContainer.userProfileRepository.updateProfile(
                        userProfile.copy(avatarUri = newUri)
                    )
                },
                onEditClick = onNavigateToProfile
            )

            SettingsSection(title = Strings.txt(StringsKey.settings_section_characters)) {
                SettingsItem(
                    icon = Icons.Default.Person,
                    title = Strings.txt(StringsKey.settings_item_characters),
                    subtitle = Strings.txt(StringsKey.settings_sub_characters),
                    onClick = onNavigateToCharacter,
                    iconBrush = IconGradientPurple
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon = Icons.Default.Psychology,
                    title = Strings.txt(StringsKey.settings_item_skills),
                    subtitle = Strings.txt(StringsKey.settings_sub_skills),
                    onClick = onNavigateToSkills,
                    iconBrush = IconGradientPurple
                )
            }

            SettingsSection(title = Strings.txt(StringsKey.settings_section_memory)) {
                SettingsItem(
                    icon = Icons.Default.Psychology,
                    title = Strings.txt(StringsKey.settings_item_memory),
                    subtitle = Strings.txt(StringsKey.settings_sub_memory),
                    onClick = onNavigateToMemory,
                    iconBrush = IconGradientBlue
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsToggleItem(
                    icon = Icons.Default.Psychology,
                    title = Strings.txt(StringsKey.settings_item_auto_learn),
                    subtitle = if (autoPreferenceLearningEnabled) {
                        Strings.txt(StringsKey.settings_sub_learn_on)
                    } else {
                        Strings.txt(StringsKey.settings_sub_learn_off)
                    },
                    checked = autoPreferenceLearningEnabled,
                    onCheckedChange = { enabled ->
                        autoPreferenceLearningEnabled = enabled
                        contextConfigRepository.updateAutoPreferenceLearningEnabled(enabled)
                    },
                    iconBrush = IconGradientBlue
                )
            }

            SettingsSection(title = Strings.txt(StringsKey.settings_section_model)) {
                SettingsItem(
                    icon = Icons.Default.Memory,
                    title = Strings.txt(StringsKey.settings_item_model),
                    subtitle = Strings.txt(StringsKey.settings_sub_model),
                    onClick = { onNavigateToModel(ModelConfigScrollTarget.DEFAULT) },
                    iconBrush = IconGradientGold
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon = Icons.Default.Memory,
                    title = Strings.txt(StringsKey.settings_item_context_window),
                    subtitle = Strings.txt(StringsKey.settings_sub_context, retainedRounds),
                    onClick = { onNavigateToModel(ModelConfigScrollTarget.CONTEXT_WINDOW) },
                    iconBrush = IconGradientGold
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon = Icons.Default.Photo,
                    title = Strings.txt(StringsKey.settings_item_image),
                    subtitle = Strings.txt(StringsKey.settings_sub_image),
                    onClick = { onNavigateToModel(ModelConfigScrollTarget.IMAGE_GENERATION) },
                    iconBrush = IconGradientGold
                )
            }

            SettingsSection(title = Strings.txt(StringsKey.settings_section_voice)) {
                SettingsItem(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = Strings.txt(StringsKey.settings_item_voice),
                    subtitle = Strings.txt(StringsKey.settings_sub_voice),
                    onClick = onNavigateToVoice,
                    iconBrush = IconGradientGreen
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon = Icons.Default.Language,
                    title = Strings.txt(StringsKey.settings_item_language),
                    subtitle = when (LocalLanguage.current) {
                    AppLanguage.ZH -> Strings.txt(StringsKey.language_zh)
                    AppLanguage.EN -> Strings.txt(StringsKey.language_en)
                },
                    onClick = onNavigateToLanguage,
                    iconBrush = IconGradientGreen
                )
            }

            SettingsSection(title = Strings.txt(StringsKey.settings_section_appearance)) {
                SettingsItem(
                    icon = Icons.Default.DarkMode,
                    title = Strings.txt(StringsKey.settings_item_dark_mode),
                    subtitle = Strings.txt(StringsKey.dark_mode_follow_system),
                    onClick = onNavigateToDarkMode,
                    iconBrush = IconGradientPink
                )
            }

            SettingsSection(title = Strings.txt(StringsKey.settings_section_about)) {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = Strings.txt(StringsKey.settings_item_about),
                    subtitle = Strings.txt(StringsKey.about_version) + " 0.1.0",
                    onClick = onNavigateToAbout,
                    iconBrush = IconGradientGray
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.dp
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    iconBrush: Brush? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconBrush != null) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(brush = iconBrush, shape = RoundedCornerShape(8.dp))
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.White
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconBrush: Brush? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconBrush != null) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(brush = iconBrush, shape = RoundedCornerShape(8.dp))
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.White
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ProfileCard(
    nickname: String,
    bio: String,
    gender: String,
    age: String,
    rawBio: String,
    interestTags: String,
    avatarUri: String = "",
    onAvatarUriChange: (String) -> Unit = {},
    onEditClick: () -> Unit
) {
    val context = LocalContext.current
    val avatarStore = remember(context) { UserAvatarStore(context) }
    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val persisted = avatarStore.persistUri(uri)
            if (persisted != null) {
                onAvatarUriChange(persisted)
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Avatar with initial letter and gradient background
                Box {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                brush = Brush.linearGradient(listOf(BrandPrimaryContainer, BrandSecondaryContainer)),
                                shape = CircleShape
                            )
                            .border(3.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarUri.isNotBlank()) {
                            AsyncImage(
                                model = avatarUri,
                                contentDescription = Strings.txt(StringsKey.profile_avatar_desc),
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = nickname.firstOrNull()?.toString() ?: "U",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandPrimary
                            )
                        }
                    }
                    // Camera badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { avatarPickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = Strings.txt(StringsKey.profile_change_avatar),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = nickname,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = bio,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Interest tags
            if (interestTags.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                val tags = interestTags.split(",", "，").map { it.trim() }.filter { it.isNotBlank() }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tags.take(4).forEach { tag ->
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            border = null
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Edit button
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onEditClick),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = Strings.txt(StringsKey.profile_edit_profile),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}


