package com.companion.chat.data.role

import com.companion.chat.data.local.dao.RoleCardDao
import com.companion.chat.data.local.entity.RoleCard

class RoleCardRepository(
    private val roleCardDao: RoleCardDao,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {

    suspend fun getAllRoleCards(): List<RoleCard> = roleCardDao.getAll()

    suspend fun getActiveRoleCard(): RoleCard? = roleCardDao.getActive()

    suspend fun getRoleCard(id: Long): RoleCard? = roleCardDao.getById(id)

    suspend fun createRoleCard(
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
        avatarImageUri: String = "",
        galleryImageUris: List<String> = emptyList(),
        imageStylePrompt: String = "",
        voiceProfileUri: String = "",
        voiceMode: String = "CLONE",
        voiceDisplayName: String = ""
    ): Long {
        val normalizedName = name.trim()
        val normalizedPersona = persona.trim()
        require(normalizedName.isNotBlank()) { "角色名称不能为空" }
        require(normalizedPersona.isNotBlank()) { "核心人设不能为空" }

        val now = nowProvider()
        return roleCardDao.insert(
            RoleCard(
                name = normalizedName,
                description = description.trim(),
                avatar = avatar.trim().ifBlank { "person" },
                persona = normalizedPersona,
                speakingStyle = speakingStyle.trim(),
                background = background.trim(),
                rules = rules.trim(),
                taboos = taboos.trim(),
                openingMessage = openingMessage.trim(),
                exampleDialogue = exampleDialogue.trim(),
                avatarImageUri = avatarImageUri.trim(),
                galleryImageUris = galleryImageUris.map { it.trim() }.filter { it.isNotBlank() },
                imageStylePrompt = imageStylePrompt.trim(),
                voiceProfileUri = voiceProfileUri.trim(),
                voiceMode = voiceMode.trim().ifBlank { "CLONE" },
                voiceDisplayName = voiceDisplayName.trim(),
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun updateRoleCard(
        id: Long,
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
        avatarImageUri: String? = null,
        galleryImageUris: List<String>? = null,
        imageStylePrompt: String? = null,
        voiceProfileUri: String? = null,
        voiceMode: String? = null,
        voiceDisplayName: String? = null
    ) {
        val existing = roleCardDao.getById(id) ?: error("未找到角色卡: $id")
        val normalizedName = name.trim()
        val normalizedPersona = persona.trim()
        require(normalizedName.isNotBlank()) { "角色名称不能为空" }
        require(normalizedPersona.isNotBlank()) { "核心人设不能为空" }

        roleCardDao.update(
            existing.copy(
                name = normalizedName,
                description = description.trim(),
                avatar = avatar.trim().ifBlank { existing.avatar },
                persona = normalizedPersona,
                speakingStyle = speakingStyle.trim(),
                background = background.trim(),
                rules = rules.trim(),
                taboos = taboos.trim(),
                openingMessage = openingMessage.trim(),
                exampleDialogue = exampleDialogue.trim(),
                avatarImageUri = avatarImageUri?.trim() ?: existing.avatarImageUri,
                galleryImageUris = galleryImageUris
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?: existing.galleryImageUris,
                imageStylePrompt = imageStylePrompt?.trim() ?: existing.imageStylePrompt,
                voiceProfileUri = voiceProfileUri?.trim() ?: existing.voiceProfileUri,
                voiceMode = voiceMode?.trim()?.ifBlank { "CLONE" } ?: existing.voiceMode,
                voiceDisplayName = voiceDisplayName?.trim() ?: existing.voiceDisplayName,
                updatedAt = nowProvider()
            )
        )
    }

    suspend fun deleteRoleCard(id: Long) {
        val existing = roleCardDao.getById(id) ?: return
        check(!existing.isBuiltIn) { "内置角色卡不可删除" }
        roleCardDao.delete(existing)
    }

    suspend fun activateRoleCard(id: Long) {
        roleCardDao.deactivateAll()
        roleCardDao.activate(id, nowProvider())
    }

    suspend fun appendGalleryImage(id: Long, imageUri: String, useAsAvatarWhenEmpty: Boolean = true): Boolean {
        val normalizedUri = imageUri.trim()
        if (normalizedUri.isBlank()) {
            return false
        }
        val existing = roleCardDao.getById(id) ?: return false
        val nextGallery = (existing.galleryImageUris + normalizedUri).distinct()
        roleCardDao.update(
            existing.copy(
                avatarImageUri = if (useAsAvatarWhenEmpty && existing.avatarImageUri.isBlank()) {
                    normalizedUri
                } else {
                    existing.avatarImageUri
                },
                galleryImageUris = nextGallery,
                updatedAt = nowProvider()
            )
        )
        return true
    }

}
