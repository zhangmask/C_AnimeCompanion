package com.companion.chat.data.discover

import android.content.Context
import android.content.SharedPreferences
import com.companion.chat.data.role.RoleCardRepository
import com.companion.chat.data.voice.VoiceClipScanner

class DiscoverRoleRepository(
    private val sharedPreferences: SharedPreferences,
    private val roleCardRepository: RoleCardRepository? = null,
    private val roles: List<DiscoverRoleCard> = DiscoverRoleSeeds.roles,
    private val voiceClipScanner: VoiceClipScanner? = null
) {
    constructor(context: Context, roleCardRepository: RoleCardRepository? = null) : this(
        sharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        roleCardRepository = roleCardRepository,
        voiceClipScanner = VoiceClipScanner(context)
    )

    fun getRoleItems(
        query: String = "",
        selectedTag: String? = null,
        includeMature: Boolean = false,
        sortMode: RoleSortMode = RoleSortMode.HOT
    ): List<DiscoverRoleCardItem> {
        val normalizedQuery = query.trim()
        val filtered = roles.filter { role ->
            val matchesQuery = normalizedQuery.isBlank() ||
                role.name.contains(normalizedQuery, ignoreCase = true) ||
                role.author.contains(normalizedQuery, ignoreCase = true) ||
                role.description.contains(normalizedQuery, ignoreCase = true) ||
                role.tags.any { it.contains(normalizedQuery, ignoreCase = true) }
            val matchesTag = selectedTag.isNullOrBlank() || role.tags.contains(selectedTag)
            val matchesRating = includeMature || role.contentRating != ContentRating.MATURE
            matchesQuery && matchesTag && matchesRating
        }
        val sorted = when (sortMode) {
            RoleSortMode.HOT -> filtered.sortedByDescending { it.heat }
            RoleSortMode.NEWEST -> filtered.sortedByDescending { it.createdAt }
            RoleSortMode.NAME -> filtered.sortedBy { it.name }
        }
        return sorted.map { DiscoverRoleCardItem(it, getCollection(it.id)) }
    }

    fun getRoleItem(roleId: String): DiscoverRoleCardItem? {
        val role = roles.firstOrNull { it.id == roleId } ?: return null
        return DiscoverRoleCardItem(role, getCollection(role.id))
    }

    fun getTags(): List<String> {
        val preferred = listOf("男性", "女性", "二次元", "恋爱", "冒险", "剧情", "英语", "中文")
        val all = roles.flatMap { it.tags }.distinct()
        return preferred.filter { it in all } + all.filterNot { it in preferred }.sorted()
    }

    fun toggleFavorite(roleId: String): RoleCollection {
        val current = getCollection(roleId)
        sharedPreferences.edit().putBoolean(key(roleId, KEY_FAVORITE), !current.isFavorite).apply()
        return getCollection(roleId)
    }

    fun unlock(roleId: String): RoleCollection {
        sharedPreferences.edit()
            .putBoolean(key(roleId, KEY_UNLOCKED), true)
            .putBoolean(key(roleId, KEY_FAVORITE), true)
            .apply()
        return getCollection(roleId)
    }

    suspend fun copyToMyRoleCard(roleId: String): Long {
        val repository = requireNotNull(roleCardRepository) { "RoleCardRepository 未配置" }
        val role = roles.firstOrNull { it.id == roleId } ?: error("未找到发现角色: $roleId")
        getCollection(roleId).importedRoleCardId?.let { return it }
        // 所有角色默认尝试 MOSS TTS 克隆路径。
        // 优先使用 voice_clips 目录中的音频；没有则留空，
        // RoleAwareVoiceOutputEngine 会自动回退到 assets/voice/moss_default_voice.wav。
        val defaultVoiceUri = voiceClipScanner?.getDefaultClipUri() ?: ""
        val id = repository.createRoleCard(
            name = role.name,
            description = role.description,
            avatar = "person",
            persona = role.persona,
            speakingStyle = role.speakingStyle,
            background = role.background,
            rules = "保持角色设定一致；优先以私人陪伴和持续关系为目标回应。",
            taboos = "不要在回复中暴露图片 URI、语音 URI 或内部 Provider 配置。",
            openingMessage = role.openingMessage,
            exampleDialogue = "",
            avatarImageUri = role.coverImageUri,
            galleryImageUris = listOf(role.coverImageUri).filter { it.isNotBlank() },
            imageStylePrompt = role.imageStyle,
            voiceProfileUri = defaultVoiceUri,
            voiceMode = "CLONE",
            voiceDisplayName = role.voiceSummary
        )
        sharedPreferences.edit()
            .putBoolean(key(roleId, KEY_UNLOCKED), true)
            .putBoolean(key(roleId, KEY_FAVORITE), true)
            .putLong(key(roleId, KEY_IMPORTED_ROLE_CARD_ID), id)
            .apply()
        return id
    }

    suspend fun attachGeneratedImage(roleId: String, imageUri: String): Boolean {
        val repository = requireNotNull(roleCardRepository) { "RoleCardRepository 未配置" }
        val importedId = getCollection(roleId).importedRoleCardId ?: return false
        return repository.appendGalleryImage(importedId, imageUri)
    }

    fun getCollection(roleId: String): RoleCollection {
        val importedId = sharedPreferences.getLong(key(roleId, KEY_IMPORTED_ROLE_CARD_ID), 0L)
        return RoleCollection(
            roleId = roleId,
            isFavorite = sharedPreferences.getBoolean(key(roleId, KEY_FAVORITE), false),
            isUnlocked = sharedPreferences.getBoolean(key(roleId, KEY_UNLOCKED), false),
            importedRoleCardId = importedId.takeIf { it > 0L }
        )
    }

    private fun key(roleId: String, suffix: String): String = "$roleId:$suffix"

    private companion object {
        const val PREFS_NAME = "discover_roles"
        const val KEY_FAVORITE = "favorite"
        const val KEY_UNLOCKED = "unlocked"
        const val KEY_IMPORTED_ROLE_CARD_ID = "imported_role_card_id"
    }
}
