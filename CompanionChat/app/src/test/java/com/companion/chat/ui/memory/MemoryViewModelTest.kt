package com.companion.chat.ui.memory

import android.app.Application
import androidx.sqlite.db.SupportSQLiteQuery
import com.companion.chat.data.local.dao.MemoryDao
import com.companion.chat.data.local.entity.Memory
import com.companion.chat.data.memory.MemoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryViewModelTest {

    @Test
    fun `能加载全部记忆并按分类筛选`() {
        val dao = MemoryViewModelFakeDao(
            mutableListOf(
                memory(id = 1, content = "用户叫小明", category = "fact"),
                memory(id = 2, content = "用户喜欢火锅", category = "preference")
            )
        )
        val viewModel = createViewModel(dao)

        assertEquals(2, viewModel.uiState.value.memories.size)

        viewModel.setFilter(MemoryFilter.PREFERENCE)

        assertEquals(1, viewModel.uiState.value.memories.size)
        assertEquals("用户喜欢火锅", viewModel.uiState.value.memories.first().content)
    }

    @Test
    fun `手动新增记忆直接写为长期记忆`() {
        val dao = MemoryViewModelFakeDao(mutableListOf())
        val viewModel = createViewModel(dao)

        viewModel.addMemory(content = "用户住在北京", category = "fact")

        assertEquals(1, dao.memories.size)
        assertEquals("long_term", dao.memories.first().layer)
        assertEquals("manual", dao.memories.first().source)
        assertEquals(1, viewModel.uiState.value.memories.size)
    }

    @Test
    fun `可删除和提升短期记忆`() {
        val dao = MemoryViewModelFakeDao(
            mutableListOf(
                memory(id = 1, content = "短期记忆", category = "fact", layer = "short_term"),
                memory(id = 2, content = "长期记忆", category = "fact", layer = "long_term")
            )
        )
        val viewModel = createViewModel(dao)

        viewModel.promoteMemory(1L)
        assertEquals("long_term", dao.memories.first { it.id == 1L }.layer)

        viewModel.deleteMemory(dao.memories.first { it.id == 2L })
        assertTrue(dao.memories.none { it.id == 2L })
    }

    private fun createViewModel(dao: MemoryViewModelFakeDao): MemoryViewModel {
        return MemoryViewModel(
            application = Application(),
            memoryRepository = MemoryRepository(
                memoryDao = dao,
                nowProvider = { 1_700_000_000_000L }
            ),
            workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )
    }

    private fun memory(
        id: Long,
        content: String,
        category: String,
        layer: String = "long_term"
    ) = Memory(
        id = id,
        content = content,
        category = category,
        layer = layer,
        source = "manual",
        referenceCount = 0,
        sessionId = null,
        createdAt = 0,
        updatedAt = 0,
        expiresAt = null
    )

    private class MemoryViewModelFakeDao(
        val memories: MutableList<Memory>
    ) : MemoryDao {

        private var nextId = (memories.maxOfOrNull { it.id } ?: 0L) + 1L

        override suspend fun insert(memory: Memory): Long {
            val inserted = memory.copy(id = nextId++)
            memories += inserted
            return inserted.id
        }

        override suspend fun insertAll(memories: List<Memory>): List<Long> {
            return memories.map { insert(it) }
        }

        override suspend fun update(memory: Memory) {
            val index = memories.indexOfFirst { it.id == memory.id }
            if (index >= 0) {
                memories[index] = memory
            }
        }

        override suspend fun delete(memory: Memory) {
            memories.removeAll { it.id == memory.id }
        }

        override suspend fun getAll(): List<Memory> = memories.sortedByDescending { it.updatedAt }

        override fun observeAll(): Flow<List<Memory>> =
            flowOf(memories.sortedByDescending { it.updatedAt })

        override suspend fun getByLayer(layer: String): List<Memory> = memories.filter { it.layer == layer }

        override suspend fun getPersistentMemories(): List<Memory> =
            memories.filter { it.layer == "long_term" }.sortedByDescending { it.updatedAt }

        override suspend fun getByCategory(category: String): List<Memory> = memories.filter { it.category == category }

        override suspend fun findExactMatch(category: String, content: String): Memory? =
            memories.firstOrNull { it.category == category && it.content == content }

        override suspend fun searchByFTS(query: SupportSQLiteQuery): List<Memory> = emptyList()

        override suspend fun incrementReference(id: Long): Int = 0

        override suspend fun promoteToLongTerm(id: Long, now: Long): Int {
            val index = memories.indexOfFirst { it.id == id }
            if (index < 0) {
                return 0
            }
            memories[index] = memories[index].copy(layer = "long_term", updatedAt = now, expiresAt = null)
            return 1
        }

        override suspend fun cleanupExpiredShortTerm(now: Long): Int = 0

        override suspend fun getPromotableShortTerm(): List<Memory> = emptyList()
    }
}
