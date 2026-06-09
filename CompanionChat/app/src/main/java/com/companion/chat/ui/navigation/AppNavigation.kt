package com.companion.chat.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME(
        route = "home",
        label = "发现",
        selectedIcon = Icons.Filled.Explore,
        unselectedIcon = Icons.Outlined.Explore
    ),
    CHAT(
        route = "chat",
        label = "对话",
        selectedIcon = Icons.AutoMirrored.Filled.Chat,
        unselectedIcon = Icons.AutoMirrored.Outlined.Chat
    ),
    MEMORY(
        route = "memory",
        label = "记忆",
        selectedIcon = Icons.Filled.Memory,
        unselectedIcon = Icons.Outlined.Memory
    ),
    SETTINGS(
        route = "settings",
        label = "设置",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
}

object DiscoverRoutes {
    const val DETAIL = "discover/{roleId}"

    fun detail(roleId: String): String = "discover/$roleId"
}

object SettingsRoutes {
    const val CHARACTER = "settings/character"
    const val EDIT_CHARACTER = "settings/character/{roleId}"
    const val SKILLS = "settings/skills"
    const val MODEL = "settings/model"
    const val VOICE = "settings/voice"
    const val LANGUAGE = "settings/language"
    const val DARK_MODE = "settings/dark_mode"
    const val ABOUT = "settings/about"
    const val PROFILE = "settings/profile"

    fun editCharacter(roleId: Long): String = "settings/character/$roleId"
}
