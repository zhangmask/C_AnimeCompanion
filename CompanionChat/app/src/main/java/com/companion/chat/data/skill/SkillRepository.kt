package com.companion.chat.data.skill

import com.companion.chat.data.local.dao.SkillDao
import com.companion.chat.data.local.entity.Skill

class SkillRepository(
    private val skillDao: SkillDao,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {

    suspend fun getAllSkills(): List<Skill> = skillDao.getAll()

    suspend fun getActiveSkill(): Skill? = skillDao.getActive()

    suspend fun createSkill(
        name: String,
        description: String,
        systemPrompt: String,
        icon: String = "custom"
    ): Long {
        val normalizedName = name.trim()
        val normalizedDescription = description.trim()
        val normalizedPrompt = systemPrompt.trim()
        require(normalizedName.isNotBlank()) { "Skill 名称不能为空" }
        require(normalizedPrompt.isNotBlank()) { "Skill prompt 不能为空" }

        val now = nowProvider()
        return skillDao.insert(
            Skill(
                name = normalizedName,
                description = normalizedDescription,
                systemPrompt = normalizedPrompt,
                icon = icon.trim().ifBlank { "custom" },
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun updateSkill(
        id: Long,
        name: String,
        description: String,
        systemPrompt: String,
        icon: String
    ) {
        val existing = skillDao.getById(id) ?: error("未找到 Skill: $id")
        val normalizedName = name.trim()
        val normalizedPrompt = systemPrompt.trim()
        require(normalizedName.isNotBlank()) { "Skill 名称不能为空" }
        require(normalizedPrompt.isNotBlank()) { "Skill prompt 不能为空" }

        skillDao.update(
            existing.copy(
                name = normalizedName,
                description = description.trim(),
                systemPrompt = normalizedPrompt,
                icon = icon.trim().ifBlank { existing.icon },
                updatedAt = nowProvider()
            )
        )
    }

    suspend fun deleteSkill(id: Long) {
        val existing = skillDao.getById(id) ?: return
        check(!existing.isBuiltIn) { "内置 Skill 不可删除" }
        skillDao.delete(existing)
    }

    suspend fun activateSkill(id: Long) {
        skillDao.deactivateAll()
        skillDao.activate(id, nowProvider())
    }
}
