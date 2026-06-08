package com.companion.chat.data.discover

enum class ContentRating {
    SAFE,
    MATURE
}

enum class RoleSortMode {
    HOT,
    NEWEST,
    NAME
}

data class RoleGenerationPreset(
    val imageProvider: String,
    val defaultPrompt: String,
    val negativePrompt: String = "",
    val preferLocal: Boolean = false
)

data class DiscoverRoleCard(
    val id: String,
    val name: String,
    val author: String,
    val coverImageUri: String = "",
    val tags: List<String>,
    val description: String,
    val persona: String,
    val speakingStyle: String = "",
    val background: String = "",
    val openingMessage: String = "",
    val heat: Int,
    val createdAt: Long,
    val isLocalCreated: Boolean = false,
    val contentRating: ContentRating = ContentRating.SAFE,
    val imageStyle: String = "",
    val voiceSummary: String = "系统 TTS",
    val generationPreset: RoleGenerationPreset
)

data class RoleCollection(
    val roleId: String,
    val isFavorite: Boolean = false,
    val isUnlocked: Boolean = false,
    val importedRoleCardId: Long? = null
)

data class DiscoverRoleCardItem(
    val role: DiscoverRoleCard,
    val collection: RoleCollection
)
