package com.companion.chat.data.memory

import com.companion.chat.data.local.dao.MemoryDao
import com.companion.chat.data.local.dao.MemoryEntityDao
import com.companion.chat.data.local.dao.MemoryLinkDao
import com.companion.chat.data.local.dao.FtsQueryHelper
import com.companion.chat.data.local.entity.Memory

/**
 * PPR 轻量图检索器 — 从用户输入提取种子，沿 links 做带权扩散。
 *
 * 参考 OpenViking 的 PPR 搜索增强 + mem0 的实体提升机制。
 *
 * 流程：
 *   Phase 1: 种子获取（FTS 检索 + 实体匹配）
 *   Phase 2: PPR 1-hop 传播（按 linkType 配置权重）
 *   Phase 3: 多种子桥接叠加
 *   Phase 4: 四因子评分排序
 */
class PprRetriever(
    private val memoryDao: MemoryDao,
    private val memoryLinkDao: MemoryLinkDao,
    private val memoryEntityDao: MemoryEntityDao
) {
    /**
     * PPR 检索入口。
     *
     * @param userMessage 用户输入
     * @param roleCardId 角色 ID（可选，用于角色过滤）
     * @param topK 返回前 K 个结果
     * @return 排序后的带评分记忆列表
     */
    suspend fun retrieve(
        userMessage: String,
        roleCardId: Long? = null,
        topK: Int = 5
    ): List<ScoredMemory> {
        // Phase 1: 种子获取
        val keywords = extractKeywords(userMessage)
        if (keywords.isEmpty()) return emptyList()

        val ftsTerms = keywords.map { kw -> "\"${escapeFtsTerm(kw)}\"" }
        val ftsExpression = ftsTerms.joinToString(" OR ")

        val ftsResults = if (roleCardId != null) {
            memoryDao.searchByFTSWithRole(FtsQueryHelper.buildFtsQueryWithRole(ftsExpression, roleCardId, topK * 2))
        } else {
            memoryDao.searchByFTS(FtsQueryHelper.buildFtsQuery(ftsExpression, topK * 2))
        }

        if (ftsResults.isEmpty()) return emptyList()

        // TODO: 增加 memory_entities 表的实体名称模糊匹配，提升种子质量
        // 当前仅使用 FTS 关键词检索作为种子，未做实体感知匹配

        // 为每个种子计算 base_score（基于 FTS 排名，指数衰减）
        val seeds: List<SeedEntry> = ftsResults.mapIndexed { index, memory ->
            val score = Math.pow(0.5, index.toDouble())
            SeedEntry(memory.id, baseScore = score)
        }

        // Phase 2: PPR 1-hop 传播 + Phase 3: 桥接叠加
        val pprScores = mutableMapOf<Long, Double>()

        for (seed in seeds) {
            val neighbors = memoryLinkDao.getNeighbors(seed.memoryId, MIN_LINK_WEIGHT)
            for (neighbor in neighbors) {
                val weight = PPR_CONFIG[neighbor.linkType] ?: 0.4
                val ppr = seed.baseScore * DAMPING_FACTOR * weight

                // 累加到对端记忆
                val targetId = if (neighbor.fromId == seed.memoryId) neighbor.toId else neighbor.fromId
                pprScores[targetId] = (pprScores[targetId] ?: 0.0) + ppr
            }
        }

        // Phase 4: 评分合并
        val seedIds = seeds.map { it.memoryId }.toSet()
        val now = System.currentTimeMillis()

        val scored = ftsResults.associateBy { it.id }.mapNotNull { (id, memory) ->
            val ftsScore = seeds.find { it.memoryId == id }?.baseScore ?: 0.0
            val pprScore = pprScores[id] ?: 0.0
            val idleDays = ((now - memory.lastAccessedAt) / DAY_MILLIS).toFloat()
            val recencyScore = Math.exp(-Math.log(2.0) / 14.0 * idleDays)
            val entityBoost = calculateEntityBoost(id)

            val finalScore = if (id in seedIds) {
                0.35 * ftsScore + 0.25 * pprScore + 0.25 * recencyScore + 0.15 * entityBoost
            } else {
                pprScore
            }

            if (pprScore < 0.05 && id !in seedIds) null
            else ScoredMemory(
                memory = memory,
                score = finalScore.coerceIn(0.0, 1.0),
                ftsScore = ftsScore,
                pprScore = pprScore,
                recencyScore = recencyScore,
                entityBoost = entityBoost
            )
        }.sortedByDescending { it.score }.take(topK)

        // 强度强化：被检索到的记忆增加 strength（每日上限 0.4）
        val today = now / (1000 * 60 * 60 * 24)
        scored.forEach { (scoredMemory) ->
            memoryDao.strengthen(scoredMemory.id, STRENGTHEN_DELTA, today, now)
        }

        return scored
    }

    private suspend fun calculateEntityBoost(memoryId: Long): Double {
        val entities = memoryEntityDao.getEntitiesForMemory(memoryId)
        if (entities.isEmpty()) return 0.0
        val maxLinked = entities.maxOf { it.linkedMemoryCount }
        return 0.5 * (1.0 / (1.0 + 0.001 * (maxLinked - 1).coerceAtLeast(0).toDouble().let { it * it }))
    }

    private fun extractKeywords(userMessage: String): List<String> {
        val normalized = userMessage.lowercase()
            .replace(PUNCTUATION_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ").trim()
        return normalized.split(WHITESPACE_REGEX)
            .filter { it.length >= 2 && it !in STOP_WORDS }
            .distinct()
    }

    private fun escapeFtsTerm(term: String): String {
        return term.replace("\"", "")
            .replace("NOT", "").replace("OR", "")
            .replace("AND", "").replace("NEAR", "")
            .replace("*", "").replace("^", "")
    }

    private data class SeedEntry(
        val memoryId: Long,
        val baseScore: Double
    )

    companion object {
        private const val DAMPING_FACTOR = 0.85
        private const val MIN_LINK_WEIGHT = 0.3
        private const val STRENGTHEN_DELTA = 0.05
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000

        private val PPR_CONFIG = mapOf(
            "related_to" to 0.4,
            "belongs_to" to 0.7,
            "caused_by" to 0.5,
            "derived_from" to 0.2,
            "contradicts" to 0.8,
            "evolved_from" to 0.3
        )

        private val WHITESPACE_REGEX = Regex("\\s+")
        private val PUNCTUATION_REGEX = Regex("[^a-z0-9\\u4E00-\\u9FFF]+")
        private val STOP_WORDS = setOf("这个", "那个", "一下", "请问", "帮我", "帮忙", "可以", "吗", "呢")
    }
}

data class ScoredMemory(
    val memory: Memory,
    val score: Double,
    val ftsScore: Double,
    val pprScore: Double,
    val recencyScore: Double,
    val entityBoost: Double
)
