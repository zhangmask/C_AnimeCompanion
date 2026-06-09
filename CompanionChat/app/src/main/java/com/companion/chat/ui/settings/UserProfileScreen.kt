package com.companion.chat.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.companion.chat.CompanionChatApplication
import com.companion.chat.ui.theme.BrandPrimary
import com.companion.chat.ui.theme.BrandOutline
import com.companion.chat.ui.theme.BrandOutlineVariant
import com.companion.chat.ui.theme.BrandSurfaceContainer

@Composable
fun UserProfileScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as CompanionChatApplication).appContainer
    val repository = remember { appContainer.userProfileRepository }
    val profile = remember { repository.getProfile() }

    var nickname by remember { mutableStateOf(profile.nickname) }
    var gender by remember { mutableStateOf(profile.gender) }
    var age by remember { mutableStateOf(profile.age) }
    var bio by remember { mutableStateOf(profile.bio) }
    var interestTags by remember { mutableStateOf(profile.interestTags) }
    var avatarUri by remember { mutableStateOf(profile.avatarUri) }
    var introduction by remember { mutableStateOf(profile.introduction) }
    var importantInfo by remember { mutableStateOf(profile.importantInfo) }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("基础", "个性")

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            // Take persistable permission
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            avatarUri = uri.toString()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F5FA))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Header ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "编辑个人资料",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(BrandSurfaceContainer)
                        .clickable { onNavigateBack() },
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
                    .background(Color.White)
                    .padding(horizontal = 20.dp)
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = selectedTab == index
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = index },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = tab,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) BrandPrimary else BrandOutline
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .width(if (isSelected) 32.dp else 0.dp)
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
                    .background(Color(0xFFE8E4EC))
            )

            // ── Tab Content ──
            when (selectedTab) {
                0 -> {
                    // 基础 Tab
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color.White)
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Avatar Section
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(BrandPrimary.copy(alpha = 0.1f))
                                    .clickable { avatarPickerLauncher.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                if (avatarUri.isNotBlank()) {
                                    AsyncImage(
                                        model = avatarUri,
                                        contentDescription = "头像",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = "头像",
                                        modifier = Modifier.size(40.dp),
                                        tint = BrandPrimary
                                    )
                                }
                                // Camera icon overlay
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(BrandPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.CameraAlt,
                                        contentDescription = "修改头像",
                                        modifier = Modifier.size(14.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                        }

                        // Change avatar hint
                        Text(
                            text = "点击修改头像",
                            fontSize = 12.sp,
                            color = BrandPrimary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )

                        ProfileFormField(
                            label = "昵称",
                            value = nickname,
                            onValueChange = { nickname = it },
                            placeholder = "给自己取个名字"
                        )

                        ProfileFormField(
                            label = "性别",
                            value = gender,
                            onValueChange = { gender = it },
                            placeholder = "男 / 女 / 其他"
                        )

                        ProfileFormField(
                            label = "年龄",
                            value = age,
                            onValueChange = { age = it },
                            placeholder = "你的年龄"
                        )
                    }
                }
                1 -> {
                    // 个性 Tab
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color.White)
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ProfileFormField(
                            label = "个性签名",
                            value = bio,
                            onValueChange = { bio = it },
                            placeholder = "一句话介绍自己",
                            maxLines = 2
                        )

                        ProfileFormField(
                            label = "个人介绍",
                            value = introduction,
                            onValueChange = { introduction = it },
                            placeholder = "详细介绍一下自己，让 AI 更了解你",
                            maxLines = 4
                        )

                        ProfileFormField(
                            label = "兴趣标签",
                            value = interestTags,
                            onValueChange = { interestTags = it },
                            placeholder = "用逗号分隔，如：阅读, 音乐, 游戏",
                            maxLines = 2
                        )

                        ProfileFormField(
                            label = "重要信息",
                            value = importantInfo,
                            onValueChange = { importantInfo = it },
                            placeholder = "希望 AI 记住的重要事情，如过敏信息、特殊需求等",
                            maxLines = 4
                        )

                        // Help text
                        Text(
                            text = "这些信息会帮助 AI 更好地了解你，提供更个性化的对话体验。重要信息会被优先记住。",
                            fontSize = 12.sp,
                            color = Color(0xFF79747E),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // ── Save Button ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable {
                            repository.updateProfile(
                                com.companion.chat.data.profile.UserProfile(
                                    nickname = nickname,
                                    gender = gender,
                                    age = age,
                                    bio = bio,
                                    interestTags = interestTags,
                                    avatarUri = avatarUri,
                                    introduction = introduction,
                                    importantInfo = importantInfo
                                )
                            )
                            onNavigateBack()
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = BrandPrimary
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "保存",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileFormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    maxLines: Int = 1
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF49454F)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, fontSize = 14.sp) },
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
