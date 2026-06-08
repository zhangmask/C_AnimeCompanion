package com.companion.chat.data.skill

import com.companion.chat.data.local.dao.SkillDao
import com.companion.chat.data.local.entity.Skill
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillRepositoryTest {

    @Test
    fun `创建自定义 skill 时会规范化名称和 prompt`() = runBlocking {
        val dao = FakeSkillDao()
        val repository = SkillRepository(dao, nowProvider = { 100L })

        repository.createSkill(
            name = "  我的翻译器  ",
            description = "  描述  ",
            systemPrompt = "  请帮我翻译  "
        )

        val created = dao.skills.single()
        assertEquals("我的翻译器", created.name)
        assertEquals("描述", created.description)
        assertEquals("请帮我翻译", created.systemPrompt)
        assertFalse(created.isBuiltIn)
    }

    @Test
    fun `删除内置 skill 会被拦截`() = runBlocking {
        val dao = FakeSkillDao(
            mutableListOf(
                skill(id = 1L, name = "翻译助手", isBuiltIn = true)
            )
        )
        val repository = SkillRepository(dao)

        var thrown = false
        try {
            repository.deleteSkill(1L)
        } catch (_: IllegalStateException) {
            thrown = true
        }

        assertTrue(thrown)
        assertEquals(1, dao.skills.size)
    }

    @Test
    fun `激活 skill 会确保只有一个激活项并累加 usageCount`() = runBlocking {
        val dao = FakeSkillDao(
            mutableListOf(
                skill(id = 1L, name = "翻译助手", isBuiltIn = true, isActive = true, usageCount = 2),
                skill(id = 2L, name = "会议总结", isBuiltIn = false, isActive = false, usageCount = 0)
            )
        )
        val repository = SkillRepository(dao, nowProvider = { 200L })

        repository.activateSkill(2L)

        val activeIds = dao.skills.filter { it.isActive }.map { it.id }
        assertEquals(listOf(2L), activeIds)
        assertEquals(1, dao.skills.first { it.id == 2L }.usageCount)
        assertEquals(200L, dao.skills.first { it.id == 2L }.updatedAt)
    }

    private fun skill(
        id: Long,
        name: String,
        isBuiltIn: Boolean = false,
        isActive: Boolean = false,
        usageCount: Int = 0
    ) = Skill(
        id = id,
        name = name,
        description = "",
        systemPrompt = "$name prompt",
        icon = "custom",
        isBuiltIn = isBuiltIn,
        isActive = isActive,
        usageCount = usageCount,
        createdAt = 0L,
        updatedAt = 0L
    )

    private class FakeSkillDao(
        val skills: MutableList<Skill> = mutableListOf()
    ) : SkillDao {

        private var nextId = (skills.maxOfOrNull { it.id } ?: 0L) + 1L

        override suspend fun insert(skill: Skill): Long {
            val inserted = skill.copy(id = nextId++)
            skills += inserted
            return inserted.id
        }

        override suspend fun insertAll(skills: List<Skill>): List<Long> = skills.map { insert(it) }

        override suspend fun update(skill: Skill) {
            val index = skills.indexOfFirst { it.id == skill.id }
            if (index >= 0) {
                skills[index] = skill
            }
        }

        override suspend fun delete(skill: Skill) {
            skills.removeAll { it.id == skill.id }
        }

        override suspend fun getAll(): List<Skill> =
            skills.sortedWith(compareByDescending<Skill> { it.isBuiltIn }.thenByDescending { it.updatedAt })

        override suspend fun getActive(): Skill? = skills.firstOrNull { it.isActive }

        override suspend fun getById(id: Long): Skill? = skills.firstOrNull { it.id == id }

        override suspend fun deactivateAll(): Int {
            skills.replaceAll { it.copy(isActive = false) }
            return skills.size
        }

        override suspend fun activate(id: Long, now: Long): Int {
            val index = skills.indexOfFirst { it.id == id }
            if (index < 0) {
                return 0
            }
            val skill = skills[index]
            skills[index] = skill.copy(isActive = true, usageCount = skill.usageCount + 1, updatedAt = now)
            return 1
        }
    }
}
