package com.companion.chat.data.memory

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.companion.chat.data.local.dao.MemoryDao
import com.companion.chat.data.local.entity.Memory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRetrieverTest {

    @Test
    fun `匹配记忆可检索且长期记忆优先`() = runBlocking {
        val dao = SearchableFakeMemoryDao(
            memories = mutableListOf(
                memory(
                    id = 1,
                    content = "用户是 Android 开发者",
                    layer = "short_term",
                    referenceCount = 1
                ),
                memory(
                    id = 2,
                    content = "用户长期从事 Android 项目",
                    layer = "long_term",
                    referenceCount = 0
                ),
                memory(
                    id = 3,
                    content = "用户喜欢火锅",
                    layer = "long_term",
                    referenceCount = 5
                )
            )
        )
        val retriever = MemoryRetriever(memoryDao = dao)

        val result = retriever.retrieveRelevantMemories("帮我写个 Android 应用")

        assertEquals(2, result.size)
        assertEquals(2L, result[0].id)
        assertEquals(1L, result[1].id)
        assertEquals(listOf(2L, 1L), dao.incrementedIds)
        assertTrue(dao.queries.last().contains("android"))
        assertTrue(dao.queries.last().contains("应用"))
    }

    @Test
    fun `空消息或过短消息不触发检索`() = runBlocking {
        val dao = SearchableFakeMemoryDao(
            memories = mutableListOf(
                memory(id = 1, content = "用户是 Android 开发者")
            )
        )
        val retriever = MemoryRetriever(memoryDao = dao)

        val emptyResult = retriever.retrieveRelevantMemories(" ")
        val shortResult = retriever.retrieveRelevantMemories("？")

        assertTrue(emptyResult.isEmpty())
        assertTrue(shortResult.isEmpty())
        assertTrue(dao.queries.isEmpty())
    }

    @Test
    fun `中文关系问句会归一化成多关键词查询`() = runBlocking {
        val dao = SearchableFakeMemoryDao(
            memories = mutableListOf(
                memory(id = 1, content = "小王是用户同事", layer = "long_term")
            )
        )
        val retriever = MemoryRetriever(memoryDao = dao)

        val result = retriever.retrieveRelevantMemories("小王和我什么关系")

        assertEquals(listOf(1L), result.map { it.id })
        assertTrue(dao.queries.last().contains("小王"))
        assertTrue(dao.queries.last().contains("关系"))
    }

    @Test
    fun `弱词和纯符号会被过滤`() = runBlocking {
        val dao = SearchableFakeMemoryDao(
            memories = mutableListOf(
                memory(id = 1, content = "用户喜欢火锅")
            )
        )
        val retriever = MemoryRetriever(memoryDao = dao)

        val result = retriever.retrieveRelevantMemories("？？ 这个 那个 一下 吗")

        assertTrue(result.isEmpty())
        assertTrue(dao.queries.isEmpty())
    }

    @Test
    fun `喜欢什么问句会归一化命中偏好记忆`() = runBlocking {
        val dao = SearchableFakeMemoryDao(
            memories = mutableListOf(
                memory(id = 1, content = "用户喜欢火锅", layer = "long_term")
            )
        )
        val retriever = MemoryRetriever(memoryDao = dao)

        val result = retriever.retrieveRelevantMemories("我喜欢什么")

        assertEquals(listOf(1L), result.map { it.id })
        assertTrue(dao.queries.last().contains("喜欢"))
    }

    @Test
    fun `检索结果最多返回五条并按引用和更新时间排序`() = runBlocking {
        val dao = SearchableFakeMemoryDao(
            memories = mutableListOf(
                memory(id = 1, content = "Android 1", layer = "short_term", referenceCount = 1, updatedAt = 100),
                memory(id = 2, content = "Android 2", layer = "short_term", referenceCount = 3, updatedAt = 200),
                memory(id = 3, content = "Android 3", layer = "short_term", referenceCount = 3, updatedAt = 300),
                memory(id = 4, content = "Android 4", layer = "short_term", referenceCount = 2, updatedAt = 400),
                memory(id = 5, content = "Android 5", layer = "short_term", referenceCount = 4, updatedAt = 500),
                memory(id = 6, content = "Android 6", layer = "short_term", referenceCount = 5, updatedAt = 600)
            )
        )
        val retriever = MemoryRetriever(memoryDao = dao)

        val result = retriever.retrieveRelevantMemories("Android")

        assertEquals(listOf(6L, 5L, 3L, 2L, 4L), result.map { it.id })
        assertEquals(listOf(6L, 5L, 3L, 2L, 4L), dao.incrementedIds)
    }

    private fun memory(
        id: Long,
        content: String,
        layer: String = "short_term",
        referenceCount: Int = 0,
        updatedAt: Long = 1_000L
    ) = Memory(
        id = id,
        content = content,
        category = "fact",
        layer = layer,
        source = "rule_extractor",
        referenceCount = referenceCount,
        sessionId = "session-1",
        createdAt = updatedAt,
        updatedAt = updatedAt,
        expiresAt = null
    )

    private class SearchableFakeMemoryDao(
        private val memories: MutableList<Memory>
    ) : MemoryDao {

        val queries = mutableListOf<String>()
        val incrementedIds = mutableListOf<Long>()

        override suspend fun insert(memory: Memory): Long = error("unused")

        override suspend fun insertAll(memories: List<Memory>): List<Long> = error("unused")

        override suspend fun update(memory: Memory) = Unit

        override suspend fun delete(memory: Memory) = Unit

        override suspend fun getAll(): List<Memory> = memories.toList()

        override fun observeAll(): Flow<List<Memory>> = flowOf(memories.toList())

        /* getByLayer removed

        override suspend fun getActiveMemories(minStrength: Double): List<Memory> =
            memories.filter { it.strength >= 0.4 }.sortedByDescending { it.updatedAt }

        override suspend fun getByCategory(category: String): List<Memory> = memories.filter { it.category == category }

        override suspend fun findExactMatch(category: String, content: String): Memory? = null

        override suspend fun searchByFTS(query: SupportSQLiteQuery): List<Memory> {
            val sqliteQuery = query as SimpleSQLiteQuery
            val sql = sqliteQuery.sql
            queries += sql
            val keywords = sql.substringAfter("MATCH '")
                .substringBefore("'")
                .split(" OR ")
                .map { it.trim().trim('"').lowercase() }
                .filter { it.isNotBlank() }
            return memories.filter { memory ->
                val content = memory.content.lowercase()
                keywords.any { keyword -> content.contains(keyword) }
            }
        }

        /* incrementReference removed */
}
