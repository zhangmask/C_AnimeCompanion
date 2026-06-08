package com.companion.chat.data.memory

import androidx.sqlite.db.SupportSQLiteQuery
import com.companion.chat.data.local.dao.MemoryDao
import com.companion.chat.data.local.entity.Memory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryLifecycleManagerTest {

    @Test
    fun `启动时清理过期短期记忆并提升可晋升记忆`() = runBlocking {
        val now = 1_700_000_000_000L
        val dao = LifecycleFakeMemoryDao(
            memories = mutableListOf(
                memory(id = 1, content = "过期短期", layer = "short_term", expiresAt = now - 1),
                memory(id = 2, content = "可提升短期", layer = "short_term", referenceCount = 3, expiresAt = now + 1000),
                memory(id = 3, content = "保留长期", layer = "long_term", referenceCount = 10, expiresAt = null)
            )
        )
        val repository = MemoryRepository(
            memoryDao = dao,
            nowProvider = { now }
        )
        val manager = MemoryLifecycleManager(
            memoryRepository = repository,
            nowProvider = { now }
        )

        manager.runStartupMaintenance()

        assertEquals(listOf(1L), dao.cleanedExpiredIds)
        assertEquals(listOf(2L), dao.promotedIds)
        assertEquals("long_term", dao.memories.first { it.id == 2L }.layer)
        assertTrue(dao.memories.any { it.id == 3L && it.layer == "long_term" })
    }

    @Test
    fun `没有可清理和可提升记忆时正常跳过`() = runBlocking {
        val now = 1_700_000_000_000L
        val dao = LifecycleFakeMemoryDao(
            memories = mutableListOf(
                memory(id = 1, content = "未过期短期", layer = "short_term", referenceCount = 2, expiresAt = now + 1000),
                memory(id = 2, content = "长期记忆", layer = "long_term", referenceCount = 5, expiresAt = null)
            )
        )
        val repository = MemoryRepository(
            memoryDao = dao,
            nowProvider = { now }
        )
        val manager = MemoryLifecycleManager(
            memoryRepository = repository,
            nowProvider = { now }
        )

        manager.runStartupMaintenance()

        assertTrue(dao.cleanedExpiredIds.isEmpty())
        assertTrue(dao.promotedIds.isEmpty())
    }

    private fun memory(
        id: Long,
        content: String,
        layer: String,
        referenceCount: Int = 0,
        expiresAt: Long?
    ) = Memory(
        id = id,
        content = content,
        category = "fact",
        layer = layer,
        source = "rule_extractor",
        referenceCount = referenceCount,
        sessionId = "session-1",
        createdAt = 0,
        updatedAt = 0,
        expiresAt = expiresAt
    )

    private class LifecycleFakeMemoryDao(
        val memories: MutableList<Memory>
    ) : MemoryDao {

        val cleanedExpiredIds = mutableListOf<Long>()
        val promotedIds = mutableListOf<Long>()

        override suspend fun insert(memory: Memory): Long = error("unused")

        override suspend fun insertAll(memories: List<Memory>): List<Long> = error("unused")

        override suspend fun update(memory: Memory) = Unit

        override suspend fun delete(memory: Memory) = Unit

        override suspend fun getAll(): List<Memory> = memories.toList()

        override fun observeAll(): Flow<List<Memory>> = flowOf(memories.toList())

        override suspend fun getByLayer(layer: String): List<Memory> = memories.filter { it.layer == layer }

        override suspend fun getPersistentMemories(): List<Memory> =
            memories.filter { it.layer == "long_term" }.sortedByDescending { it.updatedAt }

        override suspend fun getByCategory(category: String): List<Memory> = memories.filter { it.category == category }

        override suspend fun findExactMatch(category: String, content: String): Memory? = null

        override suspend fun searchByFTS(query: SupportSQLiteQuery): List<Memory> = emptyList()

        override suspend fun incrementReference(id: Long): Int = 0

        override suspend fun promoteToLongTerm(id: Long, now: Long): Int {
            val index = memories.indexOfFirst { it.id == id }
            if (index < 0) {
                return 0
            }
            promotedIds += id
            memories[index] = memories[index].copy(layer = "long_term", updatedAt = now, expiresAt = null)
            return 1
        }

        override suspend fun cleanupExpiredShortTerm(now: Long): Int {
            val expired = memories.filter { it.layer == "short_term" && it.expiresAt != null && it.expiresAt < now }
            cleanedExpiredIds += expired.map { it.id }
            memories.removeAll(expired.toSet())
            return expired.size
        }

        override suspend fun getPromotableShortTerm(): List<Memory> {
            return memories.filter { it.layer == "short_term" && it.referenceCount >= 3 }
        }
    }
}
