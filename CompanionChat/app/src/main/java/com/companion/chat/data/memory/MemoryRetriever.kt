package com.companion.chat.data.memory

import com.companion.chat.data.local.dao.FtsQueryHelper
import com.companion.chat.data.local.dao.MemoryDao
import com.companion.chat.data.local.entity.Memory

/**
 * 记忆检索器 — 简化版，关键词提取 + FTS 基础检索。
 *
 * 改造后：
 * - PPR 高级检索移至 PprRetriever
 * - 本类保留为轻量工具，供简单场景使用
 * - 引用计数递增逻辑已废除（改用 strength 强化）
 */
class MemoryRetriever(
    private val memoryDao: MemoryDao
) {

    /**
     * 基础 FTS 检索（无角色过滤）。
     */
    suspend fun retrieveRelevantMemories(userMessage: String): List<Memory> {
        val keywords = extractKeywords(userMessage)
        if (keywords.isEmpty()) return emptyList()

        val ftsTerms = keywords.map { kw -> "\"${escapeFtsTerm(kw)}\"" }
        val ftsExpression = ftsTerms.joinToString(" OR ")

        val ftsResults = memoryDao.searchByFTS(FtsQueryHelper.buildFtsQuery(ftsExpression, MAX_RESULTS))

        // 兜底：FTS 无结果时做全量扫描
        val fallbackMatches = if (ftsResults.isEmpty()) {
            memoryDao.getAll().filter { memory ->
                val content = memory.content.lowercase()
                keywords.any { content.contains(it) }
            }
        } else emptyList()

        return (ftsResults + fallbackMatches)
            .distinctBy { it.id }
            .sortedByDescending { it.strength }
            .take(MAX_RESULTS)
    }

    fun extractKeywords(userMessage: String): List<String> {
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

    companion object {
        private const val MAX_RESULTS = 5
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val PUNCTUATION_REGEX = Regex("[^a-z0-9\\u4E00-\\u9FFF]+")
        private val STOP_WORDS = setOf("这个", "那个", "一下", "请问", "帮我", "帮忙", "可以", "吗", "呢", "呀", "啊")
    }
}
