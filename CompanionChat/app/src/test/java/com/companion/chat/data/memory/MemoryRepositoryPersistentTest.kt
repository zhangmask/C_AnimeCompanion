package com.companion.chat.data.memory

import androidx.sqlite.db.SupportSQLiteQuery
import com.companion.chat.data.local.dao.MemoryDao
import com.companion.chat.data.local.entity.Memory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryRepositoryPersistentTest {

    @Test
    fun `getPersistentMemories 只返回长期记忆并按更新时间倒序`() = runBlocking {
        val fakeDao = FakeMemoryDao(
            memories = listOf(
                memory(id = 1, layer = "short_term", updatedAt = 100L, content = "短期记忆"),
                memory(id = 2, layer = "long_term", updatedAt = 200L, content = "长期记忆A"),
                memory(id = 3, layer = "long_term", updatedAt = 300L, content = "长期记忆B")
            )
        )
        val repository = MemoryRepository(memoryDao = fakeDao)

        val result = repository.getPersistentMemories()

        assertEquals(listOf(3L, 2L), result.map { it.id })
        assertEquals(listOf("长期记忆B", "长期记忆A"), result.map { it.content })
    }

    private class FakeMemoryDao(
        memories: List<Memory>
    ) : MemoryDao {

        private val storedMemories = memories.toMutableList()

        override suspend fun insert(memory: Memory): Long = error("unused")

        override suspend fun insertAll(memories: List<Memory>): List<Long> = error("unused")

        override suspend fun update(memory: Memory) = Unit

        override suspend fun delete(memory: Memory) = Unit

        override suspend fun getAll(): List<Memory> = storedMemories.sortedByDescending { it.updatedAt }

        override fun observeAll(): Flow<List<Memory>> =
            flowOf(storedMemories.sortedByDescending { it.updatedAt })

        override suspend fun getByLayer(layer: String): List<Memory> =
            storedMemories
                .filter { it.layer == layer }
                .sortedByDescending { it.updatedAt }

        override suspend fun getPersistentMemories(): List<Memory> =
            storedMemories
                .filter { it.layer == "long_term" }
                .sortedByDescending { it.updatedAt }

        override suspend fun getByCategory(category: String): List<Memory> = emptyList()

        override suspend fun findExactMatch(category: String, content: String): Memory? = null

        override suspend fun searchByFTS(query: SupportSQLiteQuery): List<Memory> = emptyList()

        override suspend fun incrementReference(id: Long): Int = 0

        override suspend fun promoteToLongTerm(id: Long, now: Long): Int = 0

        override suspend fun cleanupExpiredShortTerm(now: Long): Int = 0

        override suspend fun getPromotableShortTerm(): List<Memory> = emptyList()
    }

    private fun memory(
        id: Long,
        layer: String,
        updatedAt: Long,
        content: String
    ): Memory {
        return Memory(
            id = id,
            content = content,
            category = "fact",
            layer = layer,
            source = "manual",
            referenceCount = 0,
            sessionId = null,
            createdAt = updatedAt,
            updatedAt = updatedAt,
            expiresAt = null
        )
    }
}
