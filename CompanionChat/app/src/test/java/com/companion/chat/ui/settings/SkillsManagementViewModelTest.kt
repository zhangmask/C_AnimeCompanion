package com.companion.chat.ui.settings

import android.app.Application
import com.companion.chat.data.local.dao.SkillDao
import com.companion.chat.data.local.entity.Skill
import com.companion.chat.data.skill.SkillRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillsManagementViewModelTest {

    @Test
    fun `能区分当前激活 内置 与自定义 skills`() {
        val dao = FakeSkillDao(
            mutableListOf(
                skill(id = 1L, name = "翻译助手", isBuiltIn = true, isActive = true),
                skill(id = 2L, name = "会议总结", isBuiltIn = false)
            )
        )
        val viewModel = createViewModel(dao)

        assertEquals("翻译助手", viewModel.uiState.value.activeSkill?.name)
        assertEquals(1, viewModel.uiState.value.builtInSkills.size)
        assertEquals(1, viewModel.uiState.value.customSkills.size)
    }

    @Test
    fun `可以新增 编辑 和删除自定义 skill`() {
        val dao = FakeSkillDao()
        val viewModel = createViewModel(dao)

        viewModel.createSkill(
            name = "网页分析",
            description = "分析页面信息",
            systemPrompt = "请帮助分析网页内容"
        )

        val created = dao.skills.single()
        assertEquals("网页分析", created.name)

        viewModel.updateSkill(
            id = created.id,
            name = "网页分析Plus",
            description = created.description,
            systemPrompt = created.systemPrompt,
            icon = created.icon
        )
        assertEquals("网页分析Plus", dao.skills.single().name)

        viewModel.deleteSkill(created.id)
        assertTrue(dao.skills.isEmpty())
    }

    private fun createViewModel(dao: FakeSkillDao): SkillsManagementViewModel {
        return SkillsManagementViewModel(
            application = Application(),
            skillRepository = SkillRepository(dao, nowProvider = { 100L }),
            workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )
    }

    private fun skill(
        id: Long,
        name: String,
        isBuiltIn: Boolean = false,
        isActive: Boolean = false
    ) = Skill(
        id = id,
        name = name,
        description = "",
        systemPrompt = "$name prompt",
        icon = "custom",
        isBuiltIn = isBuiltIn,
        isActive = isActive,
        usageCount = 0,
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

        override suspend fun getAll(): List<Skill> = skills

        override suspend fun getActive(): Skill? = skills.firstOrNull { it.isActive }

        override suspend fun getById(id: Long): Skill? = skills.firstOrNull { it.id == id }

        override suspend fun deactivateAll(): Int = 0

        override suspend fun activate(id: Long, now: Long): Int = 0
    }
}
