package com.companion.chat.data.memory

import androidx.sqlite.db.SupportSQLiteQuery
import com.companion.chat.data.local.dao.MemoryDao
import com.companion.chat.data.local.entity.Memory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRepositoryWriteTest {

    @Test
    fun `自动提取结果写入短期记忆并设置默认字段`() = runBlocking {
        val insertedMemories = mutableListOf<Memory>()
        val fakeDao = FakeMemoryDao(insertedMemories)
        val fakeExtractor = object : MemoryExtractor {
            override fun extract(userMessage: String, sessionId: String): List<ExtractedMemory> {
                return listOf(
                    ExtractedMemory(
                        content = "用户叫小明",
                        category = "fact",
                        layer = "short_term",
                        source = "rule_extractor"
                    ),
                    ExtractedMemory(
                        content = "用户喜欢火锅",
                        category = "preference",
                        layer = "short_term",
                        source = "rule_extractor"
                    )
                )
            }
        }
        val now = 1_700_000_000_000L
        val repository = MemoryRepository(
            memoryDao = fakeDao,
            extractor = fakeExtractor,
            nowProvider = { now }
        )

        val result = repository.extractAndStoreMemories(
            userMessage = "记住我的信息",
            sessionId = "session-1"
        )

        assertEquals(2, result.size)
        assertEquals(2, insertedMemories.size)
        assertTrue(insertedMemories.all { it.layer == "short_term" })
        assertTrue(insertedMemories.all { it.source == "rule_extractor" })
        assertTrue(insertedMemories.all { it.sessionId == "session-1" })
        assertTrue(insertedMemories.all { it.createdAt == now && it.updatedAt == now })
        assertEquals(now + MemoryRepository.SHORT_TERM_TTL_MILLIS, insertedMemories[0].expiresAt)
        assertEquals(now + MemoryRepository.SHORT_TERM_TTL_MILLIS, insertedMemories[1].expiresAt)
        assertEquals(0, insertedMemories[0].referenceCount)
    }

    @Test
    fun `没有提取结果时不写入任何记忆`() = runBlocking {
        val insertedMemories = mutableListOf<Memory>()
        val fakeDao = FakeMemoryDao(insertedMemories)
        val repository = MemoryRepository(
            memoryDao = fakeDao,
            extractor = object : MemoryExtractor {
                override fun extract(userMessage: String, sessionId: String): List<ExtractedMemory> {
                    return emptyList()
                }
            },
            nowProvider = { 1_700_000_000_000L }
        )

        val result = repository.extractAndStoreMemories(
            userMessage = "普通消息",
            sessionId = "session-1"
        )

        assertTrue(result.isEmpty())
        assertTrue(insertedMemories.isEmpty())
    }

    @Test
    fun `模型提取结果写入时会跳过重复记忆`() = runBlocking {
        val insertedMemories = mutableListOf(
            Memory(
                id = 1L,
                content = "用户喜欢火锅",
                category = "preference",
                layer = "short_term",
                source = MemoryRepository.MODEL_SOURCE,
                referenceCount = 0,
                sessionId = "session-1",
                createdAt = 10L,
                updatedAt = 10L,
                expiresAt = 20L
            )
        )
        val fakeDao = FakeMemoryDao(insertedMemories)
        val repository = MemoryRepository(
            memoryDao = fakeDao,
            nowProvider = { 1_700_000_000_000L }
        )

        val result = repository.storeModelExtractedMemories(
            extractedMemories = listOf(
                ExtractedMemory(
                    content = "用户喜欢火锅",
                    category = "preference",
                    layer = "short_term",
                    source = MemoryRepository.MODEL_SOURCE
                ),
                ExtractedMemory(
                    content = "用户一般晚上聊天比较多",
                    category = "time",
                    layer = "short_term",
                    source = MemoryRepository.MODEL_SOURCE
                )
            ),
            sessionId = "session-1"
        )

        assertEquals(2, result.size)
        assertEquals(2, insertedMemories.size)
        assertEquals(listOf("用户喜欢火锅", "用户一般晚上聊天比较多"), result.map { it.content })
    }

    private class FakeMemoryDao(
        private val insertedMemories: MutableList<Memory>
    ) : MemoryDao {

        private var nextId = 1L

        override suspend fun insert(memory: Memory): Long {
            insertedMemories += memory.copy(id = nextId)
            return nextId++
        }

        override suspend fun insertAll(memories: List<Memory>): List<Long> {
            return memories.map { insert(it) }
        }

        override suspend fun update(memory: Memory) = Unit

        override suspend fun delete(memory: Memory) = Unit

        override suspend fun getAll(): List<Memory> = insertedMemories.toList()

        override fun observeAll(): Flow<List<Memory>> = flowOf(insertedMemories.toList())

        override suspend fun getByLayer(layer: String): List<Memory> =
            insertedMemories.filter { it.layer == layer }

        override suspend fun getPersistentMemories(): List<Memory> =
            insertedMemories.filter { it.layer == "long_term" }.sortedByDescending { it.updatedAt }

        override suspend fun getByCategory(category: String): List<Memory> =
            insertedMemories.filter { it.category == category }

        override suspend fun findExactMatch(category: String, content: String): Memory? =
            insertedMemories.firstOrNull { it.category == category && it.content == content }

        override suspend fun searchByFTS(query: SupportSQLiteQuery): List<Memory> = emptyList()

        override suspend fun incrementReference(id: Long): Int = 0

        override suspend fun promoteToLongTerm(id: Long, now: Long): Int = 0

        override suspend fun cleanupExpiredShortTerm(now: Long): Int = 0

        override suspend fun getPromotableShortTerm(): List<Memory> = emptyList()
    }
}
