package com.companion.chat.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.ui.text.font.FontWeight
import com.companion.chat.CompanionChatApplication
import com.companion.chat.data.context.ContextConfigRepository
import com.companion.chat.ui.theme.BrandPrimary
import com.companion.chat.ui.theme.BrandPrimaryContainer
import com.companion.chat.ui.theme.BrandSecondaryContainer
import com.companion.chat.ui.theme.IconGradientBlue
import com.companion.chat.ui.theme.IconGradientGold
import com.companion.chat.ui.theme.IconGradientGray
import com.companion.chat.ui.theme.IconGradientGreen
import com.companion.chat.ui.theme.IconGradientPink
import com.companion.chat.ui.theme.IconGradientPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onNavigateToCharacter: () -> Unit = {},
    onNavigateToSkills: () -> Unit = {},
    onNavigateToMemory: () -> Unit = {},
    onNavigateToModel: () -> Unit = {},
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
                        text = "设置",
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
                nickname = userProfile.nickname.ifBlank { "设置昵称" },
                bio = userProfile.bio.ifBlank { "设置你的个人信息，让 AI 伙伴更了解你" },
                gender = userProfile.gender,
                age = userProfile.age,
                rawBio = userProfile.bio,
                interestTags = userProfile.interestTags,
                onEditClick = onNavigateToProfile
            )

            SettingsSection(title = "角色") {
                SettingsItem(
                    icon = Icons.Default.Person,
                    title = "角色管理",
                    subtitle = "创建和切换陪伴角色卡",
                    onClick = onNavigateToCharacter,
                    iconBrush = IconGradientPurple
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon = Icons.Default.Psychology,
                    title = "Skills 管理",
                    subtitle = "管理工作能力模板和自定义 skills",
                    onClick = onNavigateToSkills,
                    iconBrush = IconGradientPurple
                )
            }

            SettingsSection(title = "记忆") {
                SettingsItem(
                    icon = Icons.Default.Psychology,
                    title = "记忆管理",
                    subtitle = "查看、编辑和提升短期记忆",
                    onClick = onNavigateToMemory,
                    iconBrush = IconGradientBlue
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsToggleItem(
                    icon = Icons.Default.Psychology,
                    title = "自动学习偏好",
                    subtitle = if (autoPreferenceLearningEnabled) {
                        "后台总结最近对话并逐步学习用户偏好"
                    } else {
                        "已关闭后台偏好总结，不会自动触发阶段四学习"
                    },
                    checked = autoPreferenceLearningEnabled,
                    onCheckedChange = { enabled ->
                        autoPreferenceLearningEnabled = enabled
                        contextConfigRepository.updateAutoPreferenceLearningEnabled(enabled)
                    },
                    iconBrush = IconGradientBlue
                )
            }

            SettingsSection(title = "模型") {
                SettingsItem(
                    icon = Icons.Default.Memory,
                    title = "模型配置",
                    subtitle = "选择模型、GPU/CPU 后端",
                    onClick = onNavigateToModel,
                    iconBrush = IconGradientGold
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon = Icons.Default.Memory,
                    title = "上下文窗口大小",
                    subtitle = "当前保留最近 $retainedRounds 轮对话",
                    onClick = onNavigateToModel,
                    iconBrush = IconGradientGold
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon = Icons.Default.Photo,
                    title = "图片生成",
                    subtitle = "配置联网图片生成 HTTP 接口",
                    onClick = onNavigateToModel,
                    iconBrush = IconGradientGold
                )
            }

            SettingsSection(title = "语音") {
                SettingsItem(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = "语音设置",
                    subtitle = "语音输入输出、语速语调",
                    onClick = onNavigateToVoice,
                    iconBrush = IconGradientGreen
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon = Icons.Default.Language,
                    title = "语言",
                    subtitle = "中文",
                    onClick = onNavigateToLanguage,
                    iconBrush = IconGradientGreen
                )
            }

            SettingsSection(title = "外观") {
                SettingsItem(
                    icon = Icons.Default.DarkMode,
                    title = "深色模式",
                    subtitle = "跟随系统",
                    onClick = onNavigateToDarkMode,
                    iconBrush = IconGradientPink
                )
            }

            SettingsSection(title = "关于") {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "关于",
                    subtitle = "版本 0.1.0",
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
    onEditClick: () -> Unit
) {
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
                        Text(
                            text = nickname.firstOrNull()?.toString() ?: "U",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandPrimary
                        )
                    }
                    // Camera badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "更换头像",
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
                        text = "编辑个人资料",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Profile fields
            Spacer(modifier = Modifier.height(8.dp))
            ProfileFieldRow(label = "昵称", value = nickname)
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            ProfileFieldRow(label = "性别", value = gender)
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            ProfileFieldRow(label = "年龄", value = age)
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            ProfileFieldRow(label = "个性签名", value = rawBio)
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            ProfileFieldRow(label = "兴趣标签", value = interestTags.ifBlank { "动漫、科技、旅行" })
        }
    }
}

@Composable
private fun ProfileFieldRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = value.ifBlank { "未设置" },
            style = MaterialTheme.typography.bodySmall,
            color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                   else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            maxLines = 1
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            modifier = Modifier
                .size(14.dp)
                .padding(start = 4.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}
