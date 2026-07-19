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
     * 关键词相似搜索：从 content 中提取关键词，在同 category 的已有记忆中搜索。
     * 返回包含任一关键词的已有记忆列表。
     */
    suspend fun findSimilarByKeywords(category: String, content: String, limit: Int = 3): List<Memory> {
        val keywords = extractKeywordsFromContent(content)
        if (keywords.isEmpty()) return emptyList()
        val results = mutableListOf<Memory>()
        val seen = mutableSetOf<Long>()
        for (keyword in keywords) {
            val pattern = "%$keyword%"
            val matches = memoryDao.searchByContentLike(category, pattern, limit)
            for (match in matches) {
                if (match.id !in seen) {
                    seen.add(match.id)
                    results.add(match)
                    if (results.size >= limit) return results
                }
            }
        }
        return results
    }

    /**
     * 从记忆内容中提取关键词（去除常见语气词、短词）。
     */
    private fun extractKeywordsFromContent(content: String): List<String> {
        val noise = setOf("的", "了", "是", "在", "我", "你", "他", "她", "它", "们", "吧", "呢", "啊", "呀", "嘛", "哦", "也", "都", "就", "还", "又", "才", "会", "能", "要", "想", "说", "看", "做", "去", "来", "到", "和", "与", "但", "不", "没", "有", "这", "那", "一", "很", "太", "真", "好", "多", "少", "大", "小")
        return content.split(Regex("[\\s,，。、；;！!？?\\.\\n]+"))
            .map { it.trim() }
            .filter { it.length >= 2 && it !in noise }
            .distinct()
            .take(5)
    }

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
        val now = nowProvider()
        val today = now / (1000 * 60 * 60 * 24) // epoch day
        memoryDao.strengthen(memoryId, delta, today, now)
    }

    suspend fun cleanupWeakMemories(threshold: Double = 0.05): Int {
        return memoryDao.deleteByStrengthBelow(threshold)
    }

    // ── FTS 检索 ──

    suspend fun searchByFTS(expression: String, limit: Int = 5): List<Memory> {
        val results = memoryDao.searchByFTS(FtsQueryHelper.buildFtsQuery(expression, limit))
        // 检索命中 = 用户提到了相关话题，强化这些记忆（"提到了才回升"）
        results.forEach { strengthenMemory(it.id, MemoryConfig.STRENGTHEN_FTS_HIT) }
        return results
    }

    suspend fun searchByFTSWithRole(expression: String, roleCardId: Long, limit: Int = 5): List<Memory> {
        val results = memoryDao.searchByFTSWithRole(FtsQueryHelper.buildFtsQueryWithRole(expression, roleCardId, limit))
        results.forEach { strengthenMemory(it.id, MemoryConfig.STRENGTHEN_FTS_HIT) }
        return results
    }

    /**
     * LIKE 关键词检索（替代 FTS，因为 FTS4 默认 tokenizer 不支持中文分词）。
     * 对每个关键词执行 content LIKE '%keyword%'，合并去重，按 strength 排序。
     */
    suspend fun searchByKeywordsWithRole(keywords: List<String>, roleCardId: Long, limit: Int = 5): List<Memory> {
        if (keywords.isEmpty()) return emptyList()
        val results = mutableListOf<Memory>()
        val seen = mutableSetOf<Long>()
        for (keyword in keywords) {
            if (keyword.length < 2) continue
            val pattern = "%$keyword%"
            val matches = memoryDao.searchByContentLikeWithRole(pattern, roleCardId, limit)
            for (match in matches) {
                if (match.id !in seen) {
                    seen.add(match.id)
                    results.add(match)
                    if (results.size >= limit) {
                        results.forEach { strengthenMemory(it.id, MemoryConfig.STRENGTHEN_FTS_HIT) }
                        return results
                    }
                }
            }
        }
        results.forEach { strengthenMemory(it.id, MemoryConfig.STRENGTHEN_FTS_HIT) }
        return results
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
