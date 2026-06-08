package com.companion.chat.ui.settings

import android.app.Application
import com.companion.chat.data.local.dao.RoleCardDao
import com.companion.chat.data.local.entity.RoleCard
import com.companion.chat.data.role.RoleCardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleManagementViewModelTest {

    @Test
    fun `能加载角色卡并区分当前激活项`() {
        val dao = FakeRoleCardDao(
            mutableListOf(
                roleCard(id = 1L, name = "小夏", isActive = true),
                roleCard(id = 2L, name = "小林")
            )
        )
        val viewModel = createViewModel(dao)

        assertEquals("小夏", viewModel.uiState.value.activeRoleCard?.name)
        assertEquals(2, viewModel.uiState.value.roleCards.size)
    }

    @Test
    fun `可以新增编辑和删除角色卡`() {
        val dao = FakeRoleCardDao()
        val viewModel = createViewModel(dao)

        viewModel.createRoleCard(
            name = "阿澈",
            description = "冷静型陪伴",
            avatar = "moon",
            persona = "冷静温柔",
            speakingStyle = "克制简洁",
            background = "",
            rules = "",
            taboos = "",
            openingMessage = "",
            exampleDialogue = ""
        )

        val created = dao.roleCards.single()
        assertEquals("阿澈", created.name)

        viewModel.updateRoleCard(
            id = created.id,
            name = "阿澈Plus",
            description = created.description,
            avatar = created.avatar,
            persona = created.persona,
            speakingStyle = created.speakingStyle,
            background = created.background,
            rules = created.rules,
            taboos = created.taboos,
            openingMessage = created.openingMessage,
            exampleDialogue = created.exampleDialogue
        )
        assertEquals("阿澈Plus", dao.roleCards.single().name)

        viewModel.deleteRoleCard(created.id)
        assertTrue(dao.roleCards.isEmpty())
    }

    private fun createViewModel(dao: FakeRoleCardDao): RoleManagementViewModel {
        return RoleManagementViewModel(
            application = Application(),
            roleCardRepository = RoleCardRepository(dao, nowProvider = { 100L }),
            workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )
    }

    private fun roleCard(id: Long, name: String, isActive: Boolean = false) = RoleCard(
        id = id,
        name = name,
        description = "",
        avatar = "person",
        persona = "默认人设",
        speakingStyle = "",
        background = "",
        rules = "",
        taboos = "",
        openingMessage = "",
        exampleDialogue = "",
        isBuiltIn = false,
        isActive = isActive,
        createdAt = 0L,
        updatedAt = 0L
    )

    private class FakeRoleCardDao(
        val roleCards: MutableList<RoleCard> = mutableListOf()
    ) : RoleCardDao {

        private var nextId = (roleCards.maxOfOrNull { it.id } ?: 0L) + 1L

        override suspend fun insert(roleCard: RoleCard): Long {
            val inserted = roleCard.copy(id = nextId++)
            roleCards += inserted
            return inserted.id
        }

        override suspend fun update(roleCard: RoleCard) {
            val index = roleCards.indexOfFirst { it.id == roleCard.id }
            if (index >= 0) {
                roleCards[index] = roleCard
            }
        }

        override suspend fun delete(roleCard: RoleCard) {
            roleCards.removeAll { it.id == roleCard.id }
        }

        override suspend fun getAll(): List<RoleCard> = roleCards.sortedByDescending { it.updatedAt }

        override suspend fun getActive(): RoleCard? = roleCards.firstOrNull { it.isActive }

        override suspend fun getById(id: Long): RoleCard? = roleCards.firstOrNull { it.id == id }

        override suspend fun deactivateAll(): Int = 0

        override suspend fun activate(id: Long, now: Long): Int = 0
    }
}
