package com.companion.chat.data.memory

import com.companion.chat.data.local.dao.FtsQueryHelper
import com.companion.chat.data.local.dao.MemoryDao
import com.companion.chat.data.local.entity.Memory
import kotlinx.coroutines.flow.Flow

/**
 * 记忆仓库 — 记忆的持久化入口。
 *
 * 改造后：
 * - 废除规则提取器依赖（extractor），交由 MemoryExtractLoop 统一处理
 * - 废除 retriever，改为 PprRetriever
 * - 新增实体/链接写入和语义去重
 */
class MemoryRepository(
    private val memoryDao: MemoryDao,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {

    // ── 基础 CRUD ──

    suspend fun getAllMemories(): List<Memory> = memoryDao.getAll()
    fun observeAllMemories(): Flow<List<Memory>> = memoryDao.observeAll()
    suspend fun getByCategory(category: String): List<Memory> = memoryDao.getByCategory(category)
    suspend fun findExactMatch(category: String, content: String): Memory? =
        memoryDao.findExactMatch(category, content)

    /**
     * 存储单条记忆（统一入口）。
     */
    suspend fun storeMemory(
        content: String,
        category: String,
        source: String,
        entityName: String? = null,
        sessionId: String? = null,
        roleCardId: Long? = null
    ): Memory {
        val now = nowProvider()
        val memory = Memory(
            content = content.trim(),
            category = category,
            strength = MemoryConfig.INITIAL_STRENGTH,
            source = source,
            entityName = entityName,
            sessionId = sessionId,
            roleCardId = roleCardId,
            createdAt = now,
            updatedAt = now,
            lastAccessedAt = now
        )
        val id = memoryDao.insert(memory)
        return memory.copy(id = id)
    }

    suspend fun updateMemory(memory: Memory) {
        memoryDao.update(memory.copy(updatedAt = nowProvider()))
    }

    suspend fun deleteMemory(memory: Memory) {
        memoryDao.delete(memory)
    }

    // ── 强度管理 ──
    // applyDailyDecay 移至 MemoryDecayManager，避免重复

    suspend fun strengthenMemory(memoryId: Long, delta: Double = 0.15) {
        memoryDao.strengthen(memoryId, delta, nowProvider())
    }

    suspend fun cleanupWeakMemories(threshold: Double = 0.05): Int {
        return memoryDao.deleteByStrengthBelow(threshold)
    }

    // ── FTS 检索 ──

    suspend fun searchByFTS(expression: String, limit: Int = 5): List<Memory> {
        return memoryDao.searchByFTS(FtsQueryHelper.buildFtsQuery(expression, limit))
    }

    suspend fun searchByFTSWithRole(expression: String, roleCardId: Long, limit: Int = 5): List<Memory> {
        return memoryDao.searchByFTSWithRole(FtsQueryHelper.buildFtsQueryWithRole(expression, roleCardId, limit))
    }

    // ── 语义去重 ──

    /**
     * 向量语义去重。后备方案：用简化的规则去重。
     */
    suspend fun deduplicateBySemantics(
        content: String,
        category: String,
        threshold: Float = MemoryConfig.SEMANTIC_DEDUP_THRESHOLD,
        embeddingEngine: (suspend (String) -> FloatArray)? = null
    ): Memory? {
        val existingMemories = memoryDao.getByCategory(category)

        if (embeddingEngine != null) {
            val embedding = embeddingEngine(content) ?: return null
            for (memory in existingMemories) {
                val existingEmbedding = embeddingEngine(memory.content) ?: continue
                val similarity = cosineSimilarity(embedding, existingEmbedding)
                if (similarity >= threshold) return memory
            }
        } else {
            // 后备：简化规则去重（仅去除纯语气词）
            val normalized = normalizeForDedupSimple(content)
            for (memory in existingMemories) {
                if (normalizeForDedupSimple(memory.content) == normalized) return memory
            }
        }
        return null
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        return dot / (kotlin.math.sqrt(na) * kotlin.math.sqrt(nb))
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
        const val MODEL_SOURCE = "model_extractor"
        const val MANUAL_SOURCE = "manual"

        /** 简化去重 — 只去除纯语气词（移除"打""着""过"等过度去除） */
        private val DEDUP_NOISE_SIMPLE = listOf("的", "了", "吧", "呢", "啊", "呀", "嘛", "哦")
        private fun normalizeForDedupSimple(content: String): String {
            var result = content.trim().lowercase()
            for (noise in DEDUP_NOISE_SIMPLE) {
                result = result.replace(noise, "")
            }
            return result
        }
    }
}
